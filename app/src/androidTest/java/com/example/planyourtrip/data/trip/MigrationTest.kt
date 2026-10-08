package com.example.planyourtrip.data.trip

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v6 → v7 (cloud sync): existing rows are kept, each gets its own uuid and is pending upload.
 * v7 → v8 (places): activities are kept without a place, and the places table exists.
 * v10 → v11 (Map tab removed): a linked place's name fills an empty location, then places and the place columns go.
 * v11 → v12 (Prep tab): checklist_items and documents are added empty; everything else is kept as it was.
 */
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

    @Test
    fun migrate7To8_keepsActivities_withoutAPlace_andAddsPlaces() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO activities (id, trip_id, date, start_time, title, type, booked, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 1, 20770, 600, 'Colosseum', 'ACTIVITY', 1, 'a-1', 5, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 8, true)

        db.query("SELECT title, start_time, booked, uuid, updated_at, pending_sync, place_id FROM activities").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Colosseum", c.getString(0))
            assertEquals(600, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals("a-1", c.getString(3))
            assertEquals(5L, c.getLong(4))
            assertEquals(0, c.getInt(5)) // nothing to upload: the activity didn't change
            assertTrue(c.isNull(6))
        }
        db.query("SELECT COUNT(*) FROM places").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate8To9_keepsPlacesAndActivities_notAutoMatched_andNeverLookedUp() {
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO places (id, trip_id, name, latitude, longitude, uuid, updated_at, pending_sync) " +
                    "VALUES (1, 1, 'Colosseum', 41.8902, 12.4922, 'p-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO activities (id, trip_id, date, start_time, title, type, booked, place_id, uuid, " +
                    "updated_at, pending_sync) VALUES (1, 1, 20770, 600, 'Colosseum', 'ACTIVITY', 0, 1, 'a-1', 5, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 9, true)

        db.query("SELECT name, auto_matched, pending_sync FROM places").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Colosseum", c.getString(0))
            assertEquals(0, c.getInt(1)) // chosen by the user
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT title, place_id, place_lookup, pending_sync FROM activities").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Colosseum", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }
        db.close()
    }

    @Test
    fun migrate9To10_keepsPlaces_notMarkedAsSavedOnTheMap() {
        helper.createDatabase(DB, 9).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO places (id, trip_id, name, latitude, longitude, auto_matched, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 1, 'Colosseum', 41.8902, 12.4922, 1, 'p-1', 5, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 10, true)

        db.query("SELECT name, auto_matched, added_on_map, pending_sync FROM places").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Colosseum", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3)) // nothing to upload
        }
        db.close()
    }

    @Test
    fun migrate10To11_copiesPlaceNamesIntoEmptyLocations_andDropsPlaces_keepingAllData() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, uuid, " +
                    "updated_at, pending_sync) VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 1, 'Flights', 180000, 'FLIGHTS', 20700, 'e-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO places (id, trip_id, name, address, latitude, longitude, auto_matched, added_on_map, " +
                    "uuid, updated_at, pending_sync) VALUES (1, 1, 'Colosseum', 'Piazza del Colosseo 1', 41.8902, " +
                    "12.4922, 1, 0, 'p-1', 5, 0)"
            )
            val activity = "INSERT INTO activities (id, trip_id, date, start_time, title, note, type, location, " +
                "estimated_cost_agorot, booked, place_id, place_lookup, uuid, updated_at, pending_sync) VALUES "
            // (a) linked, no location text → gets the place name.
            db.execSQL(activity + "(1, 1, 20769, 600, 'Arena tour', 'Tickets', 'ACTIVITY', NULL, 9000, 1, 1, " +
                "'colosseum, rome', 'a-1', 5, 0)")
            // (b) linked, blank location text → gets the place name too.
            db.execSQL(activity + "(2, 1, 20770, NULL, 'Again', NULL, 'ACTIVITY', '  ', NULL, 0, 1, NULL, 'a-2', 5, 0)")
            // (c) linked with its own location text → unchanged, not pending.
            db.execSQL(activity + "(3, 1, 20771, NULL, 'Night visit', NULL, 'ACTIVITY', 'Via Sacra', NULL, 0, 1, " +
                "NULL, 'a-3', 5, 0)")
            // (d) no place → unchanged.
            db.execSQL(activity + "(4, 1, 20772, NULL, 'Train', NULL, 'TRANSPORT', NULL, NULL, 0, NULL, NULL, " +
                "'a-4', 5, 0)")
            db.execSQL(
                "INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) VALUES " +
                    "('p-9', 'u1', 'PLACE', 't-1', 6), ('a-9', 'u1', 'ACTIVITY', 't-1', 6)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 11, true, MIGRATION_10_11)

        db.query(
            "SELECT id, title, note, type, location, start_time, estimated_cost_agorot, booked, uuid, updated_at, " +
                "pending_sync FROM activities ORDER BY id"
        ).use { c ->
            assertEquals(4, c.count)
            c.moveToNext()
            assertEquals("Arena tour", c.getString(1))
            assertEquals("Tickets", c.getString(2))
            assertEquals("Colosseum", c.getString(4))
            assertEquals(600, c.getInt(5))
            assertEquals(9000, c.getInt(6))
            assertEquals(1, c.getInt(7))
            assertEquals("a-1", c.getString(8))
            assertTrue(c.getLong(9) > 5) // the copied location is a new change…
            assertEquals(1, c.getInt(10)) // …that uploads
            c.moveToNext()
            assertEquals("Colosseum", c.getString(4))
            assertEquals(1, c.getInt(10))
            c.moveToNext()
            assertEquals("Via Sacra", c.getString(4))
            assertEquals(5, c.getLong(9))
            assertEquals(0, c.getInt(10))
            c.moveToNext()
            assertEquals("Train", c.getString(1))
            assertNull(c.getString(4))
            assertEquals(0, c.getInt(10))
        }
        db.query("SELECT destination, budget_agorot FROM trips").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Rome", c.getString(0))
            assertEquals(500000, c.getInt(1))
        }
        db.query("SELECT name, amount_agorot FROM expenses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Flights", c.getString(0))
        }
        db.query("SELECT uuid FROM deletions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a-9", c.getString(0))
            assertFalse(c.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'places'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("PRAGMA table_info(activities)").use { c ->
            val columns = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertFalse("place_id" in columns)
            assertFalse("place_lookup" in columns)
        }
        // The trip cascade still works on the rebuilt table.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM trips WHERE id = 1")
        db.query("SELECT COUNT(*) FROM activities").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate11To12_keepsAllData_andAddsEmptyChecklistAndDocumentTables() {
        helper.createDatabase(DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync) " +
                    "VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, uuid, updated_at, " +
                    "pending_sync) VALUES (1, 1, 'Flights', 180000, 'FLIGHTS', 20700, 'e-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO activities (id, trip_id, date, start_time, title, note, type, location, " +
                    "estimated_cost_agorot, booked, uuid, updated_at, pending_sync) VALUES " +
                    "(1, 1, 20769, 600, 'Colosseum', 'Tickets', 'ACTIVITY', 'Piazza del Colosseo', 9000, 1, 'a-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) " +
                    "VALUES ('a-9', 'u1', 'ACTIVITY', 't-1', 6)"
            )
            db.execSQL(
                "INSERT INTO exchange_rates (currency_code, rate, rate_date, fetched_at) " +
                    "VALUES ('EUR', '3.4408', 20730, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 12, true)

        db.query("SELECT destination, budget_agorot, currency_code, uuid, updated_at, pending_sync FROM trips").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Rome", c.getString(0))
            assertEquals(500000, c.getInt(1))
            assertEquals("EUR", c.getString(2))
            assertEquals("t-1", c.getString(3))
            assertEquals(5, c.getLong(4))
            assertEquals(0, c.getInt(5)) // nothing new to upload
        }
        db.query("SELECT name, amount_agorot, pending_sync FROM expenses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Flights", c.getString(0))
            assertEquals(180000, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT title, location, start_time, booked, pending_sync FROM activities").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Colosseum", c.getString(0))
            assertEquals("Piazza del Colosseo", c.getString(1))
            assertEquals(600, c.getInt(2))
            assertEquals(1, c.getInt(3))
            assertEquals(0, c.getInt(4))
        }
        db.query("SELECT uuid FROM deletions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a-9", c.getString(0))
        }
        db.query("SELECT rate FROM exchange_rates").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("3.4408", c.getString(0))
        }
        for (table in listOf("checklist_items", "documents")) {
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }

        // The new tables work: a checklist item goes with its trip, a document (no foreign key) stays.
        db.execSQL(
            "INSERT INTO checklist_items (id, trip_id, list, text, checked, due_date, created_at, uuid, updated_at, " +
                "pending_sync) VALUES (1, 1, 'BEFORE_TRIP', 'Passport', 0, 20760, 7, 'c-1', 7, 1)"
        )
        db.execSQL(
            "INSERT INTO documents (id, owner_id, trip_uuid, uuid, name, type, detail, file_name, mime_type, " +
                "size_bytes, created_at) VALUES (1, 'u1', 't-1', 'd-1', 'Tickets', 'FLIGHT', NULL, 'd-1.pdf', " +
                "'application/pdf', 1000, 7)"
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM trips WHERE id = 1")
        db.query("SELECT COUNT(*) FROM checklist_items").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM documents").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate12To13_keepsAllData_existingExpensesArePaidByMeNotShared_andAddsEmptySplitTables() {
        helper.createDatabase(DB, 12).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync) " +
                    "VALUES (1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, original_amount, " +
                    "original_currency, exchange_rate, rate_date, uuid, updated_at, pending_sync) VALUES " +
                    "(1, 1, 'Flights', 180000, 'FLIGHTS', 20700, NULL, NULL, NULL, NULL, 'e-1', 5, 0), " +
                    "(2, 1, 'Gelato', 4020, 'FOOD', 20770, '10.00', 'EUR', '4.02', 20770, 'e-2', 6, 1)"
            )
            db.execSQL(
                "INSERT INTO activities (id, trip_id, date, start_time, title, note, type, location, " +
                    "estimated_cost_agorot, booked, uuid, updated_at, pending_sync) VALUES " +
                    "(1, 1, 20769, 600, 'Colosseum', NULL, 'ACTIVITY', NULL, NULL, 0, 'a-1', 5, 0)"
            )
            db.execSQL(
                "INSERT INTO checklist_items (id, trip_id, list, text, checked, due_date, created_at, uuid, " +
                    "updated_at, pending_sync) VALUES (1, 1, 'PACKING', 'Charger', 1, NULL, 7, 'c-1', 7, 0)"
            )
            db.execSQL(
                "INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) " +
                    "VALUES ('e-9', 'u1', 'EXPENSE', 't-1', 8)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 13, true)

        db.query("SELECT destination, budget_agorot, pending_sync FROM trips").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Rome", c.getString(0))
            assertEquals(500000, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
        db.query(
            "SELECT name, amount_agorot, original_amount, paid_by, split_between, updated_at, pending_sync " +
                "FROM expenses ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Flights", c.getString(0))
            assertEquals(180000, c.getInt(1))
            assertEquals("me", c.getString(3))
            assertEquals("me", c.getString(4))
            assertEquals(5, c.getLong(5)) // untouched: nothing new to upload
            assertEquals(0, c.getInt(6))
            assertTrue(c.moveToNext())
            assertEquals("Gelato", c.getString(0))
            assertEquals(4020, c.getInt(1))
            assertEquals("10.00", c.getString(2))
            assertEquals("me", c.getString(3))
            assertEquals("me", c.getString(4))
            assertEquals(1, c.getInt(6)) // still pending, as before
        }
        db.query("SELECT COUNT(*) FROM activities").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT text FROM checklist_items").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Charger", c.getString(0))
        }
        db.query("SELECT uuid FROM deletions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("e-9", c.getString(0))
        }
        for (table in listOf("travelers", "settlements")) {
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }

        // The new tables work and go with their trip.
        db.execSQL(
            "INSERT INTO travelers (id, trip_id, name, color, created_at, uuid, updated_at, pending_sync) " +
                "VALUES (1, 1, 'Noa', 'ROSE', 9, 'r-1', 9, 1)"
        )
        db.execSQL(
            "INSERT INTO settlements (id, trip_id, from_traveler, to_traveler, amount_agorot, date, created_at, " +
                "uuid, updated_at, pending_sync) VALUES (1, 1, 'me', 'r-1', 3200, 20771, 9, 's-1', 9, 1)"
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM trips WHERE id = 1")
        for (table in listOf("travelers", "settlements", "expenses")) {
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
