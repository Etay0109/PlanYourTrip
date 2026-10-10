package com.example.planyourtrip.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules that decide which local trips an account lost ([tripsToCheck], [decideAccess]). */
class TripAccessTest {

    private val owners = mapOf("mine" to "me", "theirs" to "alice", "unknown" to "")

    @Test
    fun onlyTripsTheListDidntReturnAlive_areReadDirectly() {
        val listed = mapOf("mine" to false, "theirs" to false)
        assertEquals(setOf("unknown"), tripsToCheck("me", owners, listed))

        // A trip of someone else returned as deleted is read too; my own deleted trip isn't.
        val deleted = mapOf("mine" to true, "theirs" to true, "unknown" to false)
        assertEquals(setOf("theirs"), tripsToCheck("me", owners, deleted))
    }

    @Test
    fun aTripIDontOwn_isLostOnlyWhenTheListMissesItAndTheReadConfirms() {
        val notListed = emptyMap<String, Boolean>()
        for (read in listOf(TripAccess.NO_ACCESS, TripAccess.MISSING, TripAccess.DELETED)) {
            assertEquals(setOf("theirs"), decideAccess("me", owners, notListed, mapOf("theirs" to read)).lost)
        }
        // Readable after all: kept.
        assertTrue(decideAccess("me", owners, notListed, mapOf("theirs" to TripAccess.LIVE)).lost.isEmpty())
        // Listed alive: kept, whatever the direct read says (rules gone wrong).
        assertTrue(
            decideAccess("me", owners, mapOf("theirs" to false), mapOf("theirs" to TripAccess.NO_ACCESS)).lost.isEmpty()
        )
        // No direct read: kept.
        assertTrue(decideAccess("me", owners, notListed, emptyMap()).lost.isEmpty())
    }

    @Test
    fun deletedByItsOwner_isLost() {
        val decision = decideAccess("me", owners, mapOf("theirs" to true), mapOf("theirs" to TripAccess.DELETED))
        assertEquals(setOf("theirs"), decision.lost)
    }

    @Test
    fun myOwnTrip_isNeverLost_andADeniedReadIsAnError() {
        val reads = mapOf("mine" to TripAccess.NO_ACCESS, "unknown" to TripAccess.NO_ACCESS)
        val decision = decideAccess("me", owners, emptyMap(), reads)
        assertTrue(decision.lost.isEmpty())
        assertEquals(setOf("mine", "unknown"), decision.ownTripsDenied)

        val missing = decideAccess("me", owners, emptyMap(), mapOf("mine" to TripAccess.MISSING))
        assertTrue(missing.lost.isEmpty())
        assertTrue(missing.ownTripsDenied.isEmpty())
    }
}
