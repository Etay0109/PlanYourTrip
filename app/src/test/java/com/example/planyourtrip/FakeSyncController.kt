package com.example.planyourtrip

import com.example.planyourtrip.data.sync.SignOutResult
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** [SyncController] for ViewModel tests: set [status] and [pendingOffline]; sign out goes to [auth]. */
class FakeSyncController(private val auth: FakeAuthRepository) : SyncController {
    override val status = MutableStateFlow(SyncStatus.SAVED)

    /** True = changes wait to upload and there is no connection. */
    var pendingOffline = false

    val signOutCalls = mutableListOf<Boolean>()

    override suspend fun signOut(force: Boolean): SignOutResult {
        signOutCalls += force
        if (pendingOffline && !force) return SignOutResult.NEEDS_CONFIRMATION
        auth.signOut()
        return SignOutResult.SIGNED_OUT
    }
}
