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
