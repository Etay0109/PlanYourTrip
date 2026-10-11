package com.example.planyourtrip.data.photo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.sync.NetworkMonitor
import com.example.planyourtrip.data.trip.AppDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room side of the destination photos: the table, observing by key, upserts and the repository on a real database. */
@RunWith(AndroidJUnit4::class)
class DestinationPhotoRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DestinationPhotoDao

    private val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.destinationPhotoDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_replacesTheRowOfAKey_andObserveReturnsOnlyTheAskedKeys() = runTest {
        dao.upsert(DestinationPhotoEntity("rome|", null, null, null, checkedAt = 1))
        dao.upsert(DestinationPhotoEntity("lisbon|", "https://a/l-960.jpg", "https://a/l-250.jpg", null, 2))
        dao.upsert(DestinationPhotoEntity("rome|", "https://a/r-960.jpg", "https://a/r-250.jpg", "https://c/r", 3))

        val rome = dao.get("rome|")
        assertEquals("https://a/r-960.jpg", rome?.cardUrl)
        assertEquals("https://c/r", rome?.filePageUrl)
        assertEquals(3L, rome?.checkedAt)

        assertEquals(listOf("rome|"), dao.observe(listOf("rome|", "paris|")).first().map { it.key })
        assertNull(dao.get("paris|"))
    }

    @Test
    fun repository_storesAFoundPhoto_andANotFound_inRoom() = runBlocking {
        val source = DestinationPhotoSource { query ->
            if (query.text == "Rome") {
                PhotoResult.Found("https://a/r-960.jpg", "https://a/r-250.jpg", "https://c/r", PhotoOrigin.LEAD, "r.jpg", "Rome")
            } else {
                PhotoResult.NotFound
            }
        }
        val online = object : NetworkMonitor {
            override val isOnline = MutableStateFlow(true)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = RoomDestinationPhotoRepository(dao, source, online, clock, scope)
            val rome = PhotoQuery("Rome")
            val nowhere = PhotoQuery("Qwxzv")

            repository.request(listOf(rome, nowhere))

            // Real time: the lookups run on Room's and the IO threads.
            val photos = withTimeout(5_000) {
                repository.observe(setOf(rome.key, nowhere.key)).first { it.isNotEmpty() }
            }
            assertEquals(mapOf(rome.key to DestinationPhoto("https://a/r-960.jpg", "https://a/r-250.jpg")), photos)
            val notFound = withTimeout(5_000) {
                var row = dao.get(nowhere.key)
                while (row == null) {
                    delay(20)
                    row = dao.get(nowhere.key)
                }
                row
            }
            assertEquals(clock.millis(), notFound.checkedAt)
            assertNull(notFound.cardUrl)
        } finally {
            scope.cancel()
        }
    }
}
