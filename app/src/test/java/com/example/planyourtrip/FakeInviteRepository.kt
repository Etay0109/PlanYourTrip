package com.example.planyourtrip

import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.data.sharing.InvitePreview
import com.example.planyourtrip.data.sharing.InviteRepository
import com.example.planyourtrip.data.sharing.InviteUnavailableException
import com.example.planyourtrip.data.sharing.JoinResult
import com.example.planyourtrip.data.sharing.TripFullException
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [InviteRepository] for ViewModel tests, never Firestore. [current] is each trip's open invite (as the
 * cloud would return it); [created] counts new codes; [previews] is what [lookUp] finds; [joinResult] what
 * [join] returns. Set [offline] to fail everything, [full] to refuse new codes.
 */
class FakeInviteRepository(private val now: () -> Long = { 0L }) : InviteRepository {
    val current = mutableMapOf<String, Invite?>()
    val created = mutableListOf<Pair<String, String>>()
    val cancelled = mutableListOf<String>()
    val previews = mutableMapOf<String, InvitePreview>()
    val lookups = mutableListOf<String>()
    val joins = mutableListOf<Pair<InvitePreview, String>>()
    var joinResult = JoinResult.JOINED
    var offline = false
    var full = false

    /** When set, [join] / [lookUp] wait for it. */
    var joinGate: CompletableDeferred<Unit>? = null
    var lookupGate: CompletableDeferred<Unit>? = null

    private var next = 0

    override suspend fun currentInvite(uid: String, tripUuid: String): Invite? {
        if (offline) throw InviteUnavailableException()
        return current[tripUuid]
    }

    override suspend fun createInvite(uid: String, tripUuid: String, ownerName: String): Invite {
        if (offline) throw InviteUnavailableException()
        if (full) throw TripFullException()
        val invite = Invite("CODE234${"ABCDEFGH"[next++ % 8]}", createdAt = now())
        created += tripUuid to ownerName
        current[tripUuid] = invite
        return invite
    }

    override suspend fun cancelInvite(uid: String, tripUuid: String, code: String) {
        if (offline) throw InviteUnavailableException()
        cancelled += code
        current[tripUuid] = null
    }

    override suspend fun lookUp(uid: String, code: String): InvitePreview? {
        lookups += code
        lookupGate?.await()
        if (offline) throw InviteUnavailableException()
        return previews[code]
    }

    override suspend fun join(uid: String, preview: InvitePreview, name: String): JoinResult {
        joins += preview to name
        joinGate?.await()
        if (offline) return JoinResult.FAILED
        return joinResult
    }
}
