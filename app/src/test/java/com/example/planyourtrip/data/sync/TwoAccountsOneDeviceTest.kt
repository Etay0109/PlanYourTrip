package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two accounts on one phone (one local store, one set of flags, one manager following the session), both with a
 * legacy cloud copy that isn't migrated on this phone yet: Alice owns London and Rome, Bob joins London. The fake
 * cloud keeps budgets where Firestore does (trips/{trip}/memberData/{uid}), which is what the phone ran into on
 * 2026-10-09: budgets read back under the member's uid, so the migration could never finish.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoAccountsOneDeviceTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")
    private val bob = AuthUser(uid = "bob", displayName = "Bob", email = "bob@example.com")

    private val fake = FakeCloudStore()
    private val phone = FakeSyncLocalStore()
    private val flags = FakeSyncFlags()
    private val auth = FakeAuthRepository(alice)

    private val london = trip("london", updatedAt = 100, destination = "London")
    private val rome = trip("rome", updatedAt = 100, destination = "Rome")
    private val prague = trip("prague", updatedAt = 100, destination = "Prague", owner = "bob")

    /** Alice's legacy copy (users/alice/trips): budgets in the trip documents, her key "me". */
    private val aliceLegacy = listOf(
        london, budget("london", updatedAt = 100), expense("e1", "london", updatedAt = 100, paidBy = "me"),
        rome, budget("rome", updatedAt = 100, budgetAgorot = null),
    )

    /** Bob's own legacy copy, from before he joined anything. */
    private val bobLegacy = listOf(prague, budget("prague", updatedAt = 100))

    private fun TestScope.manager() = SyncManager(
        authRepository = auth,
        cloud = fake,
        local = phone,
        network = FakeNetworkMonitor(online = true),
        flags = flags,
        tripCleanup = TripCleanup.None,
        scope = backgroundScope,
    )

    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    /** As on the phone: the v14 Room migration converted Alice's trips, and an earlier run already copied them. */
    private fun givenAlicesPhoneAndCloud() {
        fake.legacy["alice"] = aliceLegacy.toMutableList()
        fake.legacy["bob"] = bobLegacy.toMutableList()
        convertLegacy(aliceLegacy, "alice").forEach { phone.edit("alice", it, pending = false) }
        fake.put("alice", *convertLegacy(aliceLegacy, "alice").toTypedArray())
        fake.put("bob", *convertLegacy(bobLegacy, "bob").toTypedArray())
    }

    @Test
    fun signOutOfA_joinAsB_signBackInAsA_bothSeeTheirTrips() = runTest {
        givenAlicesPhoneAndCloud()
        val sync = manager()
        sync.start()
        settle()

        // 1. Alice signs out with everything uploaded: her rows leave the phone (as designed).
        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = false))
        settle()

        // 2. Bob signs in, joins London with Alice's code (what FirestoreInviteRepository.join writes), refreshes.
        auth.currentUser.value = bob
        settle()
        fake.addMember("london", "bob")
        assertTrue("the refresh after joining failed", sync.refresh())
        assertEquals(setOf("london", "prague"), tripsOf("bob"))

        // 3. Bob signs out, Alice signs in again: her trips come back down.
        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = false))
        auth.currentUser.value = alice
        settle()
        assertEquals(setOf("london", "rome"), tripsOf("alice"))
        assertTrue("alice" in flags.sharedTripsMigrated && "bob" in flags.sharedTripsMigrated)
    }

    @Test
    fun theMigration_neverOverwritesANewerBudgetInTheCloud() = runTest {
        givenAlicesPhoneAndCloud()
        // Alice changed her London budget on another phone after the copy.
        fake.put("alice", budget("london", updatedAt = 500, budgetAgorot = 900_000))

        manager().sync("alice", full = true)

        assertEquals(budget("london", updatedAt = 500, budgetAgorot = 900_000), fake.docsOf("alice")["budget:london"])
    }

    @Test
    fun aBudgetReadFromItsDocument_isKeyedByItsTrip_notByTheMembersUid() {
        val written = budget("london", updatedAt = 100)

        // FirestoreCloudStore reads trips/london/memberData/alice: the document id is "alice".
        val read = SyncDocuments.fromDocument(SyncKind.BUDGET, "alice", "london", SyncDocuments.toDocument(written))

        assertEquals(written, read)
    }

    /**
     * The exact case of 2026-10-09, with a migration that can't finish (as with the budget-key bug): signing out keeps
     * every account's data on the phone, the status says "Sync problem", and the trips are there again for Alice.
     */
    @Test
    fun aMigrationThatCanNotFinish_neverMakesSigningOutRemoveTrips() = runTest {
        givenAlicesPhoneAndCloud()
        fake.unreadableKinds = setOf(SyncKind.BUDGET)
        val sync = manager()
        sync.start()
        settle()
        assertEquals(SyncStatus.PROBLEM, sync.status.value)
        assertNull(sync.lastSyncedAt.value)

        // Alice signs out: nothing is pending, but the cloud copy was never confirmed, so she is asked first.
        assertEquals(SignOutResult.NOT_CONFIRMED, sync.signOut(force = false))
        assertEquals(alice, auth.currentUser.value)
        assertEquals(setOf("london", "rome"), tripsOf("alice"))

        // "Sign out anyway": her trips stay on the phone, hidden from Bob.
        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = true))
        auth.currentUser.value = bob
        settle()
        fake.addMember("london", "bob")
        assertFalse(sync.refresh())
        settle()
        assertEquals(SyncStatus.PROBLEM, sync.status.value)
        assertTrue(tripsOf("bob").isEmpty())
        assertEquals(SignOutResult.NOT_CONFIRMED, sync.signOut(force = false))
        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = true))

        // Alice is back: her trips never left. Once the cloud reads work, everything syncs and she can sign out.
        auth.currentUser.value = alice
        settle()
        assertEquals(setOf("london", "rome"), tripsOf("alice"))
        fake.unreadableKinds = emptySet()
        assertTrue(sync.refresh())
        settle()
        assertEquals(SyncStatus.SAVED, sync.status.value)
        assertEquals(setOf("london", "rome"), tripsOf("alice"))
        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = false))
        assertTrue(phone.rowsOf("alice").isEmpty())
    }

    private fun tripsOf(uid: String) = phone.liveOf(uid).values.filter { it.kind == SyncKind.TRIP }.map { it.uuid }.toSet()
}
