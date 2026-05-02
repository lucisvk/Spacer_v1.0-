package com.example.spacer.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class AvailabilityConflictModelsTest {

    @Test
    fun inviteConflictInputs_preservesProvidedRanges() {
        val eventStart = Instant.parse("2026-04-27T18:00:00Z")
        val eventEnd = Instant.parse("2026-04-27T20:00:00Z")
        val venue = VenueOpenHoursWindow(
            dayOfWeek = 1,
            opensAt = LocalTime.of(10, 0),
            closesAt = LocalTime.of(22, 0),
            isClosed = false
        )
        val availability = UserAvailabilityWindow(
            dayOfWeek = 1,
            startsAt = LocalTime.of(17, 0),
            endsAt = LocalTime.of(21, 0)
        )
        val busy = InstantRange(
            startsAt = Instant.parse("2026-04-27T18:30:00Z"),
            endsAt = Instant.parse("2026-04-27T19:00:00Z")
        )

        val inputs = InviteConflictInputs(
            eventStart = eventStart,
            eventEnd = eventEnd,
            venueHours = listOf(venue),
            inviteeAvailability = listOf(availability),
            calendarBusyRanges = listOf(busy)
        )

        assertEquals(eventStart, inputs.eventStart)
        assertEquals(eventEnd, inputs.eventEnd)
        assertEquals(1, inputs.venueHours.size)
        assertEquals(1, inputs.inviteeAvailability.size)
        assertEquals(1, inputs.calendarBusyRanges.size)
    }

    @Test
    fun inviteConflictResult_tracksConflictTypeAndReason() {
        val result = InviteConflictResult(
            userId = "user-1",
            conflictType = ConflictType.CALENDAR_BUSY,
            reason = "Calendar busy from 6:30 PM to 7:00 PM"
        )

        assertEquals("user-1", result.userId)
        assertEquals(ConflictType.CALENDAR_BUSY, result.conflictType)
        assertTrue(result.reason.contains("Calendar busy"))
    }

    @Test
    fun venueOpenHoursWindow_defaultOpenState_isNotClosed() {
        val venue = VenueOpenHoursWindow(
            dayOfWeek = 2,
            opensAt = LocalTime.of(9, 0),
            closesAt = LocalTime.of(17, 0)
        )

        assertFalse(venue.isClosed)
    }
}
