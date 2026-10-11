package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trip's place (country code + coordinates) in the trip document: written always, read leniently. */
class TripPlaceSyncTest {

    private val london = trip("t1", updatedAt = 3).let {
        it.copy(data = (it.data as ItemData.TripData).copy(countryCode = "GB", latitude = 51.5, longitude = -0.13))
    }

    private fun read(doc: Map<String, Any?>) = SyncDocuments.fromDocument(SyncKind.TRIP, "t1", null, doc)

    private fun tripData(doc: Map<String, Any?>) = read(doc)?.data as ItemData.TripData

    @Test
    fun thePlace_isWrittenAsACodeAndTwoNumbers() {
        val doc = SyncDocuments.toDocument(london)
        assertEquals("GB", doc["countryCode"])
        assertEquals(51.5, doc["latitude"])
        assertEquals(-0.13, doc["longitude"])
    }

    @Test
    fun noPlace_writesEveryKeyAsNull() {
        val doc = SyncDocuments.toDocument(trip("t1", updatedAt = 3))
        for (key in listOf("countryCode", "latitude", "longitude")) {
            assertTrue(key, doc.containsKey(key))
            assertNull(key, doc[key])
        }
    }

    @Test
    fun anUploadOfAnExistingTrip_mergesThePlace_andATombstoneRemovesIt() {
        assertTrue(SyncDocuments.TRIP_DATA_FIELDS.containsAll(listOf("countryCode", "latitude", "longitude")))
        assertFalse(SyncDocuments.TRIP_DATA_FIELDS.contains(SyncDocuments.OWNER_ID))
    }

    @Test
    fun aTripDocumentFromBeforeSuggestions_hasNoPlace() {
        val old = SyncDocuments.toDocument(trip("t1", updatedAt = 3)) - listOf("countryCode", "latitude", "longitude")
        val data = tripData(old)
        assertEquals("Rome", data.destination)
        assertNull(data.countryCode)
        assertNull(data.latitude)
        assertNull(data.longitude)
    }

    @Test
    fun wholeNumberCoordinates_areReadAsDoubles() {
        val doc = SyncDocuments.toDocument(london) + mapOf("latitude" to 0L, "longitude" to 12L)
        val data = tripData(doc)
        assertEquals(0.0, data.latitude!!, 0.0)
        assertEquals(12.0, data.longitude!!, 0.0)
    }

    @Test
    fun badValues_areDropped_neverTheTrip() {
        val base = SyncDocuments.toDocument(london)
        val cases = listOf(
            mapOf("countryCode" to "GBR"), mapOf("countryCode" to 44L),
            mapOf("latitude" to 91.0), mapOf("longitude" to -181.0),
            mapOf("latitude" to "51.5"), mapOf("longitude" to null),
        )
        for (change in cases) {
            val read = read(base + change)
            assertNotNull("$change dropped the trip", read)
            val data = read!!.data as ItemData.TripData
            if ("countryCode" in change) {
                assertNull("$change", data.countryCode)
                assertEquals(51.5, data.latitude!!, 0.0)
            } else {
                assertEquals("GB", data.countryCode)
                assertNull("$change", data.latitude)
                assertNull("$change", data.longitude)
            }
        }
    }

    @Test
    fun aLowercaseCode_isReadUppercase() {
        assertEquals("GB", tripData(SyncDocuments.toDocument(london) + ("countryCode" to "gb")).countryCode)
    }
}
