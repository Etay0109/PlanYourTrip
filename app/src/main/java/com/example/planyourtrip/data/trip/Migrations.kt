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
