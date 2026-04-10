package com.example.spacer.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Read-only scan of on-device calendar instances (Google, Exchange, etc.) for busy overlap.
 *
 * **Does not call Google Cloud / Calendar API** — uses [android.provider.CalendarContract] via
 * [android.content.ContentResolver] only. No Places or Maps billing.
 */
object DeviceCalendarBusyChecker {

    fun hasReadCalendarPermission(context: Context): Boolean =
        PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PermissionChecker.PERMISSION_GRANTED

    /**
     * Returns true if any calendar instance in [[eventStartMillis], [eventEndMillis]) is not marked FREE.
     * Requires [Manifest.permission.READ_CALENDAR].
     */
    fun eventOverlapsBusyTime(
        context: Context,
        eventStartMillis: Long,
        eventEndMillis: Long
    ): Boolean {
        if (!hasReadCalendarPermission(context)) return false
        if (eventEndMillis <= eventStartMillis) return false

        val cr = context.contentResolver
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, eventStartMillis)
        ContentUris.appendId(builder, eventEndMillis)
        val uri: Uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.AVAILABILITY
        )

        val cursor: Cursor? = cr.query(uri, projection, null, null, null)
        cursor?.use { c ->
            val idxBegin = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val idxEnd = c.getColumnIndex(CalendarContract.Instances.END)
            val idxAvail = c.getColumnIndex(CalendarContract.Instances.AVAILABILITY)
            while (c.moveToNext()) {
                val begin = if (idxBegin >= 0) c.getLong(idxBegin) else continue
                val end = if (idxEnd >= 0) c.getLong(idxEnd) else continue
                val availability = if (idxAvail >= 0) c.getInt(idxAvail) else CalendarContract.Events.AVAILABILITY_BUSY
                if (availability == CalendarContract.Events.AVAILABILITY_FREE) continue
                // Busy / tentative / unknown: treat as potential conflict.
                if (begin < eventEndMillis && end > eventStartMillis) return true
            }
        }
        return false
    }

    fun eventWindowMillis(startsAtIso: String, endsAtIso: String?): Pair<Long, Long>? {
        val start = parseStartMillis(startsAtIso) ?: return null
        val end = parseEndMillis(startsAtIso, endsAtIso) ?: (start + 60 * 60 * 1000)
        return start to end
    }

    private fun parseStartMillis(startsAt: String): Long? = try {
        OffsetDateTime.parse(startsAt)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseEndMillis(startsAt: String, endsAt: String?): Long? {
        val end = endsAt?.takeIf { it.isNotBlank() } ?: return null
        return try {
            OffsetDateTime.parse(end)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** A single calendar account/source the user can pick to "connect" to Spacer. */
    data class CalendarAccount(
        /** Stable id used in `calendar_provider`. */
        val id: String,
        /** Account display name shown in the picker, e.g. `you@gmail.com`. */
        val accountName: String,
        /** Calendar display name within that account, e.g. "Personal". */
        val displayName: String,
        /** Account type (e.g. "com.google", "com.samsung.android.exchange"). */
        val accountType: String
    )

    /**
     * List the calendar accounts known to the device. Returns empty when the
     * user has not granted [Manifest.permission.READ_CALENDAR] yet.
     */
    fun listDeviceCalendarAccounts(context: Context): List<CalendarAccount> {
        if (!hasReadCalendarPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.VISIBLE
        )
        val out = mutableListOf<CalendarAccount>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
            )?.use { c ->
                val idxId = c.getColumnIndex(CalendarContract.Calendars._ID)
                val idxAccountName = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val idxAccountType = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val idxDisplay = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = if (idxId >= 0) c.getLong(idxId).toString() else continue
                    val accountName = if (idxAccountName >= 0) c.getString(idxAccountName).orEmpty() else ""
                    val accountType = if (idxAccountType >= 0) c.getString(idxAccountType).orEmpty() else ""
                    val displayName = if (idxDisplay >= 0) c.getString(idxDisplay).orEmpty() else ""
                    out += CalendarAccount(
                        id = id,
                        accountName = accountName,
                        displayName = displayName,
                        accountType = accountType
                    )
                }
            }
        }
        return out
    }
}
