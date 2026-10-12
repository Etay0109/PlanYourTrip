package com.example.planyourtrip.data.photo

import com.example.planyourtrip.FakeDestinationPhotoDao
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakePhotoSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DestinationPhotoRepositoryTest {

    private class MutableClock(var millis: Long) : Clock() {
        override fun millis(): Long = millis
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val dao = FakeDestinationPhotoDao()
    private val source = FakePhotoSource()
    private val network = FakeNetworkMonitor()
    private val clock = MutableClock(1_000_000_000L)

    private val rome = PhotoQuery("Rome, Italy", 41.8933203, 12.4829321)
    private val london = PhotoQuery("London, Canada", 42.9832406, -81.243372)

    private fun TestScope.repository() =
        RoomDestinationPhotoRepository(dao, source, network, clock, backgroundScope)

    private suspend fun RoomDestinationPhotoRepository.photoOf(query: PhotoQuery): DestinationPhoto? =
        observe(setOf(query.key)).first()[query.key]

    @Test
    fun found_isStoredWithItsFilePage_andNeverLookedUpAgain() = runTest {
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))
        val repository = repository()

        repository.request(listOf(rome))
        runCurrent()

        assertEquals(DestinationPhoto("https://img.test/rome-960.jpg", "https://img.test/rome-250.jpg"), repository.photoOf(rome))
        assertEquals("https://commons.test/File:rome.jpg", dao.rows.value[rome.key]?.filePageUrl)

        // Months later, still not asked again.
        clock.millis += PHOTO_RETRY_AFTER_MILLIS * 10
        repository.request(listOf(rome))
        runCurrent()
        assertEquals(1, source.queries.size)
    }

    @Test
    fun notFound_isRemembered_andRetriedOnlyAfterAWeek() = runTest {
        val repository = repository()

        repository.request(listOf(rome))
        runCurrent()
        assertNull(repository.photoOf(rome))
        assertEquals(clock.millis, dao.rows.value[rome.key]?.checkedAt)

        // Every open of a screen asks again: nothing is searched within the week.
        clock.millis += PHOTO_RETRY_AFTER_MILLIS - 1
        repository.request(listOf(rome))
        runCurrent()
        assertEquals(1, source.queries.size)

        // After a week it is searched again, and this time Wikipedia has a photo.
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))
        clock.millis += 1
        repository.request(listOf(rome))
        runCurrent()
        assertEquals(2, source.queries.size)
        assertEquals("https://img.test/rome-960.jpg", repository.photoOf(rome)?.cardUrl)
    }

    @Test
    fun anUnreadableAnswer_isRememberedLikeNotFound() = runTest {
        source.unreadable = true
        val repository = repository()

        repository.request(listOf(rome))
        runCurrent()
        repository.request(listOf(rome))
        runCurrent()

        assertEquals(1, source.queries.size)
        assertNull(dao.rows.value[rome.key]?.cardUrl)
        assertNull(repository.photoOf(rome))
    }

    @Test
    fun offline_nothingIsTriedOrStored_andTheLookupRunsWhenTheConnectionReturns() = runTest {
        network.isOnline.value = false
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))
        val repository = repository()

        repository.request(listOf(rome))
        runCurrent()
        assertTrue(source.queries.isEmpty())
        assertTrue(dao.rows.value.isEmpty())

        network.isOnline.value = true
        runCurrent()

        assertEquals(listOf(rome), source.queries)
        assertEquals("https://img.test/rome-960.jpg", repository.photoOf(rome)?.cardUrl)
    }

    @Test
    fun aConnectionFailure_isNotRemembered_andRetriedAfterAMinuteOrWhenBackOnline() = runTest {
        source.offline = true
        val repository = repository()

        repository.request(listOf(rome))
        runCurrent()
        assertEquals(1, source.queries.size)
        assertTrue(dao.rows.value.isEmpty())

        // Right away: not hammered.
        repository.request(listOf(rome))
        runCurrent()
        assertEquals(1, source.queries.size)

        // A minute later the next screen asks again.
        source.offline = false
        clock.millis += PHOTO_CONNECTION_RETRY_MILLIS
        repository.request(listOf(rome))
        runCurrent()
        assertEquals(2, source.queries.size)
        assertNull(repository.photoOf(rome))
        assertEquals(clock.millis, dao.rows.value[rome.key]?.checkedAt)

        // Back online after a failure: retried without waiting.
        source.offline = true
        repository.request(listOf(london))
        runCurrent()
        source.offline = false
        network.isOnline.value = false
        runCurrent()
        network.isOnline.value = true
        runCurrent()
        assertEquals(listOf(rome, rome, london, london), source.queries)
        assertTrue(dao.rows.value.containsKey(london.key))
    }

    @Test
    fun anEditedDestination_isANewLookup_andTheOldPhotoStaysForTheOldText() = runTest {
        source.results = mapOf(
            "Rome, Italy" to FakePhotoSource.found("rome"),
            "London, Canada" to FakePhotoSource.found("london-ontario"),
        )
        val repository = repository()
        repository.request(listOf(rome))
        runCurrent()

        // The trip's destination is edited from Rome to London, Canada.
        repository.request(listOf(london))
        runCurrent()

        assertEquals(listOf(rome, london), source.queries)
        assertEquals("https://img.test/london-ontario-960.jpg", repository.photoOf(london)?.cardUrl)
        assertEquals("https://img.test/rome-960.jpg", repository.photoOf(rome)?.cardUrl)
    }

    @Test
    fun manyScreensAskingAtOnce_lookUpEachDestinationOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))
        val repository = repository()

        repeat(3) { repository.request(listOf(rome, rome.copy(text = "  rome, ITALY "))) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, source.queries.size)
    }

    @Test
    fun blankDestinations_areNeverLookedUp_andNoKeysObserveNothing() = runTest {
        val repository = repository()
        repository.request(listOf(PhotoQuery("   ")))
        runCurrent()

        assertTrue(source.queries.isEmpty())
        assertEquals(emptyMap<String, DestinationPhoto>(), repository.observe(emptySet()).first())
    }

    @Test
    fun observe_returnsOnlyFoundPhotos_forTheAskedKeys() = runTest {
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))
        val repository = repository()
        repository.request(listOf(rome, london))
        runCurrent()

        val photos = repository.observe(setOf(rome.key, london.key)).first()

        assertEquals(setOf(rome.key), photos.keys)
    }

    // --- One-time retry of "nothing found" ----------------------------------------------------------------------

    private class FakeRetryFlags(var version: Int = 0) : PhotoRetryFlags {
        override fun clearedVersion(): Int = version
        override fun markCleared(version: Int) {
            this.version = version
        }
    }

    @Test
    fun afterTheUpdate_storedNotFoundRowsAreClearedOnce_andLookedUpAgainRightAway() = runTest {
        // Before the update: Rome had no photo (checked a minute ago), London had one.
        dao.upsert(DestinationPhotoEntity(rome.key, null, null, null, clock.millis - 60_000))
        dao.upsert(DestinationPhotoEntity(london.key, "https://img.test/l-960.jpg", "https://img.test/l-250.jpg", null, 1))
        val flags = FakeRetryFlags()
        source.results = mapOf("Rome, Italy" to FakePhotoSource.found("rome"))

        val repository = RoomDestinationPhotoRepository(dao, source, network, clock, backgroundScope, retryFlags = flags)
        runCurrent()
        assertNull(dao.rows.value[rome.key])
        assertEquals("https://img.test/l-960.jpg", dao.rows.value[london.key]?.cardUrl)
        assertEquals(PhotoRetryFlags.CURRENT_VERSION, flags.version)

        repository.request(listOf(rome, london))
        runCurrent()
        assertEquals(listOf(rome), source.queries)
        assertEquals("https://img.test/rome-960.jpg", repository.photoOf(rome)?.cardUrl)
    }

    @Test
    fun theClearRunsOnlyOnce_aLaterNotFoundWaitsItsWeekAgain() = runTest {
        val flags = FakeRetryFlags(version = PhotoRetryFlags.CURRENT_VERSION)
        dao.upsert(DestinationPhotoEntity(rome.key, null, null, null, clock.millis - 60_000))

        val repository = RoomDestinationPhotoRepository(dao, source, network, clock, backgroundScope, retryFlags = flags)
        runCurrent()
        repository.request(listOf(rome))
        runCurrent()

        assertTrue(dao.rows.value.containsKey(rome.key))
        assertTrue(source.queries.isEmpty())
    }
}
