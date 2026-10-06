package com.example.planyourtrip.data.trip

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.planyourtrip.data.currency.ExchangeRateDao
import com.example.planyourtrip.data.currency.ExchangeRateEntity
import com.example.planyourtrip.data.sync.DeletionEntity
import com.example.planyourtrip.data.sync.SyncDao

/**
 * Local database. When changing an entity: bump [version], add a Migration (or an
 * AutoMigration) and commit the new schema JSON generated under app/schemas.
 */
@Database(
    entities = [
        TripEntity::class, ExpenseEntity::class, ExchangeRateEntity::class, ActivityEntity::class,
        DeletionEntity::class,
    ],
    version = 7,
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
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun activityDao(): ActivityDao

    abstract fun syncDao(): SyncDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "plan_your_trip.db")
                .addMigrations(MIGRATION_6_7)
                .build()
    }
}
