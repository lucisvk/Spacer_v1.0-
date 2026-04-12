package com.example.spacer.events

import java.time.Instant
import java.time.LocalTime

/**
 * Input bundle for future invite-time conflict checks.
 *
 * Hosts will compare event timing against each invitee's availability windows and
 * venue open hours to classify conflicts before invites are sent.
 */
data class InviteConflictInputs(
    val eventStart: Instant,
    val eventEnd: Instant,
    val venueHours: List<VenueOpenHoursWindow>,
    val inviteeAvailability: List<UserAvailabilityWindow>,
    val calendarBusyRanges: List<InstantRange>
)

data class VenueOpenHoursWindow(
    val dayOfWeek: Int,
    val opensAt: LocalTime,
    val closesAt: LocalTime,
    val isClosed: Boolean = false
)

data class UserAvailabilityWindow(
    val dayOfWeek: Int,
    val startsAt: LocalTime,
    val endsAt: LocalTime
)

data class InstantRange(
    val startsAt: Instant,
    val endsAt: Instant
)

enum class ConflictType {
    NONE,
    OUTSIDE_VENUE_HOURS,
    OUTSIDE_USER_AVAILABILITY,
    CALENDAR_BUSY
}

data class InviteConflictResult(
    val userId: String,
    val conflictType: ConflictType,
    val reason: String
)
