package com.example.planyourtrip

import com.example.planyourtrip.data.sync.SignOutResult
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.sync.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [SyncController] for ViewModel tests: set [status], [lastSyncedAt], [pendingOffline], [notConfirmed],
 * [refreshResult], [leaveResult], [removeResult]; sign out goes to [auth].
 */
class FakeSyncController(private val auth: FakeAuthRepository) : SyncController {
    override val status = MutableStateFlow(SyncStatus.SAVED)
    override val lastSyncedAt = MutableStateFlow<Long?>(null)

    /** True = changes wait to upload and there is no connection. */
    var pendingOffline = false

    /** True = nothing is pending, but no full sync confirmed the cloud copy ([SignOutResult.NOT_CONFIRMED]). */
    var notConfirmed = false

    val signOutCalls = mutableListOf<Boolean>()

    /** What [refresh] returns; [refreshCalls] counts them. */
    var refreshResult = true
    var refreshCalls = 0

    /** When set, [refresh] waits for it (a refresh "running"). */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(): Boolean {
        refreshCalls++
        refreshGate?.await()
        return refreshResult
    }

    /** What [leaveTrip] / [removeMember] return; the calls are recorded. */
    var leaveResult = true
    val leaveCalls = mutableListOf<String>()
    var removeResult = true
    val removeCalls = mutableListOf<Pair<String, String>>()

    /** When set, [removeMember] waits for it (a removal "running"). */
    var removeGate: CompletableDeferred<Unit>? = null

    override suspend fun leaveTrip(tripUuid: String): Boolean {
        leaveCalls += tripUuid
        return leaveResult
    }

    override suspend fun removeMember(tripUuid: String, memberUid: String): Boolean {
        removeCalls += tripUuid to memberUid
        removeGate?.await()
        return removeResult
    }

    override suspend fun signOut(force: Boolean): SignOutResult {
        signOutCalls += force
        if (pendingOffline && !force) return SignOutResult.NEEDS_CONFIRMATION
        if (notConfirmed && !force) return SignOutResult.NOT_CONFIRMED
        auth.signOut()
        return SignOutResult.SIGNED_OUT
    }
}
