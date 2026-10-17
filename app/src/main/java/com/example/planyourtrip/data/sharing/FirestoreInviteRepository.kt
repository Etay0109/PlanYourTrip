package com.example.planyourtrip.data.sharing

import com.example.planyourtrip.AppLog
import com.example.planyourtrip.data.sync.NetworkMonitor
import com.example.planyourtrip.data.sync.SharedTripDocuments
import com.example.planyourtrip.data.sync.SharedTripDocuments.Invite as InviteField
import com.example.planyourtrip.data.sync.SharedTripDocuments.Member as MemberField
import com.example.planyourtrip.data.sync.SyncDocuments
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Invites in Cloud Firestore: invites/{code}, the owner's pointer trips/{trip}/private/invite and the member
 * documents trips/{trip}/members/{uid}. Every write is one batch, so firestore.rules can check its parts against
 * each other (see the rules for what each write must contain). Writes are only sent while online, so a write that
 * reported a failure isn't applied later by the SDK's queue.
 */
class FirestoreInviteRepository(
    private val firestore: FirebaseFirestore,
    private val network: NetworkMonitor,
    private val timeoutMillis: Long = 20_000,
) : InviteRepository {

    override suspend fun currentInvite(uid: String, tripUuid: String): Invite? = call("currentInvite") {
        val code = pointer(tripUuid).get(Source.SERVER).await().getString(SharedTripDocuments.POINTER_CODE)
            ?: return@call null
        // Readable only while open and unexpired; anything else (used, cancelled, expired) is denied.
        val doc = readOrNull(invites().document(code)) ?: return@call null
        val createdAt = doc.getTimestamp(InviteField.CREATED_AT)?.toDate()?.time ?: return@call null
        Invite(code, createdAt)
    }

    override suspend fun createInvite(uid: String, tripUuid: String, ownerName: String): Invite =
        call("createInvite") {
            requireOnline()
            val trip = trips().document(tripUuid).get(Source.SERVER).await()
            if (!trip.exists() || trip.getBoolean(SyncDocuments.DELETED) == true ||
                trip.getString(SyncDocuments.OWNER_ID) != uid
            ) {
                throw InviteUnavailableException()
            }
            val members = trip.get(SyncDocuments.MEMBER_IDS) as? List<*>
            if ((members?.size ?: 0) >= SharedTripDocuments.MAX_MEMBERS) throw TripFullException()
            // A code that already exists is refused by the rules (it would be an update): try another one.
            repeat(CODE_ATTEMPTS) {
                val code = InviteCode.generate()
                val batch = firestore.batch()
                batch.set(member(tripUuid, uid), memberFields(ownerName, inviteCode = null))
                batch.set(
                    invites().document(code),
                    mapOf(
                        InviteField.TRIP_ID to tripUuid,
                        InviteField.INVITER_NAME to ownerName,
                        // Copied exactly as stored: the rules compare them with the trip's.
                        InviteField.DESTINATION to trip.get(InviteField.DESTINATION),
                        InviteField.START_DATE to trip.get(InviteField.START_DATE),
                        InviteField.END_DATE to trip.get(InviteField.END_DATE),
                        InviteField.CREATED_AT to FieldValue.serverTimestamp(),
                        InviteField.STATUS to InviteField.OPEN,
                        InviteField.USED_BY to null,
                    ),
                )
                batch.set(pointer(tripUuid), mapOf(SharedTripDocuments.POINTER_CODE to code))
                try {
                    batch.commit().await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return@repeat
                    throw e
                }
                val createdAt = invites().document(code).get(Source.SERVER).await()
                    .getTimestamp(InviteField.CREATED_AT)?.toDate()?.time ?: throw InviteUnavailableException()
                return@call Invite(code, createdAt)
            }
            throw InviteUnavailableException()
        }

    override suspend fun cancelInvite(uid: String, tripUuid: String, code: String) = call("cancelInvite") {
        requireOnline()
        val batch = firestore.batch()
        batch.update(invites().document(code), InviteField.STATUS, InviteField.CANCELLED)
        batch.set(pointer(tripUuid), mapOf(SharedTripDocuments.POINTER_CODE to null))
        batch.commit().await()
        Unit
    }

    override suspend fun lookUp(uid: String, code: String): InvitePreview? = call("lookUp") {
        if (!InviteCode.isValid(code)) return@call null
        readOrNull(invites().document(code))?.let { toPreview(code, it) }
    }

    override suspend fun join(uid: String, preview: InvitePreview, name: String): JoinResult =
        try {
            call("join") {
                requireOnline()
                // A member can read the trip; anyone else is denied.
                val trip = readOrNull(trips().document(preview.tripUuid))
                val memberIds = trip?.get(SyncDocuments.MEMBER_IDS) as? List<*>
                if (memberIds != null && uid in memberIds) return@call JoinResult.ALREADY_MEMBER
                val batch = firestore.batch()
                batch.update(
                    invites().document(preview.code),
                    mapOf(InviteField.STATUS to InviteField.USED, InviteField.USED_BY to uid),
                )
                batch.update(trips().document(preview.tripUuid), SyncDocuments.MEMBER_IDS, FieldValue.arrayUnion(uid))
                batch.set(member(preview.tripUuid, uid), memberFields(name, inviteCode = preview.code))
                try {
                    batch.commit().await()
                    JoinResult.JOINED
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    // Still open and readable: the only other reason the rules refuse is a full trip. Otherwise the
                    // code was used, cancelled or expired meanwhile.
                    val again = readOrNull(invites().document(preview.code))
                    if (again != null && again.getString(InviteField.TRIP_ID) == preview.tripUuid) {
                        JoinResult.TRIP_FULL
                    } else {
                        JoinResult.INVALID_CODE
                    }
                }
            }
        } catch (e: InviteUnavailableException) {
            JoinResult.FAILED
        }

    private fun memberFields(name: String, inviteCode: String?) = mapOf(
        MemberField.NAME to name,
        MemberField.JOINED_AT to FieldValue.serverTimestamp(),
        MemberField.LEFT to false,
        MemberField.INVITE_CODE to inviteCode,
    )

    private fun toPreview(code: String, doc: DocumentSnapshot): InvitePreview? {
        val tripUuid = doc.getString(InviteField.TRIP_ID) ?: return null
        val destination = doc.getString(InviteField.DESTINATION) ?: return null
        val start = doc.getString(InviteField.START_DATE) ?: return null
        val end = doc.getString(InviteField.END_DATE) ?: return null
        val inviterName = doc.getString(InviteField.INVITER_NAME) ?: return null
        return try {
            InvitePreview(code, tripUuid, destination, LocalDate.parse(start), LocalDate.parse(end), inviterName)
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "Unreadable invite dates", e)
            null
        }
    }

    /** The document, or null when it doesn't exist or the rules deny reading it. */
    private suspend fun readOrNull(ref: DocumentReference): DocumentSnapshot? =
        try {
            ref.get(Source.SERVER).await().takeIf { it.exists() }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
        }

    private fun requireOnline() {
        if (!network.isOnline.value) throw InviteUnavailableException()
    }

    private fun trips() = firestore.collection(SharedTripDocuments.TRIPS)

    private fun invites() = firestore.collection(SharedTripDocuments.INVITES)

    private fun member(tripUuid: String, uid: String) =
        trips().document(tripUuid).collection(SharedTripDocuments.MEMBERS).document(uid)

    private fun pointer(tripUuid: String) =
        trips().document(tripUuid).collection(SharedTripDocuments.PRIVATE).document(SharedTripDocuments.INVITE_POINTER)

    /** Every failure (offline, timeout, refused) becomes an [InviteUnavailableException]; [TripFullException] stays. */
    private suspend fun <T> call(name: String, block: suspend () -> T): T =
        try {
            withTimeout(timeoutMillis) { block() }
        } catch (e: TimeoutCancellationException) {
            AppLog.w(TAG, "$name timed out")
            throw InviteUnavailableException(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InviteUnavailableException) {
            throw e
        } catch (e: TripFullException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "$name failed", e)
            throw InviteUnavailableException(e)
        }

    private companion object {
        const val TAG = "Invites"
        const val CODE_ATTEMPTS = 5
    }
}
