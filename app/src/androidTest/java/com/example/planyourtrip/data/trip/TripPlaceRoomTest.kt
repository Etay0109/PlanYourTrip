package com.example.planyourtrip.data.trip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.sync.ItemData
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind
import com.example.planyourtrip.data.sync.planMerge
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room side of the trip's place (destination suggestions): stored, updated, kept, synced up and down. */
@RunWith(AndroidJUnit4::class)
class TripPlaceRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var trips: RoomTripRepository
    private lateinit var sync: RoomSyncLocalStore

    private val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)
    private val london = TripPlace("GB", 51.5074456, -0.1277653)
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov17 = LocalDate.of(2026, 11, 17)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        trips = RoomTripRepository(db.tripDao(), clock)
        sync = RoomSyncLocalStore(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun trip(id: Long) = trips.observeTrip(id, "u1").first()!!

    @Test
    fun aNewTrip_storesItsPlace_andFreeTextHasNone() = runTest {
        val picked = trips.addTrip("u1", "London, United Kingdom", nov12, nov17, "GBP", london)
        assertEquals(london, trip(picked).place)

        val free = trips.addTrip("u1", "London", nov12, nov17)
        assertEquals(TripPlace.NONE, trip(free).place)
    }

    @Test
    fun updating_replacesOrClearsThePlace_andBadValuesAreNeverStored() = runTest {
        val id = trips.addTrip("u1", "London", nov12, nov17)
        trips.updateTrip(id, "u1", "London, United Kingdom", nov12, nov17, null, london)
        assertEquals(london, trip(id).place)

        trips.updateTrip(id, "u1", "London and Oxford", nov12, nov17, null, TripPlace.NONE)
        assertEquals(TripPlace.NONE, trip(id).place)

        trips.updateTrip(id, "u1", "Somewhere", nov12, nov17, null, TripPlace("gbr", 95.0, 0.0))
        assertEquals(TripPlace.NONE, trip(id).place)
    }

    @Test
    fun theCurrencyAndTheBudget_keepThePlace() = runTest {
        val id = trips.addTrip("u1", "London, United Kingdom", nov12, nov17, null, london)
        trips.setCurrency(id, "u1", "GBP")
        trips.setBudget(id, "u1", 500_000)
        assertEquals(london, trip(id).place)
    }

    @Test
    fun thePlace_isUploaded_andACloudTripsPlaceIsStored() = runTest {
        val id = trips.addTrip("u1", "London, United Kingdom", nov12, nov17, null, london)
        val up = sync.snapshot("u1", pendingOnly = true).map { it.item }.single { it.kind == SyncKind.TRIP }
        val data = up.data as ItemData.TripData
        assertEquals(london, TripPlace(data.countryCode, data.latitude, data.longitude))

        // Another member renamed it on their phone and picked Rome.
        val rome = up.copy(
            updatedAt = up.updatedAt + 10,
            data = data.copy(destination = "Rome, Italy", countryCode = "IT", latitude = 41.89, longitude = 12.48),
        )
        sync.apply("u1", planMerge(sync.snapshot("u1", false), listOf(rome)))
        assertEquals("Rome, Italy", trip(id).destination)
        assertEquals(TripPlace("IT", 41.89, 12.48), trip(id).place)

        // A cloud version without a place (an older app) clears it.
        val plain = rome.copy(
            updatedAt = rome.updatedAt + 10,
            data = (rome.data as ItemData.TripData).copy(countryCode = null, latitude = null, longitude = null),
        )
        sync.apply("u1", planMerge(sync.snapshot("u1", false), listOf(plain)))
        assertNull(trip(id).countryCode)
        assertNull(trip(id).latitude)
    }

    @Test
    fun aTripArrivingFromTheCloud_withAPlace() = runTest {
        val cloud = SyncItem(
            SyncKind.TRIP, "t-1", null, 10,
            ItemData.TripData(
                "Tokyo, Japan", nov12, nov17, TripColor.SKY, "JPY", "u9", listOf("u9", "u1"), "JP", 35.68, 139.76,
            ),
        )
        sync.apply("u1", planMerge(emptyList(), listOf(cloud)))
        val id = trips.observeTrips("u1").first().single().id
        assertEquals(TripPlace("JP", 35.68, 139.76), trip(id).place)
    }
}
