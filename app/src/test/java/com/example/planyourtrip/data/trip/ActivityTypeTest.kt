package com.example.planyourtrip.data.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTypeTest {

    @Test
    fun storedNamesNeverChange() {
        // Room and Firestore store the names; "Attraction" is only a label.
        assertEquals(
            setOf("ACTIVITY", "DINING", "SHOPPING", "LODGING", "TRANSPORT", "OTHER"),
            ActivityType.entries.map { it.name }.toSet(),
        )
    }
}
