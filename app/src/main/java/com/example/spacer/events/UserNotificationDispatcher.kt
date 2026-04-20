package com.example.spacer.events

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.spacer.R
import com.example.spacer.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val EVENTS_ALERTS_CHANNEL_ID = "events_social_alerts"

object UserNotificationDispatcher {
    data class DispatchDiagnostics(
        val unreadCount: Int,
        val deliveredCount: Int,
        val failureCount: Int,
        val firstError: String? = null
    )

    fun diagnosePostingReadiness(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "POST_NOTIFICATIONS permission is denied."
        }
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) {
            return "App notifications are disabled in system settings."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = mgr.getNotificationChannel(EVENTS_ALERTS_CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return "Notification channel \"Events and social alerts\" is blocked."
            }
        }
        return "ready"
    }

    suspend fun flushUnreadToPhone(context: Context, repository: NotificationsRepository): Int {
        val unread = withContext(Dispatchers.IO) { repository.listUnread() }.getOrDefault(emptyList())
        if (unread.isEmpty()) return 0
        val result = postUnreadPhoneNotifications(context, unread)
        val deliveredIds = result.deliveredIds
        if (deliveredIds.isEmpty()) return 0
        withContext(Dispatchers.IO) {
            deliveredIds.forEach { repository.markRead(it) }
        }
        return deliveredIds.size
    }

    suspend fun flushUnreadToPhoneWithDiagnostics(
        context: Context,
        repository: NotificationsRepository
    ): DispatchDiagnostics {
        val unread = withContext(Dispatchers.IO) { repository.listUnread() }.getOrDefault(emptyList())
        if (unread.isEmpty()) return DispatchDiagnostics(0, 0, 0, null)
        val result = postUnreadPhoneNotifications(context, unread)
        if (result.deliveredIds.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                result.deliveredIds.forEach { repository.markRead(it) }
            }
        }
        return DispatchDiagnostics(
            unreadCount = unread.size,
            deliveredCount = result.deliveredIds.size,
            failureCount = result.failures,
            firstError = result.firstError
        )
    }

    private data class PostResult(
        val deliveredIds: List<String>,
        val failures: Int,
        val firstError: String? = null
    )

    private fun postUnreadPhoneNotifications(
        context: Context,
        unread: List<UserNotificationRow>
    ): PostResult {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EVENTS_ALERTS_CHANNEL_ID,
                "Events and social alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Friend requests, invites, and event status updates"
            }
            mgr.createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return PostResult(emptyList(), unread.size, "POST_NOTIFICATIONS permission denied")
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return PostResult(emptyList(), unread.size, "App notifications disabled")
        }

        val delivered = mutableListOf<String>()
        var failures = 0
        var firstError: String? = null
        unread.take(5).forEachIndexed { index, item ->
            runCatching {
                val deepLink = extractDeepLinkForNotification(item)
                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (!deepLink.isNullOrBlank()) {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(deepLink)
                    }
                }
                val contentIntent = PendingIntent.getActivity(
                    context,
                    item.id.hashCode(),
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val visibleBody = stripInlineDeepLink(item.body)
                NotificationManagerCompat.from(context).notify(
                    3400 + index,
                    NotificationCompat.Builder(context, EVENTS_ALERTS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.spacer_logo)
                        .setContentTitle(item.title)
                        .setContentText(visibleBody)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(visibleBody))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .build()
                )
                delivered += item.id
            }.onFailure { e ->
                failures++
                if (firstError == null) firstError = e.message ?: e::class.java.simpleName
            }
        }
        return PostResult(
            deliveredIds = delivered,
            failures = failures,
            firstError = firstError
        )
    }

    private fun extractDeepLinkForNotification(item: UserNotificationRow): String {
        val body = item.body
        val title = item.title.lowercase()
        val inlineDeepLink = extractInlineDeepLink(body)
        if (!inlineDeepLink.isNullOrBlank()) return inlineDeepLink
        return when {
            "friend request" in title || "friend request" in body.lowercase() -> "spacer://social/requests"
            "message" in title || "message" in body.lowercase() -> "spacer://dm/threads"
            "event" in title || "event" in body.lowercase() -> "spacer://events"
            else -> "spacer://events"
        }
    }

    private fun extractInlineDeepLink(text: String): String? {
        val start = text.indexOf("spacer://")
        if (start < 0) return null
        val tail = text.substring(start)
        val end = tail.indexOfFirst { it.isWhitespace() }
        return if (end < 0) tail.trim() else tail.substring(0, end).trim()
    }

    private fun stripInlineDeepLink(text: String): String {
        val link = extractInlineDeepLink(text) ?: return text
        return text.replace(link, "").replace(Regex("\\s+"), " ").trim()
    }
}
