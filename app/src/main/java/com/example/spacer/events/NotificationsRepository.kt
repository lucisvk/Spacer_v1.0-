package com.example.spacer.events

import android.util.Log
import com.example.spacer.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserNotificationRow(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null
)

@Serializable
private data class UserNotificationInsert(
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val body: String
)

class NotificationsRepository {
    private val supabase = SupabaseManager.client

    private companion object {
        const val TAG = "NotificationsRepo"
    }

    object DeepLinks {
        fun eventsHub(): String = "spacer://events"
        fun eventInvite(eventId: String): String = "spacer://event/$eventId"
        fun eventChat(eventId: String): String = "spacer://event-chat/$eventId"
        fun dmThreads(): String = "spacer://dm/threads"
        fun dmChat(peerUserId: String): String = "spacer://dm/$peerUserId"
        fun socialRequests(): String = "spacer://social/requests"
        fun publicProfile(userId: String): String = "spacer://profile/$userId"
    }

    suspend fun listUnread(): Result<List<UserNotificationRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("user_notifications")
                .select {
                    filter {
                        eq("user_id", user.id)
                    }
                }
                .decodeList<UserNotificationRow>()
                .filter { it.readAt.isNullOrBlank() }
                .sortedByDescending { it.createdAt ?: "" }
            Result.success(rows)
        } catch (e: Exception) {
            Log.e(TAG, "listUnread failed", e)
            Result.success(emptyList())
        }
    }

    suspend fun markRead(notificationId: String): Result<Unit> {
        return try {
            supabase.from("user_notifications").update(
                {
                    set("read_at", java.time.OffsetDateTime.now().toString())
                }
            ) {
                filter { eq("id", notificationId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markRead failed for id=$notificationId", e)
            Result.failure(e)
        }
    }

    suspend fun createForUser(
        userId: String,
        title: String,
        body: String,
        deepLink: String? = null
    ): Result<Unit> {
        return try {
            val normalizedBody = buildString {
                append(body.trim().ifBlank { "You have a new update." })
                val link = deepLink?.trim().orEmpty()
                if (link.isNotBlank()) {
                    append("\n")
                    append(link)
                }
            }
            supabase.from("user_notifications").insert(
                UserNotificationInsert(
                    userId = userId,
                    title = title.trim().ifBlank { "Notification" },
                    body = normalizedBody
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createForUser failed for userId=$userId", e)
            Result.failure(e)
        }
    }
}
