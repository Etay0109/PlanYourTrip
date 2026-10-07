package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Keeps the signed-in account's Room data and its cloud copy in step. Room stays the only source for the
 * screens; this class only moves data between Room ([SyncLocalStore]) and the cloud ([CloudStore]).
 *
 * - **Full sync** (download everything, [planMerge], apply here, upload): when an account signs in or the app
 *   starts signed in, and when the connection returns.
 * - **Quick sync** after each local change ([onLocalChange]): only the pending items and their cloud versions.
 * - One sync at a time; every sync works on the current account's items only.
 * - After a full sync, documents (on this device only) of trips that no longer exist are removed ([TripCleanup]).
 * - Once per account, a full sync also deletes the account's old place documents from the cloud (places were synced
 *   until the Map tab was removed; [SyncFlags] remembers that it is done).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManager(
    private val authRepository: AuthRepository,
    private val cloud: CloudStore,
    private val local: SyncLocalStore,
    private val network: NetworkMonitor,
    /** Remembers the one-time removal of the old place documents. */
    private val flags: SyncFlags,
    /** Removes this device's documents of deleted trips after each full sync (never on sign-out). */
    private val tripCleanup: TripCleanup,
    /** Application-wide scope; tests pass their test scope. */
    private val scope: CoroutineScope,
    /** Waits this long after a local change, so a burst of edits is uploaded together. */
    private val quickSyncDelayMillis: Long = 1_000,
    /** Upper limit for one cloud call (download or one upload chunk). */
    private val cloudTimeoutMillis: Long = 20_000,
    /** Items per cloud write; each chunk is marked synced as soon as it is written. */
    private val uploadChunkSize: Int = 100,
) : SyncController, LocalChangeListener {

    private val mutex = Mutex()
    private val running = MutableStateFlow(false)
    private val lastAttemptFailed = MutableStateFlow(false)
    private val localChanges = Channel<Unit>(Channel.CONFLATED)

    private val currentUid = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    override val status: StateFlow<SyncStatus> = currentUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SyncStatus.SAVED)
            } else {
                combine(local.pendingCount(uid), running, network.isOnline, lastAttemptFailed) { pending, run, online, failed ->
                    when {
                        run -> SyncStatus.SYNCING
                        pending == 0 -> SyncStatus.SAVED
                        // About to be uploaded (the quick sync waits a moment after a change).
                        online && !failed -> SyncStatus.SYNCING
                        else -> SyncStatus.OFFLINE
                    }
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SyncStatus.SAVED)

    /** Starts following the session. Call once (PlanYourTripApplication). */
    fun start() {
        scope.launch {
            currentUid.collectLatest { uid ->
                if (uid == null) return@collectLatest
                // Cancelled as soon as the account changes or signs out.
                coroutineScope {
                    launch {
                        network.isOnline.drop(1).filter { it }.collect { sync(uid, full = true) }
                    }
                    launch {
                        localChanges.receiveAsFlow().collect {
                            delay(quickSyncDelayMillis)
                            sync(uid, full = false)
                        }
                    }
                    sync(uid, full = true)
                }
            }
        }
    }

    override fun onLocalChange() {
        localChanges.trySend(Unit)
    }

    /**
     * One sync of [uid]. Returns true when everything it set out to do reached the cloud. A failure (offline,
     * timeout, rules) leaves the pending items as they are, so the next sync picks them up again.
     */
    suspend fun sync(uid: String, full: Boolean): Boolean {
        if (!network.isOnline.value) {
            lastAttemptFailed.value = true
            return false
        }
        return mutex.withLock {
            running.value = true
            try {
                val mine = local.snapshot(uid, pendingOnly = !full)
                if (!full && mine.isEmpty()) return@withLock true
                val theirs = cloudCall { if (full) cloud.fetchAll(uid) else cloud.fetch(uid, mine.map { it.item }) }
                val plan = planMerge(mine, theirs)
                local.apply(uid, plan)
                for (chunk in plan.upload.chunked(uploadChunkSize)) {
                    cloudCall { cloud.write(uid, chunk) }
                    chunk.forEach { local.markSynced(uid, it) }
                }
                if (full) {
                    removeLegacyPlaces(uid)
                    // Every trip of the account is on this device now: documents of trips that are gone go too.
                    tripCleanup.afterFullSync(uid)
                }
                lastAttemptFailed.value = false
                true
            } catch (e: CloudUnavailableException) {
                lastAttemptFailed.value = true
                false
            } finally {
                running.value = false
            }
        }
    }

    override suspend fun signOut(force: Boolean): SignOutResult {
        val uid = authRepository.currentUser.value?.uid ?: return SignOutResult.SIGNED_OUT
        if (force) {
            // The data stays on the device (scoped to its owner) and uploads at the next sign-in here.
            authRepository.signOut()
            return SignOutResult.SIGNED_OUT
        }
        if (pendingCount(uid) > 0) sync(uid, full = false)
        if (pendingCount(uid) > 0) return SignOutResult.NEEDS_CONFIRMATION
        // Everything is in the cloud: sign out, then remove the account's data from this device,
        // even if the caller's scope ends with the navigation to Login.
        withContext(NonCancellable) {
            authRepository.signOut()
            // Under the lock, so a sync that is just finishing can't write the account's data back.
            mutex.withLock { local.deleteAllFor(uid) }
        }
        return SignOutResult.SIGNED_OUT
    }

    /**
     * Deletes the account's old place documents once. A failure never fails the sync: the next full sync tries
     * again, and the flag is only set after the cloud confirmed the delete.
     */
    private suspend fun removeLegacyPlaces(uid: String) {
        if (flags.isPlacesCleanupDone(uid)) return
        try {
            cloudCall { cloud.deleteLegacyPlaces(uid) }
            flags.markPlacesCleanupDone(uid)
        } catch (e: CloudUnavailableException) {
            // Retried at the next full sync.
        }
    }

    private suspend fun pendingCount(uid: String) = local.pendingCount(uid).first()

    private suspend fun <T> cloudCall(block: suspend () -> T): T =
        try {
            withTimeout(cloudTimeoutMillis) { block() }
        } catch (e: TimeoutCancellationException) {
            throw CloudUnavailableException(e)
        }
}
