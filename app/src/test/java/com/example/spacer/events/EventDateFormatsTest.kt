package com.example.spacer.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDateFormatsTest {

    @Test
    fun formatEventDateNoTime_formatsValidIso() {
        val formatted = formatEventDateNoTime("2026-04-26T18:30:00Z")
        assertTrue("Expected month-day-year output", formatted.contains(", 2026"))
    }

    @Test
    fun formatEventDateNoTime_returnsInputOnInvalidValue() {
        val raw = "not-a-date"
        assertEquals(raw, formatEventDateNoTime(raw))
    }

    @Test
    fun formatEventDateTime_formatsValidIso() {
        val formatted = formatEventDateTime("2026-04-26T18:30:00Z")
        assertTrue("Expected year in formatted date-time", formatted.contains("2026"))
        assertTrue("Expected 12-hour clock suffix", formatted.contains("AM") || formatted.contains("PM"))
    }

    @Test
    fun formatEventTimeRange_withEndTime_returnsRange() {
        val formatted = formatEventTimeRange(
            startsAt = "2026-04-26T18:30:00Z",
            endsAt = "2026-04-26T20:00:00Z"
        )
        assertTrue("Expected formatted range separator", formatted.contains(" - "))
    }

    @Test
    fun formatEventTimeRange_withoutEndTime_returnsStartOnly() {
        val formatted = formatEventTimeRange(
            startsAt = "2026-04-26T18:30:00Z",
            endsAt = null
        )
        assertTrue("Expected 12-hour clock suffix", formatted.contains("AM") || formatted.contains("PM"))
    }
}
