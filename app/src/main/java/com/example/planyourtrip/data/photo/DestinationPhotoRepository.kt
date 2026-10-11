package com.example.planyourtrip.data.photo

import com.example.planyourtrip.data.sync.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.Duration

/** A lookup that found nothing (or got an unreadable answer) is tried again after a week. */
val PHOTO_RETRY_AFTER: Duration = Duration.ofDays(7)
val PHOTO_RETRY_AFTER_MILLIS: Long = PHOTO_RETRY_AFTER.toMillis()

/** After a connection failure while online (timeout, server error) a destination waits this long. */
val PHOTO_CONNECTION_RETRY_MILLIS: Long = Duration.ofMinutes(1).toMillis()

/** Destination photos for the screens: Room for reading, lookups in the background. */
interface DestinationPhotoRepository {
    /** The found photos of these keys ([photoKey]); a key without a photo is missing from the map. */
    fun observe(keys: Set<String>): Flow<Map<String, DestinationPhoto>>

    /** Looks up the destinations that have no answer yet (or a stale "nothing found"), one at a time. */
    fun request(queries: Collection<PhotoQuery>)

    /** No photos at all (previews and tests that don't need them). */
    object None : DestinationPhotoRepository {
        override fun observe(keys: Set<String>): Flow<Map<String, DestinationPhoto>> = flowOf(emptyMap())
        override fun request(queries: Collection<PhotoQuery>) = Unit
    }
}

/**
 * Room + a [DestinationPhotoSource]. A found photo is kept for good; "nothing found" and an unreadable answer are
 * stored and retried after [PHOTO_RETRY_AFTER]. A connection failure is not stored: offline nothing is tried, and
 * the destinations wait until the connection is back (or [PHOTO_CONNECTION_RETRY_MILLIS] after a failure while
 * online), so a phone that started offline doesn't wait a week for its photos.
 *
 * Every lookup runs in one consumer coroutine, so a destination is never looked up twice at once.
 */
class RoomDestinationPhotoRepository(
    private val dao: DestinationPhotoDao,
    private val source: DestinationPhotoSource,
    private val network: NetworkMonitor,
    private val clock: Clock,
    scope: CoroutineScope,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) : DestinationPhotoRepository {

    private sealed interface Message {
        data class Lookup(val query: PhotoQuery) : Message
        data object Reconnected : Message
    }

    private val messages = Channel<Message>(Channel.UNLIMITED)

    // Owned by the consumer coroutine only.
    private val waitingForNetwork = LinkedHashMap<String, PhotoQuery>()
    private val connectionFailedAt = HashMap<String, Long>()

    init {
        scope.launch {
            for (message in messages) {
                when (message) {
                    is Message.Lookup -> lookUp(message.query)
                    Message.Reconnected -> {
                        connectionFailedAt.clear()
                        val waiting = waitingForNetwork.values.toList()
                        waitingForNetwork.clear()
                        waiting.forEach { lookUp(it) }
                    }
                }
            }
        }
        scope.launch {
            // Only a change from offline to online counts (not the current state when the app starts).
            var wasOnline = true
            network.isOnline.collect { online ->
                if (online && !wasOnline) messages.trySend(Message.Reconnected)
                wasOnline = online
            }
        }
    }

    override fun observe(keys: Set<String>): Flow<Map<String, DestinationPhoto>> =
        if (keys.isEmpty()) {
            flowOf(emptyMap())
        } else {
            dao.observe(keys.toList()).map { rows ->
                rows.mapNotNull { row -> row.toPhoto()?.let { row.key to it } }.toMap()
            }.distinctUntilChanged()
        }

    override fun request(queries: Collection<PhotoQuery>) {
        queries.filter { it.text.isNotBlank() }.distinctBy { it.key }
            .forEach { messages.trySend(Message.Lookup(it)) }
    }

    private suspend fun lookUp(query: PhotoQuery) {
        val key = query.key
        val now = clock.millis()
        val known = dao.get(key)
        if (known != null && (known.cardUrl != null || now - known.checkedAt < PHOTO_RETRY_AFTER_MILLIS)) return
        if (!network.isOnline.value) {
            waitingForNetwork[key] = query
            return
        }
        connectionFailedAt[key]?.let { if (now - it < PHOTO_CONNECTION_RETRY_MILLIS) return }
        val result = try {
            source.find(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log("Photo lookup failed (connection), will retry: ${query.text}", e)
            connectionFailedAt[key] = clock.millis()
            waitingForNetwork[key] = query
            return
        } catch (e: Exception) {
            // An unreadable answer: remembered like "nothing found".
            log("Photo lookup failed: ${query.text}", e)
            PhotoResult.NotFound
        }
        connectionFailedAt.remove(key)
        waitingForNetwork.remove(key)
        dao.upsert(
            when (result) {
                is PhotoResult.Found ->
                    DestinationPhotoEntity(key, result.cardUrl, result.thumbUrl, result.filePageUrl, clock.millis())
                PhotoResult.NotFound -> DestinationPhotoEntity(key, null, null, null, clock.millis())
            }
        )
    }
}
