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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Clock

/**
 * Keeps the signed-in account's Room data and its cloud copy in step. Room stays the only source for the
 * screens; this class only moves data between Room ([SyncLocalStore]) and the cloud ([CloudStore]).
 *
 * - **Full sync** (download everything, [planMerge], apply here, upload): when an account signs in or the app
 *   starts signed in, and when the connection returns.
 * - **Quick sync** after each local change ([onLocalChange]): only the pending items and their cloud versions.
 * - One sync at a time; every sync works on the current account's items only.
 * - After a full sync, documents (on this device only) of trips that no longer exist are removed ([TripCleanup]).
 * - Once per account, before anything else, the account's legacy cloud copy (users/{uid}/trips, before shared trips)
 *   is moved to the shared layout and then deleted ([migrateLegacy], [SyncFlags] remembers that it is done). No
 *   merge runs before that worked, so a trip that isn't in the new layout yet is never deleted here.
 * - **Lost trips** (left, removed, or deleted by their owner): only a full sync decides, see [decideAccess]. A lost
 *   trip is removed from this device with its pending changes and documents, after [uid]'s own personal data of it
 *   was deleted in the cloud. A trip of my own is never removed that way. A quick sync that meets a trip it can't
 *   read leaves that trip's changes pending and asks for a full sync.
 * - After a full sync, the member documents of every trip are downloaded ([CloudStore.fetchMembers]).
 * - Each full sync's result is remembered per account ([SyncFlags.recordFullSync]): the status says "All changes
 *   saved" only after one succeeded, "Sync problem" while the last one failed for a reason other than the
 *   connection, and signing out removes the account's data from this device only when it is safely in the cloud.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManager(
    private val authRepository: AuthRepository,
    private val cloud: CloudStore,
    private val local: SyncLocalStore,
    private val network: NetworkMonitor,
    /** Remembers the one-time move of the legacy cloud copy. */
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
    /** The time of a successful full sync ("Last synced …"). */
    private val clock: Clock = Clock.systemUTC(),
    /** Warnings (a failed sync and why); the app logs them as "CloudSync", tests ignore them. */
    private val log: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) : SyncController, LocalChangeListener {

    private val mutex = Mutex()
    private val running = MutableStateFlow(false)
    private val lastAttemptFailed = MutableStateFlow(false)
    private val localChanges = Channel<Unit>(Channel.CONFLATED)
    private val fullSyncRequests = Channel<Unit>(Channel.CONFLATED)

    private val currentUid = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()

    /** How each account's last full sync ended, this process; [fullSyncOf] falls back to what [flags] remember. */
    private val fullSyncs = MutableStateFlow<Map<String, FullSyncState>>(emptyMap())

    private data class FullSyncState(val succeeded: Boolean, val problem: Boolean, val lastSuccessAt: Long?)

    private fun fullSyncOf(uid: String, known: Map<String, FullSyncState> = fullSyncs.value): FullSyncState? =
        known[uid] ?: flags.lastFullSync(uid)?.let { FullSyncState(it.succeeded, problem = false, it.lastSuccessAt) }

    override val status: StateFlow<SyncStatus> = currentUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SyncStatus.SAVED)
            } else {
                combine(
                    local.pendingCount(uid), running, network.isOnline, lastAttemptFailed, fullSyncs.map { fullSyncOf(uid, it) },
                ) { pending, run, online, failed, full -> statusOf(pending, run, online, failed, full) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SyncStatus.SAVED)

    override val lastSyncedAt: StateFlow<Long?> = currentUid
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else fullSyncs.map { fullSyncOf(uid, it)?.lastSuccessAt } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** "All changes saved" only when a full sync confirmed it; a failed full sync is never hidden by "nothing pending". */
    private fun statusOf(pending: Int, running: Boolean, online: Boolean, quickFailed: Boolean, full: FullSyncState?) =
        when {
            running -> SyncStatus.SYNCING
            !online -> if (pending == 0 && full?.succeeded == true) SyncStatus.SAVED else SyncStatus.OFFLINE
            full?.succeeded == false && full.problem -> SyncStatus.PROBLEM
            pending == 0 -> when (full?.succeeded) {
                true -> SyncStatus.SAVED
                // The account's first full sync here is about to start.
                null -> SyncStatus.SYNCING
                // The last full sync couldn't reach the cloud.
                false -> SyncStatus.OFFLINE
            }
            // About to be uploaded (the quick sync waits a moment after a change).
            !quickFailed -> SyncStatus.SYNCING
            else -> SyncStatus.OFFLINE
        }

    private fun recordFullSync(uid: String, succeeded: Boolean, problem: Boolean) {
        val lastSuccessAt = if (succeeded) clock.millis() else fullSyncOf(uid)?.lastSuccessAt
        flags.recordFullSync(uid, FullSyncRecord(succeeded, lastSuccessAt))
        fullSyncs.update { it + (uid to FullSyncState(succeeded, problem, lastSuccessAt)) }
    }

    /**
     * True when [uid]'s data on this device is known to be in the cloud: the legacy migration finished, the last full
     * sync succeeded, and nothing is pending.
     */
    private suspend fun isSafelyInCloud(uid: String): Boolean =
        flags.isSharedTripsMigrated(uid) && fullSyncOf(uid)?.succeeded == true && pendingCount(uid) == 0

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
                    launch {
                        fullSyncRequests.receiveAsFlow().collect { sync(uid, full = true) }
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
    suspend fun sync(uid: String, full: Boolean): Boolean = sync(uid, full, cleanUpDocuments = full)

    /** [cleanUpDocuments]: after a successful full sync, remove documents of trips that are gone (never on sign-out). */
    private suspend fun sync(uid: String, full: Boolean, cleanUpDocuments: Boolean): Boolean {
        if (!network.isOnline.value) {
            lastAttemptFailed.value = true
            return false
        }
        return mutex.withLock {
            running.value = true
            try {
                if (!flags.isSharedTripsMigrated(uid)) migrateLegacy(uid)
                var mine = local.snapshot(uid, pendingOnly = !full)
                if (!full && mine.isEmpty()) return@withLock true
                val theirs: List<SyncItem>
                if (full) {
                    // A failed list query throws here, so nothing below can remove a trip because of it.
                    val all = cloudCall { cloud.fetchAll(uid) }
                    val lost = removeLostTrips(uid, all)
                    mine = mine.filterNot { it.item.tripKey in lost }
                    theirs = all.filterNot { it.tripKey in lost }
                } else {
                    mine = withoutUnreadableTrips(uid, mine)
                    if (mine.isEmpty()) return@withLock true
                    theirs = cloudCall { cloud.fetch(uid, mine.map { it.item }) }
                }
                val plan = planMerge(mine, theirs)
                local.apply(uid, plan)
                // Trips first: a new trip must exist in the cloud before its children are written.
                for (chunk in plan.upload.sortedBy { it.kind.ordinal }.chunked(uploadChunkSize)) {
                    cloudCall { cloud.write(uid, chunk) }
                    chunk.forEach { local.markSynced(uid, it) }
                }
                if (full) {
                    val liveTrips = theirs.filter { it.kind == SyncKind.TRIP && !it.isDeleted }.map { it.uuid }
                    if (liveTrips.isNotEmpty()) local.replaceMembers(uid, cloudCall { cloud.fetchMembers(uid, liveTrips) })
                    // Every trip of the account is on this device now: documents of trips that are gone go too.
                    if (cleanUpDocuments) tripCleanup.afterFullSync(uid)
                    recordFullSync(uid, succeeded = true, problem = false)
                }
                lastAttemptFailed.value = false
                true
            } catch (e: CloudUnavailableException) {
                log("${if (full) "Full" else "Quick"} sync failed${if (e.isProblem) " (sync problem)" else ""}", e)
                if (full) recordFullSync(uid, succeeded = false, problem = e.isProblem)
                lastAttemptFailed.value = true
                false
            } finally {
                running.value = false
            }
        }
    }

    /**
     * Full sync only, after the list query succeeded with [listed]: reads the trips it didn't return (and the deleted
     * ones I don't own) directly and removes the ones [decideAccess] calls lost. My own trip that can't be read fails
     * the sync. Returns the removed trips' uuids.
     */
    private suspend fun removeLostTrips(uid: String, listed: List<SyncItem>): Set<String> {
        val owners = local.tripOwners(uid)
        val listedTrips = listed.filter { it.kind == SyncKind.TRIP }.associate { it.uuid to it.isDeleted }
        val toCheck = tripsToCheck(uid, owners, listedTrips)
        if (toCheck.isEmpty()) return emptySet()
        val access = cloudCall { cloud.tripAccess(uid, toCheck) }
        val decision = decideAccess(uid, owners, listedTrips, access)
        if (decision.ownTripsDenied.isNotEmpty()) {
            throw CloudUnavailableException(isProblem = true, message = "own trips not readable: ${decision.ownTripsDenied}")
        }
        if (decision.lost.isNotEmpty()) forgetTrips(uid, decision.lost)
        return decision.lost
    }

    /**
     * Quick sync: the pending items of trips I don't own whose trip can't be read right now (left, removed, deleted)
     * stay pending and are left out; a full sync is asked for, which decides whether the trip is lost.
     */
    private suspend fun withoutUnreadableTrips(uid: String, mine: List<LocalItem>): List<LocalItem> {
        val owners = local.tripOwners(uid)
        val foreign = mine.map { it.item.tripKey }.filter { it in owners && !isOwnTrip(owners[it], uid) }.toSet()
        if (foreign.isEmpty()) return mine
        val access = cloudCall { cloud.tripAccess(uid, foreign) }
        val unreadable = foreign.filter { access[it] != TripAccess.LIVE }.toSet()
        if (unreadable.isEmpty()) return mine
        fullSyncRequests.trySend(Unit)
        return mine.filterNot { it.item.tripKey in unreadable }
    }

    /**
     * Removes trips [uid] lost: first [uid]'s own personal data of them in the cloud (a failure fails the sync before
     * anything local changes), then the local copies with their pending changes, then their documents.
     */
    private suspend fun forgetTrips(uid: String, tripUuids: Set<String>) {
        for (trip in tripUuids) cloudCall { cloud.deletePersonal(uid, trip) }
        withContext(NonCancellable) {
            local.removeTrips(uid, tripUuids)
            tripUuids.forEach { tripCleanup.forgetTrip(uid, it) }
        }
    }

    override suspend fun leaveTrip(tripUuid: String): Boolean {
        val uid = authRepository.currentUser.value?.uid ?: return false
        if (!network.isOnline.value) return false
        return mutex.withLock {
            // The owner can't leave (they delete the trip instead).
            if (isOwnTrip(local.tripOwners(uid)[tripUuid], uid)) return@withLock false
            try {
                cloudCall { cloud.leaveTrip(uid, tripUuid) }
                withContext(NonCancellable) {
                    local.removeTrips(uid, setOf(tripUuid))
                    tripCleanup.forgetTrip(uid, tripUuid)
                }
                true
            } catch (e: CloudUnavailableException) {
                false
            }
        }
    }

    override suspend fun removeMember(tripUuid: String, memberUid: String): Boolean {
        val uid = authRepository.currentUser.value?.uid ?: return false
        if (!network.isOnline.value || memberUid == uid) return false
        val removed = mutex.withLock {
            try {
                cloudCall { cloud.removeMember(uid, tripUuid, memberUid) }
                true
            } catch (e: CloudUnavailableException) {
                false
            }
        }
        // The new member list (and their member document) comes down with a full sync.
        if (removed) sync(uid, full = true)
        return removed
    }

    override suspend fun refresh(): Boolean {
        val uid = authRepository.currentUser.value?.uid ?: return false
        return sync(uid, full = true)
    }

    override suspend fun signOut(force: Boolean): SignOutResult {
        val uid = authRepository.currentUser.value?.uid ?: return SignOutResult.SIGNED_OUT
        if (force) {
            // The data stays on the device (scoped to its owner) and uploads at the next sign-in here.
            authRepository.signOut()
            return SignOutResult.SIGNED_OUT
        }
        // A full sync (also the migration and the upload of pending changes) confirms the cloud copy. Offline it is
        // skipped and the last full sync's result counts: nothing changed here since, or something would be pending.
        sync(uid, full = true, cleanUpDocuments = false)
        if (pendingCount(uid) > 0) return SignOutResult.NEEDS_CONFIRMATION
        if (!isSafelyInCloud(uid)) return SignOutResult.NOT_CONFIRMED
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
     * Moves [uid]'s legacy cloud copy to the shared layout (see LegacyMigration.kt): copy what the new layout lacks or
     * has in an older version, read it back and check that everything arrived, and only then mark the legacy copy
     * migrated (old app versions can't read it any more) and delete it. Throws [CloudUnavailableException] when a step
     * fails; every step can be repeated, so the next sync simply starts again.
     */
    private suspend fun migrateLegacy(uid: String) {
        val legacy = convertLegacy(cloudCall { cloud.fetchLegacy(uid) }, uid)
        if (legacy.isNotEmpty()) {
            val copy = planLegacyCopy(legacy, cloudCall { cloud.fetchAll(uid) })
            for (chunk in copy.chunked(uploadChunkSize)) cloudCall { cloud.write(uid, chunk) }
            val left = planLegacyCopy(legacy, cloudCall { cloud.fetchAll(uid) })
            if (left.isNotEmpty()) {
                val what = left.groupingBy { it.kind }.eachCount()
                throw CloudUnavailableException(isProblem = true, message = "legacy copy incomplete, still missing: $what")
            }
        }
        cloudCall { cloud.markLegacyMigrated(uid) }
        cloudCall { cloud.deleteLegacy(uid) }
        flags.markSharedTripsMigrated(uid)
    }

    private suspend fun pendingCount(uid: String) = local.pendingCount(uid).first()

    /** The trip an item belongs to (a trip: itself). */
    private val SyncItem.tripKey: String get() = tripUuid ?: uuid

    private suspend fun <T> cloudCall(block: suspend () -> T): T =
        try {
            withTimeout(cloudTimeoutMillis) { block() }
        } catch (e: TimeoutCancellationException) {
            throw CloudUnavailableException(e, message = "cloud call timed out after $cloudTimeoutMillis ms")
        }
}
