package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A legacy budget carries its legacy trip's `updatedAt`, not the time the budget was set, so the migration copies it
 * only when the new layout has no budget document of that trip (never over one, whatever the times say).
 */
class LegacyBudgetTest {

    private val legacy = convertLegacy(listOf(trip("t1", updatedAt = 100), budget("t1", updatedAt = 100)), uid = "alice")
    private val legacyTrip: SyncItem = legacy.first { it.kind == SyncKind.TRIP }

    @Test
    fun noBudgetInTheNewLayout_theLegacyOneIsCopied() {
        assertEquals(listOf(SyncKind.BUDGET), planLegacyCopy(legacy, current = listOf(legacyTrip)).map { it.kind })
    }

    @Test
    fun anOlderBudgetInTheNewLayout_isNeverReplacedByTheLegacyOne() {
        val setOnAnotherPhone = budget("t1", updatedAt = 50, budgetAgorot = 900_000)

        assertTrue(planLegacyCopy(legacy, current = listOf(legacyTrip, setOnAnotherPhone)).isEmpty())
        assertTrue(isLegacyCopyComplete(legacy, current = listOf(legacyTrip, setOnAnotherPhone)))
    }

    @Test
    fun aBudgetClearedInTheNewLayout_staysCleared() {
        val cleared = budget("t1", updatedAt = 50).deletedAt(60)

        assertTrue(planLegacyCopy(legacy, current = listOf(legacyTrip, cleared)).isEmpty())
    }
}
