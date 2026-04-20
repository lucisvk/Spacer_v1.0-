package com.example.spacer.events

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.spacer.R
import com.example.spacer.calendar.DeviceCalendarBusyChecker
import com.example.spacer.network.SessionPrefs
import java.time.LocalDate

class CalendarConflictNotifier(
    private val context: Context,
    private val repository: EventRepository = EventRepository()
) {
    suspend fun notifyPendingInviteCalendarConflicts() {
        val sessionPrefs = SessionPrefs(context)
        if (!sessionPrefs.isCalendarConflictNotificationsEnabled()) return
        if (!sessionPrefs.isDeviceCalendarReadEnabled()) return
        if (!DeviceCalendarBusyChecker.hasReadCalendarPermission(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PermissionChecker.PERMISSION_GRANTED
        ) {
            return
        }

        val pending = repository.listPendingInvites().getOrDefault(emptyList())
        if (pending.isEmpty()) return

        val notifyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val notifiedToday = notifyPrefs.getStringSet(KEY_NOTIFIED_KEYS, emptySet()) ?: emptySet()
        val mutableNotified = notifiedToday.toMutableSet()
        var changed = false

        ensureChannel()

        pending.forEach { inv ->
            val dayKey = "${inv.eventId}|$today"
            if (dayKey in mutableNotified) return@forEach

            val window = DeviceCalendarBusyChecker.eventWindowMillis(inv.startsAt, inv.endsAt) ?: return@forEach
            val overlaps = DeviceCalendarBusyChecker.eventOverlapsBusyTime(
                context,
                window.first,
                window.second
            )
            if (!overlaps) return@forEach

            val id = NOTIFICATION_ID_BASE + inv.eventId.hashCode().and(0xffff)
            NotificationManagerCompat.from(context).notify(
                id,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.spacer_logo)
                    .setContentTitle("Calendar conflict: ${inv.title}")
                    .setContentText("This invite overlaps something busy on your calendar.")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(
                                "“${inv.title}” may overlap another commitment on your device calendar. " +
                                    "Open the invite in Spacer to review the time."
                            )
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            )
            mutableNotified.add(dayKey)
            changed = true
        }

        if (changed) {
            // Trim old keys (other days) to avoid unbounded growth.
            val pruned = mutableNotified.filter { it.endsWith("|$today") }.toSet()
            notifyPrefs.edit().putStringSet(KEY_NOTIFIED_KEYS, pruned).apply()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Invite calendar conflicts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "When a pending invite overlaps busy time on your device calendar"
        }
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "calendar_invite_conflicts"
        const val PREFS_NAME = "calendar_conflict_notifications"
        const val KEY_NOTIFIED_KEYS = "notified_event_day_keys"
        const val NOTIFICATION_ID_BASE = 3400
    }
}
