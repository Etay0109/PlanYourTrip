package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.trip
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.TripColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class SyncDocumentsTest {

    private fun roundTrip(item: SyncItem) =
        SyncDocuments.fromDocument(item.kind, item.uuid, item.tripUuid, SyncDocuments.toDocument(item))

    @Test
    fun trip_isStoredWithIsoDatesAgorotAndColorName() {
        val doc = SyncDocuments.toDocument(trip("t1", updatedAt = 1_700_000_000_000))

        assertEquals("2026-11-12", doc["startDate"])
        assertEquals("2026-11-17", doc["endDate"])
        assertEquals(600_000L, doc["budgetAgorot"])
        assertEquals("EUR", doc["currencyCode"])
        assertEquals("SKY", doc["color"])
        assertEquals(1_700_000_000_000L, doc["updatedAt"])
        assertEquals(false, doc["deleted"])
        assertEquals(trip("t1", updatedAt = 1_700_000_000_000), roundTrip(trip("t1", updatedAt = 1_700_000_000_000)))
    }

    @Test
    fun foreignExpense_keepsExactDecimalText_andRoundTrips() {
        val item = expense("e1", "t1", updatedAt = 5).copy(
            data = ItemData.ExpenseData(
                "Museum", 4_020, ExpenseCategory.ATTRACTIONS, LocalDate.of(2026, 11, 14),
                ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.0200"), LocalDate.of(2026, 10, 2)),
            )
        )
        val doc = SyncDocuments.toDocument(item)

        assertEquals("10.00", doc["originalAmount"])
        assertEquals("4.0200", doc["exchangeRate"])
        assertEquals("2026-10-02", doc["rateDate"])
        assertEquals(item, roundTrip(item))
    }

    @Test
    fun shekelExpense_hasAllForeignKeysNull() {
        val doc = SyncDocuments.toDocument(expense("e1", "t1", updatedAt = 5))

        listOf("originalAmount", "originalCurrency", "exchangeRate", "rateDate").forEach {
            assertEquals(true, doc.containsKey(it))
            assertNull(doc[it])
        }
    }

    @Test
    fun partialForeignFields_readAsAShekelExpense() {
        val doc = SyncDocuments.toDocument(expense("e1", "t1", updatedAt = 5)) + mapOf("originalAmount" to "10")

        val data = SyncDocuments.fromDocument(SyncKind.EXPENSE, "e1", "t1", doc)?.data as ItemData.ExpenseData

        assertNull(data.original)
    }

    @Test
    fun activity_storesTimeAsHHmm_andRoundTrips() {
        val item = activity("a1", "t1", updatedAt = 9).copy(
            data = ItemData.ActivityData(
                ActivityInput(
                    LocalDate.of(2026, 11, 13), LocalTime.of(9, 5), "Vatican", ActivityType.ACTIVITY,
                    note = "Book ahead", location = "Vatican City", estimatedCostAgorot = 9_000, booked = true,
                )
            )
        )

        assertEquals("09:05", SyncDocuments.toDocument(item)["startTime"])
        assertEquals(item, roundTrip(item))
        assertEquals(activity("a1", "t1", updatedAt = 9), roundTrip(activity("a1", "t1", updatedAt = 9)))
    }

    @Test
    fun tombstone_hasOnlyDeletedAndUpdatedAt() {
        val tombstone = expense("e1", "t1", updatedAt = 5).deletedAt(10)

        assertEquals(mapOf("deleted" to true, "updatedAt" to 10L), SyncDocuments.toDocument(tombstone))
        assertEquals(tombstone, roundTrip(tombstone))
    }

    @Test
    fun unknownEnumNames_fallBack() {
        val trip = SyncDocuments.toDocument(trip("t1", 1)) + mapOf("color" to "NEON")
        val expense = SyncDocuments.toDocument(expense("e1", "t1", 1)) + mapOf("category" to "SPA")
        val activity = SyncDocuments.toDocument(activity("a1", "t1", 1)) + mapOf("type" to "PARTY")

        assertEquals(TripColor.entries.first(), (SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, trip)?.data as ItemData.TripData).color)
        assertEquals(ExpenseCategory.OTHER, (SyncDocuments.fromDocument(SyncKind.EXPENSE, "e1", "t1", expense)?.data as ItemData.ExpenseData).category)
        assertEquals(ActivityType.OTHER, (SyncDocuments.fromDocument(SyncKind.ACTIVITY, "a1", "t1", activity)?.data as ItemData.ActivityData).input.type)
    }

    @Test
    fun unreadableDocuments_areNull() {
        val valid = SyncDocuments.toDocument(trip("t1", 1))

        assertNull(SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, valid - "destination"))
        assertNull(SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, valid + mapOf("startDate" to "12/11/2026")))
        assertNull(SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, valid - "updatedAt"))
        assertNull(SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, emptyMap()))
    }
}
