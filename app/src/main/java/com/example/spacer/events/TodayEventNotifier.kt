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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class TodayEventNotifier(
    private val context: Context,
    private val repository: EventRepository = EventRepository()
) {
    suspend fun notifyHostedEventsForToday() {
        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences("event_notifications", Context.MODE_PRIVATE)
        if (prefs.getString("last_notified_day", null) == today) return

        val eventsToday = repository.listUpcomingHostedEvents().getOrDefault(emptyList()).filter {
            parseDate(it.startsAt)?.toLocalDate() == LocalDate.now()
        }
        if (eventsToday.isEmpty()) return

        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED
        ) return

        val title = if (eventsToday.size == 1) "You host an event today" else "You host ${eventsToday.size} events today"
        val body = eventsToday.take(2).joinToString(" • ") { it.title }
        NotificationManagerCompat.from(context).notify(
            1200,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.spacer_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
        prefs.edit().putString("last_notified_day", today).apply()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Event reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Same-day reminders for events you host" }
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    private fun parseDate(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        const val CHANNEL_ID = "event_day_reminders"
    }
}
