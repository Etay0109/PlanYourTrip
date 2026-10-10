package com.example.planyourtrip.ui.tripdetails.members

import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.people
import com.example.planyourtrip.ui.common.split.personOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The "Traveling together" card's members and how member keys show up in the Budget tab. */
class MembersStateTest {

    private val trip = Trip(
        1, "Rome", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), TripColor.SKY,
        ownerId = "alice", memberIds = listOf("alice", "noa", "me"), uuid = "t1",
    )
    private val members = listOf(
        TripMember("alice", "Alice", joinedAt = 0, left = false),
        TripMember("noa", "Noa", joinedAt = 1_760_000_000_000, left = false),
        TripMember("me", "Me", joinedAt = 1_760_100_000_000, left = false),
        TripMember("dan", "Dan", joinedAt = 5, left = true),
    )
    private val travelers = listOf(Traveler(1, 1, "r1", "Amit", TravelerColor.SKY))

    @Test
    fun youFirst_thenTheOthersInJoinOrder_withTheOwnerMarked() {
        val state = membersState("me", trip, members, ZoneOffset.UTC)

        assertEquals(listOf("me", "alice", "noa"), state.members.map { it.uid })
        assertTrue(state.members[0].person is Person.You)
        assertEquals(listOf(false, true, false), state.members.map { it.isOwner })
        assertFalse(state.isOwner)
        assertEquals(3, state.count)
        assertEquals(LocalDate.of(2025, 10, 9), state.members[2].joinedOn)
    }

    @Test
    fun aTripNobodyJoined_isJustYou_asTheOwner() {
        val solo = trip.copy(ownerId = "me", memberIds = listOf("me"))
        val state = membersState("me", solo, emptyList(), ZoneOffset.UTC)
        assertEquals(1, state.count)
        assertTrue(state.isOwner)
        assertTrue(state.members.single().isOwner)
        // Before the first sync (no members known yet) it is the same.
        assertEquals(state, membersState("me", solo.copy(memberIds = emptyList(), ownerId = ""), emptyList(), ZoneOffset.UTC))
    }

    @Test
    fun tenMembers_isFull() {
        val ten = trip.copy(memberIds = listOf("me") + List(9) { "m$it" }, ownerId = "me")
        assertTrue(membersState("me", ten, emptyList(), ZoneOffset.UTC).isFull)
    }

    @Test
    fun membersArePeopleInTheBudgetTab_afterYou_beforeTheTravelers_notRenamable() {
        val list = people("me", travelers, trip.memberIds, members)
        assertEquals(listOf("me", "alice", "noa", "r1"), list.map { it.key })
        val noa = list[2] as Person.Member
        assertEquals("Noa", noa.name)
        // Colors follow the place in memberIds, the same on every device.
        assertEquals(TravelerColor.forIndex(0), noa.color)
        assertTrue((list[1] as Person.Member).isOwner)
        assertTrue(list[3] is Person.Named)
    }

    @Test
    fun aMemberWhoLeft_stillHasTheirNameOnOldExpenses_andAnUnknownKeyIsRemoved() {
        val dan = personOf("dan", "me", travelers, trip.memberIds, members) as Person.Member
        assertEquals("Dan", dan.name)
        assertTrue(dan.left)
        assertTrue(personOf("ghost", "me", travelers, trip.memberIds, members) is Person.Removed)
        // A member whose document didn't arrive yet: no name (shown as "Trip member").
        assertEquals(null, (personOf("x", "me", travelers, trip.memberIds + "x", members) as Person.Member).name)
    }
}
