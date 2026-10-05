package com.example.planyourtrip.data.currency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RatePolicyTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun noCachedRate_isStale() = assertTrue(isStale(null, now))

    @Test
    fun youngerThan12Hours_isFresh() =
        assertFalse(isStale(now - Duration.ofHours(11).plusMinutes(59), now))

    @Test
    fun exactly12Hours_orOlder_isStale() {
        assertTrue(isStale(now - Duration.ofHours(12), now))
        assertTrue(isStale(now - Duration.ofDays(3), now))
    }
}
