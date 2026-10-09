package com.example.planyourtrip.data.trip

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v7: cloud sync. Every trip, expense and activity gets a stable `uuid` (the same in Room and Firestore),
 * `updated_at` (the migration time) and `pending_sync` = 1, so all existing data is uploaded on the first sync
 * after the update. Adds the `deletions` table (deletes waiting to upload). Nothing is lost.
 * Manual because SQLite can't generate a UUID per row; the SQL matches app/schemas/…/7.json.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (table in listOf("trips", "expenses", "activities")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `pending_sync` INTEGER NOT NULL DEFAULT 1")
            val ids = mutableListOf<Long>()
            db.query("SELECT id FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
            for (id in ids) {
                db.execSQL(
                    "UPDATE `$table` SET uuid = ?, updated_at = ?, pending_sync = 1 WHERE id = ?",
                    arrayOf<Any>(UUID.randomUUID().toString(), now, id),
                )
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deletions` (`uuid` TEXT NOT NULL, `owner_id` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `trip_uuid` TEXT, `deleted_at` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletions_owner_id` ON `deletions` (`owner_id`)")
    }
}

/**
 * v11: the Map tab and saved places are gone. First each activity linked to a place whose location text is empty
 * gets the place's name as its location (marked pending with the migration time, so the text reaches the cloud and
 * the account's other devices); activities that already have a location are not touched. Then `activities` is
 * rebuilt without `place_id` / `place_lookup` (SQLite can't drop a foreign key column), `places` is dropped and
 * place deletes still waiting to upload are dropped too (the sync removes every place document from the cloud).
 * Every trip, expense and activity is kept. The SQL matches app/schemas/…/11.json.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE activities SET " +
                "location = (SELECT p.name FROM places p WHERE p.id = activities.place_id), " +
                "updated_at = ?, pending_sync = 1 " +
                "WHERE (location IS NULL OR TRIM(location) = '') " +
                "AND EXISTS (SELECT 1 FROM places p WHERE p.id = activities.place_id AND TRIM(p.name) != '')",
            arrayOf<Any>(System.currentTimeMillis()),
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trip_id` INTEGER NOT NULL, `date` INTEGER NOT NULL, `start_time` INTEGER, `title` TEXT NOT NULL, " +
                "`note` TEXT, `type` TEXT NOT NULL, `location` TEXT, `estimated_cost_agorot` INTEGER, " +
                "`booked` INTEGER NOT NULL, `uuid` TEXT NOT NULL DEFAULT '', `updated_at` INTEGER NOT NULL DEFAULT 0, " +
                "`pending_sync` INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        val columns = "`id`, `trip_id`, `date`, `start_time`, `title`, `note`, `type`, `location`, " +
            "`estimated_cost_agorot`, `booked`, `uuid`, `updated_at`, `pending_sync`"
        db.execSQL("INSERT INTO `_new_activities` ($columns) SELECT $columns FROM `activities`")
        db.execSQL("DROP TABLE `activities`")
        db.execSQL("ALTER TABLE `_new_activities` RENAME TO `activities`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_trip_id` ON `activities` (`trip_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activities_uuid` ON `activities` (`uuid`)")
        db.execSQL("DROP TABLE IF EXISTS `places`")
        db.execSQL("DELETE FROM `deletions` WHERE `kind` = 'PLACE'")
    }
}

/**
 * v14: shared trips. Nothing is lost and nothing is re-uploaded (`updated_at` / `pending_sync` of existing rows are
 * kept: the cloud migration, LegacyMigration.kt, makes the same changes to the cloud copy).
 * - The owner's traveler key "me" ([LEGACY_ME_KEY]) becomes the trip's owner uid (`trips.owner_id`) in expenses
 *   (payer and split) and settlements. The owner stays first in the list order, so every share, balance and transfer
 *   keeps its exact value. Trips without an owner (from v1, shown to no account) are left as they are.
 * - `trips.cloud_owner_id` / `member_ids` (the cloud owner and members: the owner alone) and the personal budget's
 *   own sync state `budget_updated_at` (= the trip's `updated_at`) / `budget_pending` (= the trip's `pending_sync`).
 * - Local copies are per account: unique `trips(owner_id, uuid)` and children `(trip_id, uuid)` instead of `uuid`;
 *   `deletions` is rebuilt with the primary key `(owner_id, uuid)`.
 * The SQL matches app/schemas/…/14.json.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `cloud_owner_id` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `member_ids` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `budget_updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `budget_pending` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE trips SET cloud_owner_id = owner_id, member_ids = owner_id, " +
                "budget_updated_at = updated_at, budget_pending = pending_sync"
        )

        val owner = "(SELECT t.owner_id FROM trips t WHERE t.id = %s.trip_id)"
        val ownerOf = { table: String -> owner.format(table) }
        val owned = { table: String -> "${ownerOf(table)} != ''" }
        db.execSQL(
            "UPDATE expenses SET paid_by = ${ownerOf("expenses")} " +
                "WHERE paid_by = '$LEGACY_ME_KEY' AND ${owned("expenses")}"
        )
        // The split is a comma list without duplicates: replace the one "me" token, wherever it is.
        db.execSQL(
            "UPDATE expenses SET split_between = " +
                "TRIM(REPLACE(',' || split_between || ',', ',$LEGACY_ME_KEY,', " +
                "',' || ${ownerOf("expenses")} || ','), ',') " +
                "WHERE (',' || split_between || ',') LIKE '%,$LEGACY_ME_KEY,%' AND ${owned("expenses")}"
        )
        db.execSQL(
            "UPDATE settlements SET from_traveler = ${ownerOf("settlements")} " +
                "WHERE from_traveler = '$LEGACY_ME_KEY' AND ${owned("settlements")}"
        )
        db.execSQL(
            "UPDATE settlements SET to_traveler = ${ownerOf("settlements")} " +
                "WHERE to_traveler = '$LEGACY_ME_KEY' AND ${owned("settlements")}"
        )

        db.execSQL("DROP INDEX IF EXISTS `index_trips_uuid`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_owner_id_uuid` ON `trips` (`owner_id`, `uuid`)")
        for (table in listOf("expenses", "activities", "checklist_items", "travelers", "settlements")) {
            db.execSQL("DROP INDEX IF EXISTS `index_${table}_uuid`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_trip_id_uuid` ON `$table` (`trip_id`, `uuid`)"
            )
        }

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_deletions` (`uuid` TEXT NOT NULL, `owner_id` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `trip_uuid` TEXT, `deleted_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`owner_id`, `uuid`))"
        )
        db.execSQL(
            "INSERT INTO `_new_deletions` (`uuid`, `owner_id`, `kind`, `trip_uuid`, `deleted_at`) " +
                "SELECT `uuid`, `owner_id`, `kind`, `trip_uuid`, `deleted_at` FROM `deletions`"
        )
        db.execSQL("DROP TABLE `deletions`")
        db.execSQL("ALTER TABLE `_new_deletions` RENAME TO `deletions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletions_owner_id` ON `deletions` (`owner_id`)")
    }
}
