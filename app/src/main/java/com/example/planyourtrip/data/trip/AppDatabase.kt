package com.example.planyourtrip.data.trip

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.planyourtrip.data.currency.ExchangeRateDao
import com.example.planyourtrip.data.currency.ExchangeRateEntity
import com.example.planyourtrip.data.document.DocumentDao
import com.example.planyourtrip.data.document.DocumentEntity
import com.example.planyourtrip.data.sync.DeletionEntity
import com.example.planyourtrip.data.sync.SyncDao

/**
 * Local database. When changing an entity: bump [version], add a Migration (or an
 * AutoMigration) and commit the new schema JSON generated under app/schemas.
 */
@Database(
    entities = [
        TripEntity::class, ExpenseEntity::class, ExchangeRateEntity::class, ActivityEntity::class,
        DeletionEntity::class, ChecklistItemEntity::class, DocumentEntity::class,
    ],
    version = 12,
    exportSchema = true,
    autoMigrations = [
        // v2: trips.owner_id. Trips from v1 get "" and are not shown to any account.
        AutoMigration(from = 1, to = 2),
        // v3: trips.budget_agorot (null = no budget) and the expenses table. Existing trips are kept.
        AutoMigration(from = 2, to = 3),
        // v4: trips.currency_code (null = not chosen) and the exchange_rates cache. Existing data is kept.
        AutoMigration(from = 3, to = 4),
        // v5: the activities table (daily itinerary). Existing trips, expenses and rates are kept.
        AutoMigration(from = 4, to = 5),
        // v6: expenses.original_amount / original_currency / exchange_rate / rate_date (all null = entered in
        // shekels, which is what every existing expense becomes). Nothing else changes.
        AutoMigration(from = 5, to = 6),
        // v7 (cloud sync) is the manual MIGRATION_6_7: it fills a new uuid for every existing row.
        // v8: the places table and activities.place_id (null = no place, foreign key SET NULL). Room rebuilds
        // `activities` to add the foreign key and copies every row, so all data is kept.
        AutoMigration(from = 7, to = 8),
        // v9: places.auto_matched (0 = chosen by the user, which every existing place is) and
        // activities.place_lookup (null = never looked up automatically). All data is kept.
        AutoMigration(from = 8, to = 9),
        // v10: places.added_on_map (0 = not saved on the Map tab; existing places are only removed when the last
        // activity using them is deleted). All data is kept.
        AutoMigration(from = 9, to = 10),
        // v11 (Map tab removed) is the manual MIGRATION_10_11: it copies each linked place's name into an empty
        // activity location, then drops the places table and the activities' place columns.
        // v12: the checklist_items table (Prep tab, synced) and the documents table (this device only). Both start
        // empty; all existing data is kept.
        AutoMigration(from = 11, to = 12),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun activityDao(): ActivityDao

    abstract fun syncDao(): SyncDao

    abstract fun checklistDao(): ChecklistDao

    abstract fun documentDao(): DocumentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "plan_your_trip.db")
                .addMigrations(MIGRATION_6_7, MIGRATION_10_11)
                .build()
    }
}
