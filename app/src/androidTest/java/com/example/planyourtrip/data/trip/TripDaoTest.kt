package com.example.planyourtrip.data.trip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.currency.ExchangeRateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TripDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomTripRepository
    private lateinit var expenses: RoomExpenseRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomTripRepository(db.tripDao())
        expenses = RoomExpenseRepository(db.expenseDao(), db.tripDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addTrip_storesFields_assignsColorsRoundRobin_andOrdersByStart() = runTest {
        val lisbonId = repository.addTrip("u1", " Lisbon ", LocalDate.of(2027, 4, 20), LocalDate.of(2027, 4, 26))
        repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))

        val trips = repository.observeTrips("u1").first()
        assertEquals(listOf("Rome", "Lisbon"), trips.map { it.destination })
        assertEquals(TripColor.forIndex(0), trips.first { it.id == lisbonId }.color)
        assertEquals(TripColor.forIndex(1), trips.first { it.destination == "Rome" }.color)
        assertEquals(LocalDate.of(2027, 4, 26), repository.observeTrip(lisbonId, "u1").first()?.endDate)
    }

    @Test
    fun trips_areScopedToTheirOwner() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        repository.addTrip("u2", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))

        assertEquals(listOf("Rome"), repository.observeTrips("u1").first().map { it.destination })
        assertEquals(listOf("Oslo"), repository.observeTrips("u2").first().map { it.destination })
        assertNull(repository.observeTrip(id, "u2").first())
        // Each owner's palette starts at the first color.
        assertEquals(TripColor.forIndex(0), repository.observeTrips("u2").first().single().color)
    }

    @Test
    fun updateTrip_andSetBudget_onlyForTheOwner() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        assertNull(repository.observeTrip(id, "u1").first()?.budgetAgorot) // a new trip has no budget

        repository.updateTrip(id, "u1", " Rome, Italy ", LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 18), null)
        repository.setBudget(id, "u1", 600_000)
        repository.setBudget(id, "u2", 1) // another account changes nothing
        repository.updateTrip(id, "u2", "Oslo", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "USD")

        val trip = repository.observeTrip(id, "u1").first()!!
        assertEquals("Rome, Italy", trip.destination)
        assertEquals(LocalDate.of(2026, 11, 13), trip.startDate)
        assertEquals(600_000L, trip.budgetAgorot)
    }

    @Test
    fun currency_isSetOnlyForTheOwner_andChangedByEditingTheTrip() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        assertNull(repository.observeTrip(id, "u1").first()?.currencyCode) // a new trip has no currency

        repository.setCurrency(id, "u1", "EUR")
        repository.setCurrency(id, "u2", "USD") // another account changes nothing
        assertEquals("EUR", repository.observeTrip(id, "u1").first()?.currencyCode)

        repository.updateTrip(id, "u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), "CHF")
        assertEquals("CHF", repository.observeTrip(id, "u1").first()?.currencyCode)
        repository.updateTrip(id, "u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), null)
        assertNull(repository.observeTrip(id, "u1").first()?.currencyCode)

        val withCurrency = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), "NOK")
        assertEquals("NOK", repository.observeTrip(withCurrency, "u1").first()?.currencyCode)
    }

    @Test
    fun exchangeRates_areCachedExactly_andReplacedPerCurrency() = runTest {
        val dao = db.exchangeRateDao()
        val fetched = Instant.parse("2026-10-05T08:00:00Z")
        dao.upsert(ExchangeRateEntity("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 1), fetched))
        dao.upsert(ExchangeRateEntity("EUR", BigDecimal("3.44100"), LocalDate.of(2026, 10, 2), fetched))

        val rate = dao.observe("EUR").first()!!
        assertEquals(BigDecimal("3.44100"), rate.rate)
        assertEquals(LocalDate.of(2026, 10, 2), rate.rateDate)
        assertNull(dao.observe("USD").first())
    }

    @Test
    fun expenses_newestFirst_scopedToOwner_andEditable() = runTest {
        val trip = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val older = expenses.addExpense(trip, "u1", " Flights ", 180_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1))!!
        val newer = expenses.addExpense(trip, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))!!
        assertNull(expenses.addExpense(trip, "u2", "Sneaky", 1, ExpenseCategory.OTHER, LocalDate.of(2026, 11, 13)))

        assertEquals(listOf(newer, older), expenses.observeExpenses(trip, "u1").first().map { it.id })
        assertEquals("Flights", expenses.observeExpense(older, "u1").first()?.name)
        assertEquals(emptyList<Expense>(), expenses.observeExpenses(trip, "u2").first())
        assertNull(expenses.observeExpense(older, "u2").first())

        expenses.updateExpense(newer, "u1", "Dinner", 13_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))
        expenses.deleteExpense(older, "u2") // another account deletes nothing
        assertEquals(listOf(13_000L, 180_000L), expenses.observeExpenses(trip, "u1").first().map { it.amountAgorot })

        expenses.deleteExpense(older, "u1")
        assertEquals(listOf(newer), expenses.observeExpenses(trip, "u1").first().map { it.id })
    }

    @Test
    fun deletingATrip_deletesItsExpenses() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val oslo = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        expenses.addExpense(rome, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))
        expenses.addExpense(oslo, "u1", "Taxi", 9_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 12, 1))

        // No delete-trip API exists yet; delete the row directly to check the foreign key cascade.
        db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = $rome")

        val remaining = db.openHelper.readableDatabase.query("SELECT trip_id FROM expenses").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(oslo), remaining)
    }
}
