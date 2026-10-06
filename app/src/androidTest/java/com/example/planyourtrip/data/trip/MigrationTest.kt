package com.example.planyourtrip.data.trip

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v6 → v7 (cloud sync): existing rows are kept, each gets its own uuid and is pending upload. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate6To7_keepsData_andGivesEveryRowAUuid() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color) " +
                    "VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY'), (2, 'u1', 'Lisbon', 20928, 20934, 'PEACH')"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date) " +
                    "VALUES (1, 1, 'Dinner', 12050, 'FOOD', 20770)"
            )
            db.execSQL(
                "INSERT INTO activities (id, trip_id, date, title, type, booked) " +
                    "VALUES (1, 1, 20770, 'Colosseum', 'ACTIVITY', 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_6_7)

        val uuids = mutableListOf<String>()
        for (table in listOf("trips", "expenses", "activities")) {
            db.query("SELECT uuid, updated_at, pending_sync FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    uuids += cursor.getString(0)
                    assertTrue(cursor.getLong(1) > 0)
                    assertEquals(1, cursor.getInt(2))
                }
            }
        }
        assertEquals(4, uuids.size)
        assertTrue(uuids.none { it.isBlank() })
        assertEquals(uuids.size, uuids.toSet().size)
        db.query("SELECT destination FROM trips ORDER BY id").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Rome", cursor.getString(0))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
