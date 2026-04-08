package com.example.spacer.events

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val dateOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val dateTimeFormatter12h: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
private val timeFormatter12h: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatEventDateNoTime(isoStartsAt: String): String {
    return try {
        val odt = OffsetDateTime.parse(isoStartsAt)
        odt.format(dateOnlyFormatter)
    } catch (_: DateTimeParseException) {
        isoStartsAt
    } catch (_: Throwable) {
        isoStartsAt
    }
}

fun formatEventDateTime(isoDateTime: String): String {
    return try {
        val odt = OffsetDateTime.parse(isoDateTime)
        odt.format(dateTimeFormatter12h)
    } catch (_: DateTimeParseException) {
        isoDateTime
    } catch (_: Throwable) {
        isoDateTime
    }
}

fun formatEventTimeRange(startsAt: String, endsAt: String?): String {
    return try {
        val start = OffsetDateTime.parse(startsAt)
        val startText = start.format(timeFormatter12h)
        val endText = endsAt
            ?.takeIf { it.isNotBlank() }
            ?.let { OffsetDateTime.parse(it).format(timeFormatter12h) }
        if (endText.isNullOrBlank()) startText else "$startText - $endText"
    } catch (_: DateTimeParseException) {
        startsAt
    } catch (_: Throwable) {
        startsAt
    }
}
