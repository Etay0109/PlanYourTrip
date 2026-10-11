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
 * v15 → v16 (destination suggestions): trips get country_code / latitude / longitude, null for every existing trip.
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

    @Test
    fun migrate13To14_makesMeTheOwnersUid_keepsEveryNumber_andAddsMembersAndTheBudgetsSyncState() {
        helper.createDatabase(DB, 13).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync) VALUES " +
                    "(1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0), " +
                    "(2, 'u1', 'Oslo', 20800, 20802, 'MINT', NULL, NULL, 't-2', 6, 1), " +
                    // From v1: no owner, shown to no account; left as it is.
                    "(3, '', 'Paris', 20800, 20802, 'ROSE', NULL, NULL, 't-3', 7, 1)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, paid_by, split_between, " +
                    "uuid, updated_at, pending_sync) VALUES " +
                    "(1, 1, 'Flights', 180000, 'FLIGHTS', 20700, 'me', 'me', 'e-1', 5, 0), " +
                    "(2, 1, 'Dinner', 10000, 'FOOD', 20770, 'r-1', 'me,r-1', 'e-2', 6, 1), " +
                    "(3, 1, 'Taxi', 10001, 'TRANSPORT', 20770, 'me', 'r-1,me,r-2', 'e-3', 6, 0), " +
                    "(4, 1, 'Gift', 5000, 'SHOPPING', 20770, 'me', 'r-1', 'e-4', 6, 0), " +
                    "(5, 3, 'Croissant', 500, 'FOOD', 20800, 'me', 'me', 'e-5', 7, 1)"
            )
            db.execSQL(
                "INSERT INTO travelers (id, trip_id, name, color, created_at, uuid, updated_at, pending_sync) " +
                    "VALUES (1, 1, 'Noa', 'ROSE', 9, 'r-1', 9, 0), (2, 1, 'Amit', 'INDIGO', 10, 'r-2', 10, 0)"
            )
            db.execSQL(
                "INSERT INTO settlements (id, trip_id, from_traveler, to_traveler, amount_agorot, date, created_at, " +
                    "uuid, updated_at, pending_sync) VALUES " +
                    "(1, 1, 'me', 'r-1', 3200, 20771, 9, 's-1', 9, 0), (2, 1, 'r-2', 'me', 100, 20772, 10, 's-2', 10, 1)"
            )
            db.execSQL(
                "INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) " +
                    "VALUES ('e-9', 'u1', 'EXPENSE', 't-1', 8)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 14, true, MIGRATION_13_14)

        db.query(
            "SELECT uuid, cloud_owner_id, member_ids, budget_agorot, budget_updated_at, budget_pending, updated_at, " +
                "pending_sync FROM trips ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("t-1", c.getString(0))
            assertEquals("u1", c.getString(1)) // the owner…
            assertEquals("u1", c.getString(2)) // …is the only member
            assertEquals(500000, c.getInt(3))
            assertEquals(5, c.getLong(4)) // the budget's own sync state starts as the trip's
            assertEquals(0, c.getInt(5))
            assertEquals(5, c.getLong(6)) // the trip itself is untouched
            assertEquals(0, c.getInt(7))
            assertTrue(c.moveToNext())
            assertEquals(6, c.getLong(4))
            assertEquals(1, c.getInt(5)) // never uploaded yet: the budget is pending too
            assertTrue(c.moveToNext())
            assertEquals("", c.getString(1))
        }
        db.query("SELECT uuid, paid_by, split_between, updated_at, pending_sync FROM expenses ORDER BY id").use { c ->
            val rows = mutableListOf<List<Any>>()
            while (c.moveToNext()) rows += listOf(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4))
            assertEquals(
                listOf(
                    listOf("e-1", "u1", "u1", 5L, 0),
                    listOf("e-2", "r-1", "u1,r-1", 6L, 1),
                    listOf("e-3", "u1", "r-1,u1,r-2", 6L, 0),
                    listOf("e-4", "u1", "r-1", 6L, 0),
                    listOf("e-5", "me", "me", 7L, 1), // the trip without an owner is left as it is
                ),
                rows,
            )
        }
        db.query("SELECT from_traveler, to_traveler, amount_agorot, updated_at, pending_sync FROM settlements ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf("u1", "r-1"), listOf(c.getString(0), c.getString(1)))
            assertEquals(listOf(3200, 9, 0), listOf(c.getInt(2), c.getInt(3), c.getInt(4)))
            assertTrue(c.moveToNext())
            assertEquals(listOf("r-2", "u1"), listOf(c.getString(0), c.getString(1)))
        }
        db.query("SELECT uuid, owner_id, kind FROM deletions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf("e-9", "u1", "EXPENSE"), listOf(c.getString(0), c.getString(1), c.getString(2)))
        }

        // Local copies are per account: another account may hold the same trip, the same account only once.
        db.execSQL(
            "INSERT INTO trips (owner_id, destination, start_date, end_date, color, uuid, cloud_owner_id, member_ids) " +
                "VALUES ('u2', 'Rome', 20769, 20774, 'SKY', 't-1', 'u1', 'u1,u2')"
        )
        val duplicate = runCatching {
            db.execSQL(
                "INSERT INTO trips (owner_id, destination, start_date, end_date, color, uuid) " +
                    "VALUES ('u1', 'Rome', 20769, 20774, 'SKY', 't-1')"
            )
        }
        assertTrue(duplicate.isFailure)
        db.execSQL("INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) VALUES ('e-9', 'u2', 'EXPENSE', 't-1', 8)")

        // Everything still goes with its trip.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM trips WHERE id = 1")
        for (table in listOf("travelers", "settlements")) {
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
        db.query("SELECT COUNT(*) FROM expenses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0)) // only the ownerless trip's expense
        }
        db.close()
    }

    @Test
    fun migrate14To15_keepsEverything_andAddsAnEmptyMembersTableThatGoesWithItsTrip() {
        helper.createDatabase(DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync, cloud_owner_id, member_ids, budget_updated_at, " +
                    "budget_pending) VALUES " +
                    "(1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0, 'u1', 'u1', 5, 0), " +
                    "(2, 'u2', 'Rome', 20769, 20774, 'SKY', NULL, 'EUR', 't-1', 5, 0, 'u1', 'u1,u2', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, paid_by, split_between, " +
                    "uuid, updated_at, pending_sync) VALUES (1, 1, 'Dinner', 10000, 'FOOD', 20770, 'u1', 'u1,r-1', 'e-1', 6, 1)"
            )
            db.execSQL(
                "INSERT INTO deletions (uuid, owner_id, kind, trip_uuid, deleted_at) VALUES ('a-9', 'u1', 'ACTIVITY', 't-1', 8)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 15, true)

        db.query("SELECT id, owner_id, member_ids, budget_agorot, updated_at, pending_sync FROM trips ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf(1, 500000, 5, 0), listOf(c.getInt(0), c.getInt(3), c.getInt(4), c.getInt(5)))
            assertTrue(c.moveToNext())
            assertEquals("u1,u2", c.getString(2))
        }
        db.query("SELECT paid_by, split_between, updated_at, pending_sync FROM expenses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf("u1", "u1,r-1"), listOf(c.getString(0), c.getString(1)))
            assertEquals(listOf(6, 1), listOf(c.getInt(2), c.getInt(3)))
        }
        db.query("SELECT COUNT(*) FROM deletions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM trip_members").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // One member document per uid and trip; the same uid may be in two accounts' copies.
        db.execSQL("INSERT INTO trip_members (trip_id, uid, name, joined_at, `left`) VALUES (1, 'u2', 'Noa', 9, 0)")
        db.execSQL("INSERT INTO trip_members (trip_id, uid, name, joined_at, `left`) VALUES (2, 'u2', 'Noa', 9, 0)")
        assertTrue(
            runCatching {
                db.execSQL("INSERT INTO trip_members (trip_id, uid, name, joined_at, `left`) VALUES (1, 'u2', 'Noa', 9, 0)")
            }.isFailure
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM trips WHERE id = 1")
        db.query("SELECT trip_id FROM trip_members").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertTrue(!c.moveToNext())
        }
        db.close()
    }

    @Test
    fun migrate15To16_keepsEverything_andExistingTripsHaveNoPlace() {
        helper.createDatabase(DB, 15).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync, cloud_owner_id, member_ids, budget_updated_at, " +
                    "budget_pending) VALUES " +
                    "(1, 'u1', 'Rome', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0, 'u1', 'u1,u2', 5, 0), " +
                    "(2, 'u1', 'Lisbon', 20928, 20934, 'PEACH', NULL, NULL, 't-2', 6, 1, 'u1', 'u1', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, paid_by, split_between, " +
                    "uuid, updated_at, pending_sync) VALUES (1, 1, 'Dinner', 10000, 'FOOD', 20770, 'u1', 'u1,u2', 'e-1', 6, 0)"
            )
            db.execSQL("INSERT INTO trip_members (trip_id, uid, name, joined_at, `left`) VALUES (1, 'u2', 'Noa', 9, 0)")
        }

        val db = helper.runMigrationsAndValidate(DB, 16, true)

        db.query(
            "SELECT destination, budget_agorot, currency_code, member_ids, updated_at, pending_sync, " +
                "country_code, latitude, longitude FROM trips ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf("Rome", "EUR", "u1,u2"), listOf(c.getString(0), c.getString(2), c.getString(3)))
            assertEquals(listOf(500000, 5, 0), listOf(c.getInt(1), c.getInt(4), c.getInt(5)))
            assertTrue(c.isNull(6) && c.isNull(7) && c.isNull(8))
            assertTrue(c.moveToNext())
            assertEquals("Lisbon", c.getString(0))
            assertEquals(listOf(6, 1), listOf(c.getInt(4), c.getInt(5)))
            assertTrue(c.isNull(6) && c.isNull(7) && c.isNull(8))
            assertFalse(c.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM expenses").use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM trip_members").use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }

        // A new trip can hold a place.
        db.execSQL(
            "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, uuid, country_code, latitude, " +
                "longitude) VALUES (3, 'u1', 'London, United Kingdom', 20769, 20774, 'MINT', 't-3', 'GB', 51.5, -0.13)"
        )
        db.query("SELECT country_code, latitude, longitude FROM trips WHERE id = 3").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("GB", c.getString(0))
            assertEquals(51.5, c.getDouble(1), 0.0)
            assertEquals(-0.13, c.getDouble(2), 0.0)
        }
        db.close()
    }

    @Test
    fun migrate16To17_keepsEverything_andThePhotoTableStartsEmpty() {
        helper.createDatabase(DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO trips (id, owner_id, destination, start_date, end_date, color, budget_agorot, " +
                    "currency_code, uuid, updated_at, pending_sync, cloud_owner_id, member_ids, budget_updated_at, " +
                    "budget_pending, country_code, latitude, longitude) VALUES " +
                    "(1, 'u1', 'Rome, Italy', 20769, 20774, 'SKY', 500000, 'EUR', 't-1', 5, 0, 'u1', 'u1,u2', 5, 0, " +
                    "'IT', 41.89, 12.48), " +
                    "(2, 'u1', 'Lisbon', 20928, 20934, 'PEACH', NULL, NULL, 't-2', 6, 1, 'u1', 'u1', 0, 0, NULL, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO expenses (id, trip_id, name, amount_agorot, category, date, paid_by, split_between, " +
                    "uuid, updated_at, pending_sync) VALUES (1, 1, 'Dinner', 10000, 'FOOD', 20770, 'u1', 'u1,u2', 'e-1', 6, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 17, true)

        db.query(
            "SELECT destination, budget_agorot, updated_at, pending_sync, country_code, latitude FROM trips ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(listOf("Rome, Italy", "IT"), listOf(c.getString(0), c.getString(4)))
            assertEquals(listOf(500000, 5, 0), listOf(c.getInt(1), c.getInt(2), c.getInt(3)))
            assertEquals(41.89, c.getDouble(5), 0.0)
            assertTrue(c.moveToNext())
            assertEquals("Lisbon", c.getString(0))
            assertEquals(listOf(6, 1), listOf(c.getInt(2), c.getInt(3)))
            assertFalse(c.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM expenses").use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM destination_photos").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // A photo row with and without URLs ("nothing found").
        db.execSQL(
            "INSERT INTO destination_photos (photo_key, card_url, thumb_url, file_page_url, checked_at) VALUES " +
                "('rome, italy|41.890,12.480', 'https://a/960.jpg', 'https://a/250.jpg', 'https://c/File:a.jpg', 7), " +
                "('lisbon|', NULL, NULL, NULL, 8)"
        )
        db.query("SELECT card_url, checked_at FROM destination_photos ORDER BY checked_at").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://a/960.jpg", c.getString(0))
            assertTrue(c.moveToNext())
            assertTrue(c.isNull(0))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
