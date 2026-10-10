package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.checklistItem
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.renamed
import com.example.planyourtrip.data.sync.SyncTestData.trip
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.TripMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared trips on a member's phone: losing access (left, removed, deleted by the owner), never removing a trip by
 * mistake, leaving, removing a member and the member documents. Alice owns trip t1; Bob joined it. Each account has
 * its own phone (local store and manager); both use the same fake cloud. Never the real Firestore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncAccessTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")
    private val bob = AuthUser(uid = "bob", displayName = "Bob", email = "bob@example.com")

    private val cloud = FakeCloudStore()
    private val network = FakeNetworkMonitor(online = true)

    private val alicePhone = FakeSyncLocalStore()
    private val bobPhone = FakeSyncLocalStore()

    /** Trips whose documents were removed because the account lost them: (uid, trip uuid). */
    private val forgotten = mutableListOf<Pair<String, String>>()

    private val rome = trip("t1", updatedAt = 100)
    private val dinner = expense("e1", "t1", updatedAt = 100)

    private fun TestScope.manager(user: AuthUser, local: FakeSyncLocalStore) = SyncManager(
        authRepository = FakeAuthRepository(user),
        cloud = cloud,
        local = local,
        network = network,
        flags = FakeSyncFlags(),
        tripCleanup = object : TripCleanup {
            override suspend fun afterFullSync(ownerId: String) = Unit
            override suspend fun forgetTrip(ownerId: String, tripUuid: String) {
                forgotten += ownerId to tripUuid
            }
        },
        scope = backgroundScope,
    )

    private fun packing(uuid: String, tripUuid: String, updatedAt: Long) =
        checklistItem(uuid, tripUuid, updatedAt, text = "Charger").let {
            it.copy(kind = SyncKind.PACKING, data = (it.data as ItemData.ChecklistData).copy(list = ChecklistKind.PACKING))
        }

    /** Alice's trip with an expense in the cloud; Bob joined and synced it, and has his own budget and Packing item. */
    private suspend fun TestScope.sharedTrip(): Pair<SyncManager, SyncManager> {
        listOf(rome, dinner).forEach { alicePhone.edit("alice", it) }
        val aliceSync = manager(alice, alicePhone)
        assertTrue(aliceSync.sync("alice", full = true))
        cloud.addMember("t1", "bob")
        val bobSync = manager(bob, bobPhone)
        assertTrue(bobSync.sync("bob", full = true))
        listOf(budget("t1", updatedAt = 150), packing("p1", "t1", updatedAt = 150)).forEach { bobPhone.edit("bob", it) }
        assertTrue(bobSync.sync("bob", full = true))
        assertEquals(setOf("t1", "e1", "budget:t1", "p1"), bobPhone.liveOf("bob").keys)
        assertEquals(2, cloud.personalOf("bob", "t1").size)
        return aliceSync to bobSync
    }

    // ---------- Losing access ----------

    @Test
    fun removedMember_losesTheTripOnTheNextSync_withPendingChangesAndDocuments() = runTest {
        val (_, bobSync) = sharedTrip()
        // Bob edits offline-ish: the change is still pending when the owner removes him.
        bobPhone.edit("bob", expense("e2", "t1", updatedAt = 300, paidBy = "bob"))
        cloud.removeFromTrip("t1", "bob")

        assertTrue(bobSync.sync("bob", full = true))

        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertEquals(listOf("bob" to "t1"), forgotten)
        // His own budget and Packing list are deleted in the cloud; his pending expense never reached it.
        assertTrue(cloud.personalOf("bob", "t1").isEmpty())
        assertFalse("e2" in cloud.docsOf("alice"))
        assertEquals(setOf("t1", "e1"), cloud.docsOf("alice").keys - "budget:t1")
    }

    @Test
    fun memberWhoLeftOnAnotherPhone_losesTheTripHereToo() = runTest {
        val (_, bobSync) = sharedTrip()
        cloud.leaveTrip("bob", "t1")

        assertTrue(bobSync.sync("bob", full = true))

        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertEquals(listOf("alice"), cloud.membersOf("t1"))
    }

    @Test
    fun ownerDeletedTheTrip_memberWithANewerEdit_neverBringsItBack() = runTest {
        val (aliceSync, bobSync) = sharedTrip()
        alicePhone.delete("alice", rome, at = 300)
        assertTrue(aliceSync.sync("alice", full = true))
        // Bob renamed the trip later than the delete, before syncing.
        bobPhone.edit("bob", rome.renamed("Rome and Naples", at = 400))

        assertTrue(bobSync.sync("bob", full = true))

        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertTrue(cloud.docsOf("alice").getValue("t1").isDeleted)
        assertTrue(cloud.personalOf("bob", "t1").isEmpty())
        assertEquals(listOf("bob" to "t1"), forgotten)
    }

    @Test
    fun aTripSomeoneElseOwnsThatIsGoneFromTheCloud_isRemoved_neverCreatedAgainAsMine() = runTest {
        // On Bob's phone, Alice's trip with a pending edit, but the trip document no longer exists.
        bobPhone.edit("bob", rome.renamed("Rome", at = 200))

        assertTrue(manager(bob, bobPhone).sync("bob", full = true))

        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertTrue(cloud.docsOf("bob").isEmpty())
    }

    // ---------- Never removing a trip by mistake ----------

    @Test
    fun everyTripReadDenied_byBrokenRules_removesNothing() = runTest {
        val (_, bobSync) = sharedTrip()
        bobPhone.edit("bob", expense("e2", "t1", updatedAt = 300, paidBy = "bob"))
        cloud.denyTripReads = true

        bobSync.sync("bob", full = true)

        // The list query still returns the trip, so it is never treated as lost.
        assertTrue("t1" in bobPhone.liveOf("bob"))
        assertTrue("e2" in bobPhone.liveOf("bob"))
        assertTrue(forgotten.isEmpty())
        assertTrue(cloud.deletedPersonal.isEmpty())
    }

    @Test
    fun myOwnTripWithoutAccess_failsTheSync_andKeepsEverything() = runTest {
        val (aliceSync, _) = sharedTrip()
        alicePhone.edit("alice", expense("e3", "t1", updatedAt = 300))
        // Something went wrong in the cloud: the owner isn't a member of her own trip any more.
        cloud.removeFromTrip("t1", "alice")

        assertFalse(aliceSync.sync("alice", full = true))

        assertTrue("t1" in alicePhone.liveOf("alice"))
        assertTrue(alicePhone.rowsOf("alice").single { it.item.uuid == "e3" }.pending)
        assertTrue(forgotten.isEmpty())
        assertTrue(cloud.deletedPersonal.isEmpty())
    }

    @Test
    fun aFailedListQuery_removesNothing() = runTest {
        val (_, bobSync) = sharedTrip()
        cloud.removeFromTrip("t1", "bob")
        cloud.failList = true

        assertFalse(bobSync.sync("bob", full = true))

        assertTrue("t1" in bobPhone.liveOf("bob"))
        assertTrue(forgotten.isEmpty())
        assertTrue(cloud.deletedPersonal.isEmpty())
        assertEquals(2, cloud.personalOf("bob", "t1").size)
    }

    @Test
    fun personalDataDeleteFailing_keepsEverything_andTheNextSyncFinishes() = runTest {
        val (_, bobSync) = sharedTrip()
        cloud.removeFromTrip("t1", "bob")
        cloud.failDeletePersonal = true

        assertFalse(bobSync.sync("bob", full = true))
        assertTrue("t1" in bobPhone.liveOf("bob"))
        assertTrue(forgotten.isEmpty())

        cloud.failDeletePersonal = false
        assertTrue(bobSync.sync("bob", full = true))
        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertTrue(cloud.personalOf("bob", "t1").isEmpty())
    }

    @Test
    fun myOwnNewTrip_notInTheCloudYet_stillUploads() = runTest {
        alicePhone.edit("alice", trip("t2", updatedAt = 50, destination = "Oslo"))

        assertTrue(manager(alice, alicePhone).sync("alice", full = true))

        assertEquals("Oslo", (cloud.docsOf("alice").getValue("t2").data as ItemData.TripData).destination)
        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun quickSync_ofATripICantRead_keepsItsChangesPending_andAsksForTheFullSyncThatDecides() = runTest {
        listOf(rome, dinner).forEach { alicePhone.edit("alice", it) }
        assertTrue(manager(alice, alicePhone).sync("alice", full = true))
        cloud.addMember("t1", "bob")
        val bobSync = manager(bob, bobPhone)
        // Running as in the app: the start's full sync brings the trip to Bob's phone.
        bobSync.start()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue("t1" in bobPhone.liveOf("bob"))

        bobPhone.edit("bob", expense("e2", "t1", updatedAt = 300, paidBy = "bob"))
        cloud.removeFromTrip("t1", "bob")

        // The quick sync alone removes nothing and uploads nothing of that trip.
        assertTrue(bobSync.sync("bob", full = false))
        assertTrue(bobPhone.rowsOf("bob").single { it.item.uuid == "e2" }.pending)
        assertTrue(forgotten.isEmpty())
        assertFalse("e2" in cloud.docsOf("alice"))

        // ...but it asked for a full sync, which finds the trip lost.
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertEquals(listOf("bob" to "t1"), forgotten)
        assertFalse("e2" in cloud.docsOf("alice"))
    }

    // ---------- Leaving and removing ----------

    @Test
    fun leaving_takesMeOutInTheCloud_andRemovesTheTripHere() = runTest {
        val (_, bobSync) = sharedTrip()
        bobPhone.edit("bob", expense("e2", "t1", updatedAt = 300, paidBy = "bob"))

        assertTrue(bobSync.leaveTrip("t1"))

        assertEquals(listOf("alice"), cloud.membersOf("t1"))
        assertTrue(cloud.personalOf("bob", "t1").isEmpty())
        assertTrue(bobPhone.rowsOf("bob").isEmpty())
        assertEquals(listOf("bob" to "t1"), forgotten)
    }

    @Test
    fun leaving_offline_orAsTheOwner_changesNothing() = runTest {
        val (aliceSync, bobSync) = sharedTrip()

        assertFalse(aliceSync.leaveTrip("t1"))
        network.isOnline.value = false
        assertFalse(bobSync.leaveTrip("t1"))

        assertEquals(listOf("alice", "bob"), cloud.membersOf("t1"))
        assertTrue("t1" in bobPhone.liveOf("bob"))
        assertTrue("t1" in alicePhone.liveOf("alice"))
        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun removingAMember_updatesTheCloud_andMyMemberList() = runTest {
        val (aliceSync, bobSync) = sharedTrip()

        assertTrue(aliceSync.removeMember("t1", "bob"))

        assertEquals(listOf("alice"), cloud.membersOf("t1"))
        assertEquals(listOf("alice"), (alicePhone.liveOf("alice").getValue("t1").data as ItemData.TripData).memberIds)
        // A member can't remove anyone, and the owner can't remove herself.
        assertFalse(bobSync.removeMember("t1", "alice"))
        assertFalse(aliceSync.removeMember("t1", "alice"))
    }

    // ---------- Member documents ----------

    @Test
    fun fullSync_downloadsTheMemberDocuments() = runTest {
        val (aliceSync, _) = sharedTrip()
        cloud.memberDocs["t1"] = mutableMapOf(
            "alice" to TripMember("alice", "Alice", joinedAt = 10, left = false),
            "bob" to TripMember("bob", "Bob", joinedAt = 20, left = false),
        )

        assertTrue(aliceSync.sync("alice", full = true))

        assertEquals(setOf("Alice", "Bob"), alicePhone.members.getValue("alice" to "t1").map { it.name }.toSet())
    }

    @Test
    fun aDeletedTripsTombstone_isntReadForMembers() = runTest {
        val (aliceSync, _) = sharedTrip()
        alicePhone.delete("alice", rome, at = 300)
        assertTrue(aliceSync.sync("alice", full = true))
        cloud.calls.clear()

        assertTrue(aliceSync.sync("alice", full = true))

        assertFalse(cloud.calls.any { it.startsWith("fetchMembers") })
        assertTrue(cloud.docsOf("alice").getValue("t1").isDeleted)
    }
}
