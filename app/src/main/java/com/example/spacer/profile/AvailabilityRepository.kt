package com.example.spacer.profile

import com.example.spacer.network.SupabaseManager
import com.example.spacer.network.SupabaseRequestGuard
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Serializable
private data class AvailabilityPrefsRow(
    @SerialName("user_id") val userId: String,
    @SerialName("calendar_provider") val calendarProvider: String? = null,
    @SerialName("calendar_connected") val calendarConnected: Boolean = false,
    @SerialName("show_to_friends_only") val showToFriendsOnly: Boolean = true,
    @SerialName("auto_decline_conflicts") val autoDeclineConflicts: Boolean = false
)

@Serializable
private data class AvailabilityPrefsUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("calendar_provider") val calendarProvider: String? = null,
    @SerialName("calendar_connected") val calendarConnected: Boolean = false,
    @SerialName("show_to_friends_only") val showToFriendsOnly: Boolean = true,
    @SerialName("auto_decline_conflicts") val autoDeclineConflicts: Boolean = false
)

@Serializable
private data class WeeklyWindowRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

@Serializable
private data class WeeklyWindowInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

@Serializable
private data class SpecificAvailabilityRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val note: String? = null
)

@Serializable
private data class SpecificAvailabilityInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val note: String? = null
)

@Serializable
private data class ScheduleConflictRpcRow(
    @SerialName("user_id") val userId: String,
    @SerialName("conflict_reason") val conflictReason: String
)

data class AvailabilityPreferencesUi(
    val calendarProvider: String?,
    val calendarConnected: Boolean,
    val showToFriendsOnly: Boolean,
    val autoDeclineConflicts: Boolean
)

data class WeeklyAvailabilityWindowUi(
    val id: String,
    val dayOfWeek: Int,
    val startsAt: String,
    val endsAt: String
)

data class SpecificAvailabilityWindowUi(
    val id: String,
    val startsAt: String,
    val endsAt: String,
    val isAvailable: Boolean,
    val note: String?
)

class AvailabilityRepository {
    private val supabase = SupabaseManager.client
    private suspend fun <T> guarded(block: suspend () -> T): T = SupabaseRequestGuard.run(block)

    suspend fun loadPreferences(): Result<AvailabilityPreferencesUi> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val row = guarded {
                supabase.from("user_availability_preferences")
                    .select {
                        filter { eq("user_id", user.id) }
                        limit(1)
                    }
                    .decodeList<AvailabilityPrefsRow>()
                    .firstOrNull()
            }
            Result.success(
                AvailabilityPreferencesUi(
                    calendarProvider = row?.calendarProvider,
                    calendarConnected = row?.calendarConnected ?: false,
                    showToFriendsOnly = row?.showToFriendsOnly ?: true,
                    autoDeclineConflicts = row?.autoDeclineConflicts ?: false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePreferences(
        calendarProvider: String?,
        calendarConnected: Boolean,
        showToFriendsOnly: Boolean,
        autoDeclineConflicts: Boolean
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            guarded {
                supabase.from("user_availability_preferences").upsert(
                    AvailabilityPrefsUpsert(
                        userId = user.id,
                        calendarProvider = calendarProvider,
                        calendarConnected = calendarConnected,
                        showToFriendsOnly = showToFriendsOnly,
                        autoDeclineConflicts = autoDeclineConflicts
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listWeeklyWindows(): Result<List<WeeklyAvailabilityWindowUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = guarded {
                supabase.from("user_weekly_availability_windows")
                    .select {
                        filter { eq("user_id", user.id) }
                        order(column = "day_of_week", order = Order.ASCENDING)
                    }
                    .decodeList<WeeklyWindowRow>()
            }
            Result.success(
                rows.map {
                    WeeklyAvailabilityWindowUi(
                        id = it.id,
                        dayOfWeek = it.dayOfWeek,
                        startsAt = it.startsAt,
                        endsAt = it.endsAt
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addWeeklyWindow(dayOfWeek: Int, startsAt: LocalTime, endsAt: LocalTime): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            guarded {
                supabase.from("user_weekly_availability_windows").insert(
                    WeeklyWindowInsert(
                        userId = user.id,
                        dayOfWeek = dayOfWeek,
                        startsAt = startsAt.toString(),
                        endsAt = endsAt.toString()
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeWeeklyWindow(windowId: String): Result<Unit> {
        return try {
            guarded {
                supabase.from("user_weekly_availability_windows").delete {
                    filter { eq("id", windowId) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSpecificAvailability(): Result<List<SpecificAvailabilityWindowUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = guarded {
                supabase.from("user_specific_availability")
                    .select {
                        filter { eq("user_id", user.id) }
                        order(column = "starts_at", order = Order.ASCENDING)
                    }
                    .decodeList<SpecificAvailabilityRow>()
            }
            Result.success(
                rows.map {
                    SpecificAvailabilityWindowUi(
                        id = it.id,
                        startsAt = it.startsAt,
                        endsAt = it.endsAt,
                        isAvailable = it.isAvailable,
                        note = it.note
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSpecificAvailability(
        startsAt: Instant,
        endsAt: Instant,
        isAvailable: Boolean,
        note: String? = null
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            guarded {
                supabase.from("user_specific_availability").insert(
                    SpecificAvailabilityInsert(
                        userId = user.id,
                        startsAt = startsAt.toString(),
                        endsAt = endsAt.toString(),
                        isAvailable = isAvailable,
                        note = note?.trim()?.ifBlank { null }
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSpecificAvailability(windowId: String): Result<Unit> {
        return try {
            guarded {
                supabase.from("user_specific_availability").delete {
                    filter { eq("id", windowId) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Server-side comparison of friends' availability vs event window (weekly + busy blocks).
     * Requires database function [schedule_conflicts_for_friends] from migrations.
     */
    suspend fun scheduleConflictsForFriends(
        startsAtIso: String,
        endsAtIso: String?,
        friendIds: List<String>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<List<Pair<String, String>>> {
        return try {
            val cleaned = friendIds.distinct().filter {
                runCatching { UUID.fromString(it) }.isSuccess
            }
            if (cleaned.isEmpty()) return Result.success(emptyList())
            val params = buildJsonObject {
                put("p_starts_at", JsonPrimitive(startsAtIso))
                if (endsAtIso != null) {
                    put("p_ends_at", JsonPrimitive(endsAtIso))
                }
                put(
                    "p_friend_ids",
                    JsonArray(cleaned.map { JsonPrimitive(it) })
                )
                put("p_tz", JsonPrimitive(zoneId.id))
            }
            val rows = guarded {
                supabase.postgrest.rpc(
                    "schedule_conflicts_for_friends",
                    params
                ).decodeList<ScheduleConflictRpcRow>()
            }
            Result.success(rows.map { it.userId to it.conflictReason })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
