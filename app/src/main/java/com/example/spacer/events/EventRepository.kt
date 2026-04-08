package com.example.spacer.events

import com.example.spacer.network.SupabaseManager
import com.example.spacer.network.SupabaseRequestGuard
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.FriendListItem
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.ProfileRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@Serializable
private data class AppEventInsert(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("host_id") val hostId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String? = null,
    val location: String? = null,
    val visibility: String = "public",
    val category: String? = null,
    @SerialName("max_attendees") val maxAttendees: Int? = null,
    @SerialName("bring_items") val bringItems: String? = null
)

@Serializable
private data class CancelHostedEventRpc(
    @SerialName("p_event_id") val pEventId: String
)

@Serializable
private data class EventInviteInsert(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("invitee_id") val inviteeId: String,
    val status: String = "pending"
)

@Serializable
private data class EventInviteRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("invitee_id") val inviteeId: String,
    val status: String
)

@Serializable
private data class PublicEventInviteRow(
    val id: String? = null,
    @SerialName("event_id") val eventId: String
)

@Serializable
private data class PublicListingInsert(
    @SerialName("event_id") val eventId: String
)

@Serializable
private data class UserBlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

@Serializable
private data class EventAvailabilityUpsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null
)

@Serializable
private data class EventAvailabilityUpsertWithCalendar(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null,
    @SerialName("calendar_busy_overlaps_event") val calendarBusyOverlapsEvent: Boolean
)

@Serializable
private data class AvailabilityRow(
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null,
    @SerialName("calendar_busy_overlaps_event") val calendarBusyOverlapsEvent: Boolean? = null
)

@Serializable
private data class SelfBlockedAvailabilityRow(
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val note: String? = null
)

@Serializable
private data class SelfWeeklyWindowRow(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

data class PendingInviteUi(
    val inviteId: String,
    val eventId: String,
    val title: String,
    val startsAt: String,
    val endsAt: String?,
    val location: String?,
    val hostDisplayName: String
)

data class AvailabilityEntryUi(
    val userId: String,
    val displayName: String,
    val presetSlots: String,
    val notes: String?,
    val calendarBusyOverlapsEvent: Boolean = false,
    /** True when [user_specific_availability] has an `is_available=false` row that overlaps the event window. */
    val specificBusyOverlapsEvent: Boolean = false,
    /** True when the event window falls entirely outside this guest's weekly availability windows. */
    val outsideWeeklyWindows: Boolean = false,
    /** True when no `event_availability` row exists for the guest yet. */
    val pendingReply: Boolean = false
)

@Serializable
private data class UserWeeklyWindowRow(
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

@Serializable
private data class UserSpecificAvailabilityRow(
    @SerialName("user_id") val userId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean = true
)

/** Event the current user hosts or has accepted an invite to; shown on the Events hub. */
data class MyEventHubItem(
    val event: EventRow,
    val isHosting: Boolean
)

/** [invitesSent] may be lower than requested when some IDs are not real auth users. */
data class CreateEventOutcome(
    val eventId: String,
    val invitesRequested: Int,
    val invitesSent: Int
)

@Serializable
private data class EventMemberRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "attendee",
    val status: String = "active"
)


@Serializable
private data class EventMemberInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val status: String = "active",
    @SerialName("added_by") val addedBy: String? = null
)

@Serializable
private data class EventChatRoomRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("chat_mode") val chatMode: String = "all_members"
)

@Serializable
private data class EventChatRoomInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("chat_mode") val chatMode: String = "all_members",
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class EventChatMessageRow(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class EventChatMessageInsert(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String
)

data class EventChatMessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val body: String,
    val createdAt: String
)

@Serializable
private data class DmConversationRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

@Serializable
private data class DmConversationInsert(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String
)

@Serializable
private data class DmMessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class DmMessageInsert(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String
)

@Serializable
private data class RealtimeChatBroadcastPayload(
    val eventName: String? = null
)

data class DmThreadUi(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatarUrl: String?,
    val lastMessageAt: String?,
    val lastMessagePreview: String? = null,
    val lastMessageSenderName: String? = null
)

data class DmMessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String
)

data class ChatPresenceUi(
    val userId: String,
    val displayName: String,
    val role: String,
    val avatarUrl: String? = null
)

@Serializable
private data class EventLiveStatusRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("sharing_enabled") val sharingEnabled: Boolean = true,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
private data class EventLiveStatusInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("sharing_enabled") val sharingEnabled: Boolean = true,
    @SerialName("arrived_at") val arrivedAt: String? = null
)

data class EventLiveGuestUi(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val isArrived: Boolean,
    val sharingEnabled: Boolean,
    val lat: Double?,
    val lng: Double?,
    val updatedAt: String?
)

@Serializable
private data class EventBringItemClaimRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_label") val itemLabel: String,
    @SerialName("claimed_by") val claimedBy: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class EventBringItemClaimInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_label") val itemLabel: String,
    @SerialName("claimed_by") val claimedBy: String
)

data class BringItemClaimUi(
    val itemKey: String,
    val itemLabel: String,
    val claimedByUserId: String,
    val claimedByName: String
)

private object LocalDemoChatStore {
    private const val DEMO_EVENT_ID = "demo-local-event-1"
    private const val DEMO_HOST_ID = "demo-host-local"
    private var startedAt: String = OffsetDateTime.now().plusHours(2).withNano(0).toString()
    private var endedAt: String = OffsetDateTime.now().plusHours(4).withNano(0).toString()
    private var bringItems: String = "Water bottle, notebook"

    fun demoEvent(): EventRow = EventRow(
        id = DEMO_EVENT_ID,
        title = "Demo Event Chat Sandbox",
        description = "Local fallback event to test chat, co-host tools, and presence when API is unavailable.",
        hostId = DEMO_HOST_ID,
        startsAt = startedAt,
        endsAt = endedAt,
        location = "Times Square, New York, NY 10036",
        bringItems = bringItems,
        visibility = "public",
        category = "Social"
    )

    private val eventMessagesByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableListOf(
            EventChatMessageUi(
                id = "demo-msg-1",
                senderId = DEMO_HOST_ID,
                senderName = "Demo Host",
                senderRole = "host",
                body = "Welcome! This is the offline demo event chat.",
                createdAt = OffsetDateTime.now().minusMinutes(5).withNano(0).toString()
            )
        )
    )
    private val eventChatModeByEventId = mutableMapOf(DEMO_EVENT_ID to "all_members")
    private val eventPresenceByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableSetOf(DEMO_HOST_ID, "demo-cohost-local")
    )
    private val cohostsByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableListOf(
            FriendListItem(
                id = "demo-cohost-local",
                fullName = "Demo Co-host",
                username = "demo_cohost",
                avatarUrl = null,
                presenceStatus = "online"
            )
        )
    )
    private val dmMessagesByConversationId = mutableMapOf<String, MutableList<DmMessageUi>>()
    private val bringClaimsByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableMapOf("water bottle" to "demo-cohost-local")
    )

    fun isDemoEvent(eventId: String): Boolean = eventId == DEMO_EVENT_ID

    fun updateDemoEventCoreDetails(startsAtIso: String, endsAtIso: String?, bringItemsValue: String?) {
        startedAt = startsAtIso
        endedAt = endsAtIso ?: OffsetDateTime.now().plusHours(4).withNano(0).toString()
        bringItems = bringItemsValue?.trim()?.ifBlank { "Water bottle, notebook" } ?: "Water bottle, notebook"
    }

    fun chatMode(eventId: String): String = eventChatModeByEventId[eventId] ?: "all_members"

    fun setChatMode(eventId: String, mode: String) {
        eventChatModeByEventId[eventId] = mode
    }

    fun listEventMessages(eventId: String): List<EventChatMessageUi> =
        eventMessagesByEventId[eventId]?.toList() ?: emptyList()

    fun appendEventMessage(eventId: String, message: EventChatMessageUi) {
        val list = eventMessagesByEventId.getOrPut(eventId) { mutableListOf() }
        list.add(message)
    }

    fun markPresent(eventId: String, userId: String) {
        eventPresenceByEventId.getOrPut(eventId) { mutableSetOf() }.add(userId)
    }

    fun listPresence(eventId: String): Set<String> =
        eventPresenceByEventId[eventId]?.toSet() ?: emptySet()

    fun listCohosts(eventId: String): List<FriendListItem> =
        cohostsByEventId[eventId]?.toList() ?: emptyList()

    fun addCohost(eventId: String, item: FriendListItem) {
        cohostsByEventId.getOrPut(eventId) { mutableListOf() }.add(item)
    }

    fun removeCohost(eventId: String, userId: String) {
        cohostsByEventId[eventId]?.removeAll { it.id == userId }
    }

    fun dmConversationIdForPair(a: String, b: String): String {
        val first = minOf(a, b)
        val second = maxOf(a, b)
        return "demo-dm-$first-$second"
    }

    fun listDmMessages(conversationId: String): List<DmMessageUi> =
        dmMessagesByConversationId[conversationId]?.toList() ?: emptyList()

    fun appendDmMessage(conversationId: String, message: DmMessageUi) {
        val list = dmMessagesByConversationId.getOrPut(conversationId) { mutableListOf() }
        list.add(message)
    }

    fun bringClaims(eventId: String): Map<String, String> =
        bringClaimsByEventId[eventId]?.toMap() ?: emptyMap()

    fun setBringClaim(eventId: String, itemKey: String, userId: String?) {
        val map = bringClaimsByEventId.getOrPut(eventId) { mutableMapOf() }
        if (userId == null) map.remove(itemKey) else map[itemKey] = userId
    }
}

class EventRepository {
    private val supabase = SupabaseManager.client
    private val notificationsRepo = NotificationsRepository()
    private val profileCache = mutableMapOf<String, ProfileRow>()

    private fun parseDate(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun displayName(profile: ProfileRow?, fallback: String = "User"): String {
        if (profile == null) return fallback
        return profile.fullName?.ifBlank { profile.username ?: fallback } ?: (profile.username ?: fallback)
    }

    private suspend fun getProfileCached(userId: String): ProfileRow? {
        profileCache[userId]?.let { return it }
        val profile = runCatching {
            SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
            }
        }.getOrNull()
        if (profile != null) {
            profileCache[userId] = profile
        }
        return profile
    }

    /** Batch-load profiles for many user ids in a single round trip and warm the cache. */
    private suspend fun prefetchProfiles(userIds: Collection<String>) {
        val missing = userIds.filter { it.isNotBlank() && it !in profileCache }.distinct()
        if (missing.isEmpty()) return
        runCatching {
            SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .select {
                        filter { isIn("id", missing) }
                    }
                    .decodeList<ProfileRow>()
            }
        }.onSuccess { rows ->
            rows.forEach { profileCache[it.id] = it }
        }
    }

    /** Fetch many events in one batched query. Preserves order of input ids. */
    private suspend fun getEventsByIds(ids: Collection<String>): List<EventRow> {
        val unique = ids.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty()) return emptyList()
        val rows = runCatching {
            SupabaseRequestGuard.run {
                supabase.from("app_events")
                    .select {
                        filter { isIn("id", unique) }
                    }
                    .decodeList<EventRow>()
            }
        }.getOrDefault(emptyList())
        val byId = rows.associateBy { it.id }
        return unique.mapNotNull { byId[it] }
    }

    /**
     * Active attendee counts (per event) for the given event ids in a single round trip.
     * Returned map omits events with zero active members.
     */
    suspend fun attendeeCountsByEventId(eventIds: Collection<String>): Map<String, Int> {
        val unique = eventIds.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty()) return emptyMap()
        return runCatching {
            SupabaseRequestGuard.run {
                supabase.from("event_members")
                    .select {
                        filter {
                            isIn("event_id", unique)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
            }.groupingBy { it.eventId }.eachCount()
        }.getOrDefault(emptyMap())
    }

    private suspend fun currentUserIdOrDemo(): String {
        return supabase.auth.currentUserOrNull()?.id ?: "demo-me-local"
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value); true }.getOrDefault(false)

    private fun normalizeBringItemKey(raw: String): String {
        return raw.trim().lowercase()
    }

    fun parseBringItems(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    fun encodeBringItems(items: List<String>): String? {
        val cleaned = items.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        return cleaned.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private suspend fun blockedUserIdsForCurrentUser(currentUserId: String): Set<String> {
        val blockedByMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocker_id", currentUserId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockedId }
        }.getOrDefault(emptyList())
        val blockedMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocked_id", currentUserId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockerId }
        }.getOrDefault(emptyList())
        return (blockedByMe + blockedMe).toSet()
    }

    suspend fun createEventWithInvites(
        title: String,
        description: String?,
        startsAtIso: String,
        endsAtIso: String?,
        locationLabel: String?,
        inviteeIds: List<String>,
        cohostIds: List<String> = emptyList(),
        visibility: String = "public",
        maxAttendees: Int? = null,
        category: String? = null,
        bringItems: String? = null,
        chatMode: String = "all_members"
    ): Result<CreateEventOutcome> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val vis = visibility.trim().lowercase().let { v ->
                if (v == "invite_only") "invite_only" else "public"
            }
            val eventId = UUID.randomUUID().toString()
            val cohostDistinct = cohostIds.distinct()
                .filter { it != user.id }
                .mapNotNull { id -> runCatching { UUID.fromString(id).toString() }.getOrNull() }
                .filterNot { ProfileRepository.isOfflineDemoProfile(it) }
            val inviteDistinct = inviteeIds.distinct()
                .filter { it != user.id && it !in cohostDistinct }
                .mapNotNull { id -> runCatching { UUID.fromString(id).toString() }.getOrNull() }
            supabase.from("app_events").insert(
                AppEventInsert(
                    id = eventId,
                    title = title.trim(),
                    description = description?.trim()?.ifBlank { null },
                    hostId = user.id,
                    startsAt = startsAtIso,
                    endsAt = endsAtIso?.ifBlank { null },
                    location = locationLabel?.ifBlank { null },
                    visibility = vis,
                    maxAttendees = maxAttendees?.takeIf { it > 0 },
                    category = category?.trim()?.ifBlank { null },
                    bringItems = bringItems?.trim()?.ifBlank { null }
                )
            )
            supabase.from("event_members").insert(
                EventMemberInsert(
                    eventId = eventId,
                    userId = user.id,
                    role = "host",
                    status = "active",
                    addedBy = user.id
                )
            )
            cohostDistinct.forEach { cohostId ->
                supabase.from("event_members").insert(
                    EventMemberInsert(
                        eventId = eventId,
                        userId = cohostId,
                        role = "cohost",
                        status = "active",
                        addedBy = user.id
                    )
                )
            }
            val normalizedChatMode = when (chatMode.trim().lowercase()) {
                "host_cohosts_only" -> "host_cohosts_only"
                "disabled" -> "disabled"
                else -> "all_members"
            }
            supabase.from("event_chat_rooms").insert(
                EventChatRoomInsert(
                    eventId = eventId,
                    chatMode = normalizedChatMode,
                    createdBy = user.id
                )
            )
            // Insert invites one-by-one so one invalid recipient does not fail event creation.
            val distinct = inviteDistinct
            var invitesSent = 0
            distinct.forEach { inviteeId ->
                if (ProfileRepository.isOfflineDemoProfile(inviteeId)) {
                    // Local demo users are not in auth.users; keep UX consistent in demo mode.
                    invitesSent++
                    return@forEach
                }
                val ok = runCatching {
                    supabase.from("event_invites").insert(
                        EventInviteInsert(
                            id = UUID.randomUUID().toString(),
                            eventId = eventId,
                            inviteeId = inviteeId,
                            status = "pending"
                        )
                    )
                }.isSuccess
                if (ok) {
                    invitesSent++
                    runCatching {
                        supabase.from("event_members").insert(
                            EventMemberInsert(
                                eventId = eventId,
                                userId = inviteeId,
                                role = "attendee",
                                // Keep invitees out of "Your events" until they explicitly accept.
                                status = "pending",
                                addedBy = user.id
                            )
                        )
                    }
                    runCatching {
                        notificationsRepo.createForUser(
                            userId = inviteeId,
                            title = "New event invite",
                            body = "You were invited to \"$title\".",
                            deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                        )
                    }
                }
            }
            if (vis == "public") {
                registerPublicEventListing(eventId)
            }
            Result.success(
                CreateEventOutcome(
                    eventId = eventId,
                    invitesRequested = distinct.size,
                    invitesSent = invitesSent
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Notify all event members who have NOT yet posted availability so they can reply.
     * Returns the number of nudges sent.
     */
    suspend fun nudgeNoReplyMembers(eventId: String): Result<Int> {
        return try {
            val event = getEvent(eventId).getOrElse { return Result.failure(it) }
            val members = runCatching {
                supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
                    .map { it.userId }
                    .filter { it != event.hostId }
                    .distinct()
            }.getOrDefault(emptyList())
            if (members.isEmpty()) return Result.success(0)
            val replied = runCatching {
                supabase.from("event_availability")
                    .select {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<AvailabilityRow>()
                    .map { it.userId }
                    .toSet()
            }.getOrDefault(emptySet())
            val toNudge = members.filterNot { it in replied }
            toNudge.forEach { uid ->
                runCatching {
                    notificationsRepo.createForUser(
                        userId = uid,
                        title = "Quick check-in",
                        body = "Please share your availability for \"${event.title}\".",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                }
            }
            Result.success(toNudge.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Posts an automated availability summary from the host (allowed even when chat mode is disabled). */
    suspend fun postAvailabilityConflictNotice(
        eventId: String,
        conflicts: List<Pair<String, String>>
    ): Result<Unit> {
        if (conflicts.isEmpty()) return Result.success(Unit)
        val lines = conflicts.map { (uid, reason) ->
            val name = displayName(getProfileCached(uid))
            "$name — $reason"
        }
        val body = "[Schedule] Possible conflicts — ${lines.joinToString("; ")}"
        return sendEventChatMessage(eventId, body)
    }

    /** New events are discoverable in “Public events” once [public.public_event_invites] exists (see database SQL). */
    private suspend fun registerPublicEventListing(eventId: String) {
        runCatching {
            supabase.from("public_event_invites").insert(PublicListingInsert(eventId = eventId))
        }
    }

    /**
     * Cancels a hosted event, notifies invitees (via [user_notifications] when DB migration is applied),
     * and deletes the event row (cascades invites).
     */
    suspend fun cancelHostedEvent(eventId: String): Result<Unit> {
        return try {
            val event = runCatching { getEvent(eventId).getOrThrow() }.getOrNull()
            val invitees = runCatching {
                supabase.from("event_invites")
                    .select {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<EventInviteRow>()
                    .map { it.inviteeId }
                    .distinct()
            }.getOrDefault(emptyList())
            invitees.forEach { inviteeId ->
                runCatching {
                    notificationsRepo.createForUser(
                        userId = inviteeId,
                        title = "Event canceled",
                        body = "The event \"${event?.title ?: "an event"}\" was canceled by the host.",
                        deepLink = NotificationsRepository.DeepLinks.eventsHub()
                    )
                }
            }
            supabase.postgrest.rpc("cancel_hosted_event", CancelHostedEventRpc(pEventId = eventId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Upcoming discoverable events for Home (public visibility only, soonest first). */
    suspend fun listUpcomingDiscoverableEvents(limit: Int = 24): Result<List<EventRow>> {
        return listPublicDiscoverableEvents().map { list ->
            val now = OffsetDateTime.now()
            list
                .filter { e ->
                    val d = parseDate(e.startsAt)
                    d != null && d.isAfter(now)
                }
                .take(limit)
        }
    }

    suspend fun listPendingInvites(): Result<List<PendingInviteUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val invites = SupabaseRequestGuard.run {
                supabase.from("event_invites")
                    .select {
                        filter {
                            eq("invitee_id", user.id)
                            eq("status", "pending")
                        }
                    }
                    .decodeList<EventInviteRow>()
            }
            if (invites.isEmpty()) return Result.success(emptyList())
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
            val events = getEventsByIds(invites.map { it.eventId }).associateBy { it.id }
            prefetchProfiles(events.values.map { it.hostId })
            val now = OffsetDateTime.now()
            val out = invites.mapNotNull { inv ->
                val event = events[inv.eventId] ?: return@mapNotNull null
                if (event.hostId in blockedIds) return@mapNotNull null
                // Drop invitations to events that have already ended (or started, when
                // there is no end time on the event). They clutter the hub and an RSVP
                // can no longer change anything.
                if (!isEventStillRsvpable(event.startsAt, event.endsAt, now)) return@mapNotNull null
                val host = profileCache[event.hostId]
                val hostName = host?.fullName?.ifBlank { null } ?: host?.username ?: "Host"
                PendingInviteUi(
                    inviteId = inv.id,
                    eventId = inv.eventId,
                    title = event.title,
                    startsAt = event.startsAt,
                    endsAt = event.endsAt,
                    location = event.location,
                    hostDisplayName = hostName
                )
            }
            Result.success(out.sortedBy { parseDate(it.startsAt) ?: OffsetDateTime.MAX })
        } catch (_: Exception) {
            // Keep the hub usable when invites cannot be fetched.
            Result.success(emptyList())
        }
    }

    /**
     * True when an event hasn't ended yet — i.e. an RSVP still has meaning.
     * If the event has no `endsAt`, we treat the start time as the cutoff so
     * "happening now" events stay actionable but tomorrow's stale invites get pruned.
     */
    private fun isEventStillRsvpable(startsAt: String, endsAt: String?, now: OffsetDateTime): Boolean {
        val end = endsAt?.takeIf { it.isNotBlank() }?.let { parseDate(it) }
        if (end != null) return end.isAfter(now)
        val start = parseDate(startsAt) ?: return true
        return start.isAfter(now)
    }

    suspend fun getEvent(eventId: String): Result<EventRow> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.demoEvent())
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val row = supabase.from("app_events")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter { eq("id", eventId) }
                            limit(1)
                        }.decodeList<EventRow>().firstOrNull()
                    }
                } ?: return Result.failure(IllegalStateException("Event not found or no longer available."))
            if (row.hostId != user.id) {
                val blockedIds = blockedUserIdsForCurrentUser(user.id)
                if (row.hostId in blockedIds) {
                    return Result.failure(IllegalStateException("Event unavailable."))
                }
            }
            Result.success(row)
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.demoEvent()) else Result.failure(e)
        }
    }

    suspend fun getInviteStatusForEvent(eventId: String): Result<String?> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("event_invites")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter {
                                eq("event_id", eventId)
                                eq("invitee_id", user.id)
                            }
                            limit(1)
                        }.decodeList<EventInviteRow>()
                    }
                }
            Result.success(rows.firstOrNull()?.status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Detect if the current user has an in-app availability conflict with the given event window.
     * Checks `user_specific_availability` busy windows AND `user_weekly_availability_windows` coverage.
     * If a conflict is detected, marks the user's `event_availability` row with
     * `calendar_busy_overlaps_event = true` and notifies the host so the
     * Guest availability dashboard reflects the conflict.
     */
    suspend fun detectAndRecordAvailabilityConflict(eventId: String): Result<Boolean> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.success(false)
            val event = getEvent(eventId).getOrElse { return Result.success(false) }
            val start = parseDate(event.startsAt) ?: return Result.success(false)
            val end = event.endsAt?.let { parseDate(it) } ?: start.plusHours(1)

            val busyConflict = runCatching {
                supabase.from("user_specific_availability")
                    .select {
                        filter {
                            eq("user_id", user.id)
                            eq("is_available", false)
                        }
                    }
                    .decodeList<SelfBlockedAvailabilityRow>()
            }.getOrDefault(emptyList()).any { row ->
                val rowStart = parseDate(row.startsAt) ?: return@any false
                val rowEnd = parseDate(row.endsAt) ?: return@any false
                rowStart.isBefore(end) && rowEnd.isAfter(start)
            }

            val weeklyConflict = if (busyConflict) false else runCatching {
                val rows = supabase.from("user_weekly_availability_windows")
                    .select {
                        filter { eq("user_id", user.id) }
                    }
                    .decodeList<SelfWeeklyWindowRow>()
                if (rows.isEmpty()) return@runCatching false
                val zone = java.time.ZoneId.systemDefault()
                val localStart = start.atZoneSameInstant(zone).toLocalDateTime()
                val localEnd = end.atZoneSameInstant(zone).toLocalDateTime()
                if (localStart.toLocalDate() != localEnd.toLocalDate()) return@runCatching false
                val dow = localStart.dayOfWeek.value
                val dayWindows = rows.filter { it.dayOfWeek == dow }
                if (dayWindows.isEmpty()) return@runCatching true
                val startTime = localStart.toLocalTime()
                val endTime = localEnd.toLocalTime()
                dayWindows.none { w ->
                    val ws = runCatching { java.time.LocalTime.parse(w.startsAt) }.getOrNull() ?: return@none false
                    val we = runCatching { java.time.LocalTime.parse(w.endsAt) }.getOrNull() ?: return@none false
                    !endTime.isAfter(we) && !startTime.isBefore(ws)
                }
            }.getOrDefault(false)

            val hasConflict = busyConflict || weeklyConflict
            if (!hasConflict) return Result.success(false)

            val reason = if (busyConflict) "Marked busy on their calendar" else "Outside their usual weekly availability"
            runCatching {
                val payload = EventAvailabilityUpsertWithCalendar(
                    eventId = eventId,
                    userId = user.id,
                    presetSlots = "",
                    notes = reason,
                    calendarBusyOverlapsEvent = true
                )
                runCatching { supabase.from("event_availability").insert(payload) }
                    .recoverCatching {
                        supabase.from("event_availability").update(
                            {
                                set("calendar_busy_overlaps_event", true)
                                set("notes", reason)
                            }
                        ) {
                            filter {
                                eq("event_id", eventId)
                                eq("user_id", user.id)
                            }
                        }
                    }
            }
            runCatching {
                val joinerName = displayName(getProfileCached(user.id), fallback = "Someone")
                notificationsRepo.createForUser(
                    userId = event.hostId,
                    title = "Guest availability conflict",
                    body = "$joinerName has a schedule conflict for \"${event.title}\". $reason.",
                    deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                )
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToInvite(eventId: String, accept: Boolean): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val status = if (accept) "accepted" else "declined"
            // Look up an existing invite row. Hosted invites create one; public-event
            // joins do not. We branch so callers get a single "yes/no" RSVP API.
            val existingInvite = SupabaseRequestGuard.run {
                supabase.from("event_invites")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("invitee_id", user.id)
                        }
                        limit(1)
                    }
                    .decodeList<EventInviteRow>()
                    .firstOrNull()
            }
            if (existingInvite == null) {
                // No invite row → public-event flow. Accept = join, decline = leave.
                return if (accept) {
                    joinPublicEvent(eventId)
                } else {
                    leaveJoinedEvent(eventId).recover {
                        // If they were never a member either, treat as a no-op success.
                        Unit
                    }
                }
            }
            SupabaseRequestGuard.run {
                supabase.from("event_invites").update(
                    {
                        set("status", status)
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("invitee_id", user.id)
                    }
                }
            }
            // Confirm the update actually persisted.
            val confirmed = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_invites")
                        .select {
                            filter {
                                eq("event_id", eventId)
                                eq("invitee_id", user.id)
                            }
                            limit(1)
                        }
                        .decodeList<EventInviteRow>()
                        .firstOrNull()
                }
            }.getOrNull()
            if (confirmed?.status != status) {
                return Result.failure(IllegalStateException("RSVP didn't save. Please try again."))
            }
            if (accept) {
                // Keep membership in sync so event chat and presence work immediately after accepting.
                runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("event_members").insert(
                            EventMemberInsert(
                                eventId = eventId,
                                userId = user.id,
                                role = "attendee",
                                status = "active",
                                addedBy = user.id
                            )
                        )
                    }
                }.onFailure {
                    runCatching {
                        SupabaseRequestGuard.run {
                            supabase.from("event_members").update(
                                {
                                    set("status", "active")
                                    set("role", "attendee")
                                    set("added_by", user.id)
                                }
                            ) {
                                filter {
                                    eq("event_id", eventId)
                                    eq("user_id", user.id)
                                }
                            }
                        }
                    }
                }
                runCatching { detectAndRecordAvailabilityConflict(eventId) }
            } else {
                runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("event_members").update(
                            { set("status", "removed") }
                        ) {
                            filter {
                                eq("event_id", eventId)
                                eq("user_id", user.id)
                                eq("role", "attendee")
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Join a public event from discovery and notify host/co-hosts.
     * Creates (or updates) invite/member rows so downstream event flows remain consistent.
     */
    suspend fun joinPublicEvent(eventId: String, calendarBusyConflict: Boolean = false): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val event = getEvent(eventId).getOrElse { return Result.failure(it) }
            if (event.hostId == user.id) return Result.success(Unit)
            if (event.visibility == "invite_only") {
                return Result.failure(IllegalStateException("Invite-only event"))
            }
            val capacity = event.maxAttendees
            if (capacity != null && capacity > 0) {
                val activeCount = runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("event_members")
                            .select {
                                filter {
                                    eq("event_id", eventId)
                                    eq("status", "active")
                                }
                            }
                            .decodeList<EventMemberRow>()
                            .size
                    }
                }.getOrDefault(0)
                if (activeCount >= capacity) {
                    return Result.failure(IllegalStateException("This event is full."))
                }
            }

            // Self-joiners may not be able to INSERT into event_invites under RLS (only host can).
            // For public-event self-join we treat the invite row as best-effort: try update first
            // (works if the host previously invited them), then attempt insert. Either way,
            // event_members is the source of truth for membership.
            runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_invites").update(
                        { set("status", "accepted") }
                    ) {
                        filter {
                            eq("event_id", eventId)
                            eq("invitee_id", user.id)
                        }
                    }
                }
            }.onFailure {
                runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("event_invites").insert(
                            EventInviteInsert(
                                id = UUID.randomUUID().toString(),
                                eventId = eventId,
                                inviteeId = user.id,
                                status = "accepted"
                            )
                        )
                    }
                }
            }

            val membershipWriteOk = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_members").insert(
                        EventMemberInsert(
                            eventId = eventId,
                            userId = user.id,
                            role = "attendee",
                            status = "active",
                            addedBy = user.id
                        )
                    )
                }
                true
            }.recoverCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_members").update(
                        {
                            set("status", "active")
                            set("role", "attendee")
                            set("added_by", user.id)
                        }
                    ) {
                        filter {
                            eq("event_id", eventId)
                            eq("user_id", user.id)
                        }
                    }
                }
                true
            }.getOrDefault(false)
            if (!membershipWriteOk) {
                return Result.failure(IllegalStateException("Couldn't add you to this event. Please try again."))
            }

            val verifiedMember = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_members")
                        .select {
                            filter {
                                eq("event_id", eventId)
                                eq("user_id", user.id)
                                eq("status", "active")
                            }
                            limit(1)
                        }
                        .decodeList<EventMemberRow>()
                        .firstOrNull()
                }
            }.getOrNull()
            if (verifiedMember == null) {
                return Result.failure(IllegalStateException("Join did not finish. Please try again."))
            }

            runCatching {
                val acceptedInvite = SupabaseRequestGuard.run {
                    supabase.from("event_invites")
                        .select {
                            filter {
                                eq("event_id", eventId)
                                eq("invitee_id", user.id)
                                eq("status", "accepted")
                            }
                            limit(1)
                        }
                        .decodeList<EventInviteRow>()
                        .firstOrNull()
                }
                if (acceptedInvite == null) {
                    SupabaseRequestGuard.run {
                        supabase.from("event_invites").update(
                            { set("status", "accepted") }
                        ) {
                            filter {
                                eq("event_id", eventId)
                                eq("invitee_id", user.id)
                            }
                        }
                    }
                }
            }

            runCatching {
                val joiner = getProfileCached(user.id)
                val joinerName = displayName(joiner, fallback = "Someone")
                notificationsRepo.createForUser(
                    userId = event.hostId,
                    title = "New attendee joined",
                    body = "$joinerName joined \"${event.title}\".",
                    deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                )
                listCohosts(eventId).getOrDefault(emptyList()).forEach { cohost ->
                    notificationsRepo.createForUser(
                        userId = cohost.id,
                        title = "New attendee joined",
                        body = "$joinerName joined \"${event.title}\".",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                }
                if (calendarBusyConflict) {
                    notificationsRepo.createForUser(
                        userId = user.id,
                        title = "Calendar conflict detected",
                        body = "You joined \"${event.title}\" but the time overlaps your connected calendar.",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                    notificationsRepo.createForUser(
                        userId = event.hostId,
                        title = "Attendee has a time conflict",
                        body = "$joinerName joined \"${event.title}\" with a calendar time conflict.",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                }
            }
            runCatching { detectAndRecordAvailabilityConflict(eventId) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Leave an event the user previously joined/accepted. */
    suspend fun leaveJoinedEvent(eventId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val event = getEvent(eventId).getOrElse { return Result.failure(it) }
            if (event.hostId == user.id) {
                return Result.failure(IllegalStateException("Host cannot leave their own event"))
            }
            runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_invites").update(
                        { set("status", "declined") }
                    ) {
                        filter {
                            eq("event_id", eventId)
                            eq("invitee_id", user.id)
                        }
                    }
                }
            }
            runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_members").update(
                        { set("status", "removed") }
                    ) {
                        filter {
                            eq("event_id", eventId)
                            eq("user_id", user.id)
                            eq("role", "attendee")
                        }
                    }
                }
            }
            runCatching {
                val profile = getProfileCached(user.id)
                val name = displayName(profile, "Someone")
                notificationsRepo.createForUser(
                    userId = event.hostId,
                    title = "Attendee left",
                    body = "$name left \"${event.title}\".",
                    deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Auto-share the user's stored weekly availability with the host of [eventId].
     * Called after a successful accept/join when "Share availability with hosts" is on.
     * Maps each [user_weekly_availability_windows] row covering the event's
     * day-of-week into our preset slots (morning/midday/afternoon/evening) and
     * pushes them via [submitAvailability]. No-op + success when there are no
     * weekly hours saved or the event time can't be parsed — we don't want to
     * block the RSVP flow with availability-share noise.
     */
    suspend fun shareAvailabilityFromWeeklyWindows(eventId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.success(Unit)
            val event = getEvent(eventId).getOrNull() ?: return Result.success(Unit)
            val start = runCatching { OffsetDateTime.parse(event.startsAt) }.getOrNull()
                ?: return Result.success(Unit)
            // java.time DayOfWeek: MONDAY=1..SUNDAY=7. Match the dashboard's weekly map.
            val eventDow = start.atZoneSameInstant(java.time.ZoneId.systemDefault())
                .dayOfWeek.value
            val windows = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("user_weekly_availability_windows")
                        .select {
                            filter {
                                eq("user_id", user.id)
                                eq("day_of_week", eventDow)
                            }
                        }
                        .decodeList<UserWeeklyWindowRow>()
                }
            }.getOrDefault(emptyList())
            if (windows.isEmpty()) return Result.success(Unit)
            val presets = mutableSetOf<String>()
            windows.forEach { row ->
                presets += weeklyWindowToPresetSlots(row.startsAt, row.endsAt)
            }
            if (presets.isEmpty()) return Result.success(Unit)
            submitAvailability(eventId = eventId, presetKeys = presets, notes = null)
        } catch (_: Exception) {
            // Auto-share is opportunistic; never fail the RSVP because of it.
            Result.success(Unit)
        }
    }

    /**
     * Map a `HH:MM:SS` time range into the four preset slots. Slots overlap an
     * hour each side so a 5-9 PM weekly window contributes both afternoon AND
     * evening, matching how the host dashboard parses replies.
     */
    private fun weeklyWindowToPresetSlots(startsAt: String, endsAt: String): Set<String> {
        val sHour = parseLocalHour(startsAt) ?: return emptySet()
        val eHour = parseLocalHour(endsAt) ?: return emptySet()
        val slots = mutableSetOf<String>()
        // Slot ranges (inclusive): morning 8-11, midday 12-14, afternoon 15-17, evening 18-21.
        if (sHour < 12 && eHour > 8) slots += "morning"
        if (sHour < 15 && eHour > 12) slots += "midday"
        if (sHour < 18 && eHour > 15) slots += "afternoon"
        if (sHour < 22 && eHour > 18) slots += "evening"
        return slots
    }

    private fun parseLocalHour(value: String): Int? {
        // Accepts "HH:MM:SS" or "HH:MM"
        return runCatching { value.substringBefore(':').toInt() }.getOrNull()
    }

    suspend fun submitAvailability(
        eventId: String,
        presetKeys: Set<String>,
        notes: String?,
        calendarBusyOverlapsEvent: Boolean = false
    ): Result<Unit> {
        runCatching {
            submitAvailabilityWithCalendarColumn(eventId, presetKeys, notes, calendarBusyOverlapsEvent)
        }.onSuccess {
            return Result.success(Unit)
        }
        return submitAvailabilityLegacy(eventId, presetKeys, notes)
    }

    private suspend fun submitAvailabilityWithCalendarColumn(
        eventId: String,
        presetKeys: Set<String>,
        notes: String?,
        calendarBusyOverlapsEvent: Boolean
    ) {
        val user = supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("Not logged in")
        val preset = presetKeys.joinToString(",")
        val trimmedNotes = notes?.trim()?.ifBlank { null }
        val payload = EventAvailabilityUpsertWithCalendar(
            eventId = eventId,
            userId = user.id,
            presetSlots = preset,
            notes = trimmedNotes,
            calendarBusyOverlapsEvent = calendarBusyOverlapsEvent
        )
        try {
            supabase.from("event_availability").insert(payload)
        } catch (_: Exception) {
            supabase.from("event_availability").update(
                {
                    set("preset_slots", preset)
                    set("notes", trimmedNotes)
                    set("calendar_busy_overlaps_event", calendarBusyOverlapsEvent)
                }
            ) {
                filter {
                    eq("event_id", eventId)
                    eq("user_id", user.id)
                }
            }
        }
    }

    private suspend fun submitAvailabilityLegacy(
        eventId: String,
        presetKeys: Set<String>,
        notes: String?
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val preset = presetKeys.joinToString(",")
            val payload = EventAvailabilityUpsert(
                eventId = eventId,
                userId = user.id,
                presetSlots = preset,
                notes = notes?.trim()?.ifBlank { null }
            )
            try {
                supabase.from("event_availability").insert(payload)
            } catch (_: Exception) {
                supabase.from("event_availability").update(
                    {
                        set("preset_slots", preset)
                        set("notes", notes?.trim()?.ifBlank { null })
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", user.id)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listAvailabilityForHost(eventId: String): Result<List<AvailabilityEntryUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val event = SupabaseRequestGuard.run {
                supabase.from("app_events")
                    .select {
                        filter { eq("id", eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
            }
            if (event.hostId != user.id) {
                return Result.failure(IllegalStateException("Only the host can view availability"))
            }

            // Active members (everyone the host should see in the dashboard, replied or not).
            val members = SupabaseRequestGuard.run {
                supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
            }
            // Anyone with a pending invite is also someone the host wants visibility into.
            val invites = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_invites")
                        .select {
                            filter { eq("event_id", eventId) }
                        }
                        .decodeList<EventInviteRow>()
                }
            }.getOrDefault(emptyList())
            val rosterIds = (members.map { it.userId } + invites.map { it.inviteeId })
                .filter { it != event.hostId }
                .distinct()
            if (rosterIds.isEmpty()) return Result.success(emptyList())

            // Existing per-event availability replies.
            val replies = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_availability")
                        .select {
                            filter {
                                eq("event_id", eventId)
                                isIn("user_id", rosterIds)
                            }
                        }
                        .decodeList<AvailabilityRow>()
                }
            }.getOrDefault(emptyList()).associateBy { it.userId }

            // Compare the event window against each guest's general availability.
            val windowMillis = parseDate(event.startsAt)?.let { start ->
                val endParsed = event.endsAt?.takeIf { it.isNotBlank() }?.let { parseDate(it) }
                val end = endParsed ?: start.plusHours(2)
                start to end
            }

            val weeklyByUser = if (windowMillis == null) emptyMap() else {
                runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("user_weekly_availability_windows")
                            .select { filter { isIn("user_id", rosterIds) } }
                            .decodeList<UserWeeklyWindowRow>()
                    }
                }.getOrDefault(emptyList()).groupBy { it.userId }
            }

            val specificByUser = if (windowMillis == null) emptyMap() else {
                runCatching {
                    SupabaseRequestGuard.run {
                        supabase.from("user_specific_availability")
                            .select { filter { isIn("user_id", rosterIds) } }
                            .decodeList<UserSpecificAvailabilityRow>()
                    }
                }.getOrDefault(emptyList()).groupBy { it.userId }
            }

            prefetchProfiles(rosterIds)
            val list = rosterIds.map { uid ->
                val profile = profileCache[uid]
                val name = profile?.fullName?.ifBlank { null } ?: profile?.username ?: "User"
                val reply = replies[uid]

                val specificBusy = specificByUser[uid]?.any { row ->
                    if (windowMillis == null) false
                    else if (row.isAvailable) false
                    else availabilityRowOverlapsWindow(row.startsAt, row.endsAt, windowMillis.first, windowMillis.second)
                } ?: false

                val weeklyOutside = if (windowMillis == null) false
                else weeklyWindowsExclude(weeklyByUser[uid].orEmpty(), windowMillis.first, windowMillis.second)

                AvailabilityEntryUi(
                    userId = uid,
                    displayName = name,
                    presetSlots = reply?.presetSlots ?: "",
                    notes = reply?.notes,
                    calendarBusyOverlapsEvent = reply?.calendarBusyOverlapsEvent == true,
                    specificBusyOverlapsEvent = specificBusy,
                    outsideWeeklyWindows = weeklyOutside,
                    pendingReply = reply == null
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** True if the given ISO range overlaps the event window. */
    private fun availabilityRowOverlapsWindow(
        rowStartIso: String,
        rowEndIso: String,
        eventStart: OffsetDateTime,
        eventEnd: OffsetDateTime
    ): Boolean {
        val rs = parseDate(rowStartIso) ?: return false
        val re = parseDate(rowEndIso) ?: return false
        return rs.isBefore(eventEnd) && re.isAfter(eventStart)
    }

    /**
     * True when [windows] cover *no* part of the event window, i.e. the user
     * isn't generally available for any of it. If they have no windows at all
     * we don't claim a conflict (we just don't have data).
     */
    private fun weeklyWindowsExclude(
        windows: List<UserWeeklyWindowRow>,
        eventStart: OffsetDateTime,
        eventEnd: OffsetDateTime
    ): Boolean {
        if (windows.isEmpty()) return false
        // Iterate every day the event spans (typically 1 day).
        val zone = java.time.ZoneId.systemDefault()
        var cursor = eventStart.atZoneSameInstant(zone)
        val end = eventEnd.atZoneSameInstant(zone)
        while (cursor.isBefore(end)) {
            val dow = cursor.dayOfWeek.value // Mon=1..Sun=7
            val dayStart = cursor.toLocalTime()
            val dayEnd = if (cursor.toLocalDate() == end.toLocalDate()) end.toLocalTime()
            else java.time.LocalTime.MAX
            val hasOverlap = windows.filter { it.dayOfWeek == dow }.any { w ->
                val ws = runCatching { java.time.LocalTime.parse(w.startsAt) }.getOrNull()
                val we = runCatching { java.time.LocalTime.parse(w.endsAt) }.getOrNull()
                if (ws == null || we == null) false
                else ws.isBefore(dayEnd) && we.isAfter(dayStart)
            }
            if (hasOverlap) return false
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay(zone)
        }
        return true
    }

    suspend fun updateEventCoreDetails(
        eventId: String,
        startsAtIso: String,
        endsAtIso: String?,
        bringItems: String?
    ): Result<Unit> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.updateDemoEventCoreDetails(startsAtIso, endsAtIso, bringItems)
                return Result.success(Unit)
            }
            val actorId = supabase.auth.currentUserOrNull()?.id
            val cleanedEnd = endsAtIso?.ifBlank { null }
            val cleanedBring = bringItems?.trim()?.ifBlank { null }
            SupabaseRequestGuard.run {
                supabase.from("app_events").update(
                    {
                        set("starts_at", startsAtIso)
                        set("ends_at", cleanedEnd)
                        set("bring_items", cleanedBring)
                    }
                ) {
                    filter { eq("id", eventId) }
                }
            }
            // Read back so a silently filtered UPDATE (RLS or wrong host) surfaces a
            // clear error instead of a fake "saved" toast.
            val confirmed = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", eventId) }
                            limit(1)
                        }
                        .decodeList<EventRow>()
                        .firstOrNull()
                }
            }.getOrNull()
            // Postgres normalizes timestamps to UTC on read-back, so a string
            // comparison would falsely fail (e.g., we sent "2026-05-11T15:00-04:00"
            // and Postgres returns "2026-05-11T13:00+00:00"). Compare instants
            // instead — same moment, different serialization.
            val sameInstant = run {
                val sent = runCatching { OffsetDateTime.parse(startsAtIso) }.getOrNull()?.toInstant()
                val back = confirmed?.startsAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull()?.toInstant() }
                sent != null && sent == back
            }
            if (confirmed == null || !sameInstant) {
                return Result.failure(IllegalStateException("Couldn't reschedule. Please try again."))
            }
            runCatching {
                val event = supabase.from("app_events")
                    .select {
                        filter { eq("id", eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
                val members = supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
                    .map { it.userId }
                    .distinct()
                    .filterNot { it == actorId }
                members.forEach { uid ->
                    notificationsRepo.createForUser(
                        userId = uid,
                        title = "Event details updated",
                        body = "\"${event.title}\" was updated. Check time/bring items.",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listCohosts(eventId: String): Result<List<FriendListItem>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.listCohosts(eventId))
            }
            val members = supabase.from("event_members")
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("role", "cohost")
                        eq("status", "active")
                    }
                }
                .decodeList<EventMemberRow>()
            val rows = members.mapNotNull { member ->
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", member.userId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    FriendListItem(
                        id = p.id,
                        fullName = displayName(p),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl,
                        presenceStatus = p.presenceStatus
                    )
                }.getOrNull()
            }
            Result.success(rows.sortedBy { it.fullName.lowercase() })
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.listCohosts(eventId)) else Result.failure(e)
        }
    }

    suspend fun addCohost(eventId: String, userId: String): Result<Unit> {
        return try {
            if (!LocalDemoChatStore.isDemoEvent(eventId) && !isUuid(userId)) {
                return Result.failure(IllegalArgumentException("Invalid user id for co-host"))
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.addCohost(
                    eventId,
                    FriendListItem(
                        id = userId,
                        fullName = "Demo Friend",
                        username = "demo_friend",
                        avatarUrl = null,
                        presenceStatus = "online"
                    )
                )
                return Result.success(Unit)
            }
            val actor = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            // Promote an existing member row before attempting insert.
            runCatching {
                supabase.from("event_members").update(
                    {
                        set("role", "cohost")
                        set("status", "active")
                        set("added_by", actor.id)
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", userId)
                    }
                }
            }
            runCatching {
                supabase.from("event_members").insert(
                    EventMemberInsert(
                        eventId = eventId,
                        userId = userId,
                        role = "cohost",
                        status = "active",
                        addedBy = actor.id
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeCohost(eventId: String, userId: String): Result<Unit> {
        return try {
            if (!LocalDemoChatStore.isDemoEvent(eventId) && !isUuid(userId)) {
                return Result.failure(IllegalArgumentException("Invalid user id for co-host"))
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.removeCohost(eventId, userId)
                return Result.success(Unit)
            }
            supabase.from("event_members").update(
                {
                    set("status", "removed")
                }
            ) {
                filter {
                    eq("event_id", eventId)
                    eq("user_id", userId)
                    eq("role", "cohost")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrCreateEventChatRoom(eventId: String): Result<String> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success("demo-room-$eventId")
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val existing = supabase.from("event_chat_rooms")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter { eq("event_id", eventId) }
                            limit(1)
                        }.decodeList<EventChatRoomRow>().firstOrNull()
                    }
                }
            if (existing != null) return Result.success(existing.id)
            SupabaseRequestGuard.run {
                supabase.from("event_chat_rooms").insert(
                    EventChatRoomInsert(eventId = eventId, createdBy = user.id)
                )
            }
            val created = supabase.from("event_chat_rooms")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter { eq("event_id", eventId) }
                            limit(1)
                        }.decodeSingle<EventChatRoomRow>()
                    }
                }
            Result.success(created.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEventChatMode(eventId: String): Result<String> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.chatMode(eventId))
            }
            val room = supabase.from("event_chat_rooms")
                .select {
                    filter { eq("event_id", eventId) }
                    limit(1)
                }
                .decodeList<EventChatRoomRow>()
                .firstOrNull()
            Result.success(room?.chatMode ?: "all_members")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setEventChatMode(eventId: String, mode: String): Result<Unit> {
        return try {
            val normalized = when (mode.trim().lowercase()) {
                "host_cohosts_only" -> "host_cohosts_only"
                "disabled" -> "disabled"
                else -> "all_members"
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.setChatMode(eventId, normalized)
                return Result.success(Unit)
            }
            supabase.from("event_chat_rooms").update(
                {
                    set("chat_mode", normalized)
                }
            ) {
                filter { eq("event_id", eventId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listEventChatMessages(eventId: String, limit: Int = 100): Result<List<EventChatMessageUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.listEventMessages(eventId).takeLast(limit))
            }
            val roomId = getOrCreateEventChatRoom(eventId).getOrThrow()
            val memberRows = runCatching {
                supabase.from("event_members")
                    .select {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<EventMemberRow>()
            }.getOrDefault(emptyList())
            val roleByUser = memberRows.associateBy { it.userId }
            val rows = supabase.from("event_chat_messages")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter { eq("room_id", roomId) }
                            order(column = "created_at", order = Order.ASCENDING)
                            limit(limit.toLong())
                        }.decodeList<EventChatMessageRow>()
                    }
                }
            val out = rows.map { row ->
                val profile = getProfileCached(row.senderId)
                val name = displayName(profile)
                val role = roleByUser[row.senderId]?.role ?: "attendee"
                EventChatMessageUi(
                    id = row.id,
                    senderId = row.senderId,
                    senderName = name,
                    senderRole = role,
                    body = row.body,
                    createdAt = row.createdAt
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.listEventMessages(eventId)) else Result.failure(e)
        }
    }

    fun subscribeEventChatMessages(eventId: String): Flow<Result<List<EventChatMessageUi>>> = flow {
        if (LocalDemoChatStore.isDemoEvent(eventId)) {
            while (true) {
                emit(Result.success(LocalDemoChatStore.listEventMessages(eventId)))
                delay(2200L)
            }
        }
        val roomId = getOrCreateEventChatRoom(eventId).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        emit(listEventChatMessages(eventId))
        val topic = "event_chat:$roomId"
        val channel = supabase.channel(topic)
        runCatching { channel.subscribe(blockUntilSubscribed = true) }
            .onFailure {
                emit(Result.failure(it))
                // Realtime fallback for clients that cannot subscribe successfully.
                while (true) {
                    emit(listEventChatMessages(eventId))
                    delay(4500L)
                }
            }
        channel.broadcastFlow<RealtimeChatBroadcastPayload>(event = "INSERT").collect {
            emit(listEventChatMessages(eventId))
        }
    }

    suspend fun sendEventChatMessage(eventId: String, body: String): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val message = body.trim()
            if (message.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.appendEventMessage(
                    eventId,
                    EventChatMessageUi(
                        id = "demo-msg-${System.currentTimeMillis()}",
                        senderId = userId,
                        senderName = "You",
                        senderRole = "attendee",
                        body = message,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                LocalDemoChatStore.markPresent(eventId, userId)
                return Result.success(Unit)
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val roomId = getOrCreateEventChatRoom(eventId).getOrThrow()
            supabase.from("event_chat_messages").insert(
                EventChatMessageInsert(
                    roomId = roomId,
                    senderId = user.id,
                    body = message
                )
            )
            if (!message.startsWith("[Schedule]")) {
                runCatching {
                    val event = supabase.from("app_events")
                        .select {
                            filter { eq("id", eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                    val members = supabase.from("event_members")
                        .select {
                            filter {
                                eq("event_id", eventId)
                                eq("status", "active")
                            }
                        }
                        .decodeList<EventMemberRow>()
                        .map { it.userId }
                        .distinct()
                        .filterNot { it == user.id }
                    members.forEach { uid ->
                        notificationsRepo.createForUser(
                            userId = uid,
                            title = "New event message",
                            body = "New message in \"${event.title}\".",
                            deepLink = NotificationsRepository.DeepLinks.eventChat(eventId)
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrCreateDmConversation(otherUserId: String): Result<String> {
        return try {
            val userId = currentUserIdOrDemo()
            if (userId == otherUserId) return Result.failure(IllegalArgumentException("Invalid peer"))
            if (userId.startsWith("demo-") || otherUserId.startsWith("demo-")) {
                return Result.success(LocalDemoChatStore.dmConversationIdForPair(userId, otherUserId))
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (user.id == otherUserId) return Result.failure(IllegalArgumentException("Invalid peer"))
            val first = minOf(user.id, otherUserId)
            val second = maxOf(user.id, otherUserId)
            val existing = supabase.from("dm_conversations")
                .select {
                    filter {
                        eq("user_a", first)
                        eq("user_b", second)
                    }
                    limit(1)
                }
                .decodeList<DmConversationRow>()
                .firstOrNull()
            if (existing != null) return Result.success(existing.id)
            supabase.from("dm_conversations").insert(
                DmConversationInsert(
                    userA = first,
                    userB = second
                )
            )
            val created = supabase.from("dm_conversations")
                .select {
                    filter {
                        eq("user_a", first)
                        eq("user_b", second)
                    }
                    limit(1)
                }
                .decodeSingle<DmConversationRow>()
            Result.success(created.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDmThreads(): Result<List<DmThreadUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("dm_conversations")
                .select { }
                .decodeList<DmConversationRow>()
                .filter { user.id == it.userA || user.id == it.userB }
            val threads = rows.mapNotNull { row ->
                val peerId = if (row.userA == user.id) row.userB else row.userA
                val p = getProfileCached(peerId) ?: return@mapNotNull null
                val latestMessage = runCatching {
                    supabase.from("dm_messages")
                        .select {
                            filter { eq("conversation_id", row.id) }
                            order(column = "created_at", order = Order.DESCENDING)
                            limit(1)
                        }
                        .decodeList<DmMessageRow>()
                        .firstOrNull()
                }.getOrNull()
                val latestSenderName = latestMessage?.let { latest ->
                    getProfileCached(latest.senderId)?.let { sender ->
                        displayName(sender)
                    }
                }
                DmThreadUi(
                    conversationId = row.id,
                    peerId = peerId,
                    peerName = displayName(p),
                    peerAvatarUrl = p.avatarUrl,
                    lastMessageAt = row.lastMessageAt,
                    lastMessagePreview = latestMessage?.body,
                    lastMessageSenderName = latestSenderName
                )
            }.sortedByDescending { it.lastMessageAt ?: "" }
            Result.success(threads)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDmMessages(conversationId: String, limit: Int = 200): Result<List<DmMessageUi>> {
        return try {
            if (conversationId.startsWith("demo-dm-")) {
                return Result.success(LocalDemoChatStore.listDmMessages(conversationId).takeLast(limit))
            }
            val rows = supabase.from("dm_messages")
                .select {
                    filter { eq("conversation_id", conversationId) }
                    order(column = "created_at", order = Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<DmMessageRow>()
            val out = rows.map { row ->
                val p = getProfileCached(row.senderId)
                DmMessageUi(
                    id = row.id,
                    senderId = row.senderId,
                    senderName = displayName(p),
                    body = row.body,
                    createdAt = row.createdAt
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribeDmMessages(conversationId: String): Flow<Result<List<DmMessageUi>>> = flow {
        if (conversationId.startsWith("demo-dm-")) {
            while (true) {
                emit(Result.success(LocalDemoChatStore.listDmMessages(conversationId)))
                delay(1200L)
            }
        }
        emit(listDmMessages(conversationId))
        val topic = "dm_chat:$conversationId"
        val channel = supabase.channel(topic)
        runCatching { channel.subscribe(blockUntilSubscribed = true) }
            .onFailure {
                emit(Result.failure(it))
                // Realtime fallback for clients that cannot subscribe successfully.
                while (true) {
                    emit(listDmMessages(conversationId))
                    delay(2000L)
                }
            }
        channel.broadcastFlow<RealtimeChatBroadcastPayload>(event = "INSERT").collect {
            emit(listDmMessages(conversationId))
        }
    }

    suspend fun sendDmMessage(conversationId: String, body: String): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val msg = body.trim()
            if (msg.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
            if (conversationId.startsWith("demo-dm-")) {
                LocalDemoChatStore.appendDmMessage(
                    conversationId,
                    DmMessageUi(
                        id = "demo-dm-msg-${System.currentTimeMillis()}",
                        senderId = userId,
                        senderName = "You",
                        body = msg,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                return Result.success(Unit)
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("dm_messages").insert(
                DmMessageInsert(
                    conversationId = conversationId,
                    senderId = user.id,
                    body = msg
                )
            )
            supabase.from("dm_conversations").update(
                {
                    set("last_message_at", OffsetDateTime.now().toString())
                }
            ) {
                filter { eq("id", conversationId) }
            }
            runCatching {
                val senderProfile = supabase.from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
                val senderName = displayName(senderProfile, fallback = "Someone")
                val convo = supabase.from("dm_conversations")
                    .select {
                        filter { eq("id", conversationId) }
                        limit(1)
                    }
                    .decodeSingle<DmConversationRow>()
                val otherUserId = if (convo.userA == user.id) convo.userB else convo.userA
                notificationsRepo.createForUser(
                    userId = otherUserId,
                    title = "New message",
                    body = "$senderName sent you a message.",
                    deepLink = NotificationsRepository.DeepLinks.dmChat(user.id)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribeEventChatPresence(eventId: String): Flow<Result<List<ChatPresenceUi>>> = flow {
        while (true) {
            emit(listEventChatPresence(eventId))
            delay(4500L)
        }
    }

    suspend fun listEventChatPresence(eventId: String): Result<List<ChatPresenceUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                val out = LocalDemoChatStore.listPresence(eventId).map { id ->
                    val role = when (id) {
                        "demo-host-local" -> "host"
                        "demo-cohost-local" -> "cohost"
                        else -> "attendee"
                    }
                    val label = when (id) {
                        "demo-host-local" -> "Demo Host"
                        "demo-cohost-local" -> "Demo Co-host"
                        else -> "You"
                    }
                    ChatPresenceUi(userId = id, displayName = label, role = role)
                }
                return Result.success(out)
            }
            val members = supabase.from("event_members")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter {
                                eq("event_id", eventId)
                                eq("status", "active")
                            }
                        }.decodeList<EventMemberRow>()
                    }
                }
            val out = members.mapNotNull { m ->
                val p = getProfileCached(m.userId) ?: return@mapNotNull null
                ChatPresenceUi(
                    userId = m.userId,
                    displayName = displayName(p),
                        role = m.role,
                        avatarUrl = p.avatarUrl
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listEventLiveGuests(eventId: String): Result<List<EventLiveGuestUi>> {
        return try {
            val members = SupabaseRequestGuard.run {
                supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
            }
            if (members.isEmpty()) return Result.success(emptyList())
            val liveByUser = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_live_status")
                        .select {
                            filter { eq("event_id", eventId) }
                        }
                        .decodeList<EventLiveStatusRow>()
                        .associateBy { it.userId }
                }
            }.getOrDefault(emptyMap())
            prefetchProfiles(members.map { it.userId })
            val guests = members.mapNotNull { member ->
                val profile = profileCache[member.userId] ?: return@mapNotNull null
                val live = liveByUser[member.userId]
                EventLiveGuestUi(
                    userId = member.userId,
                    displayName = displayName(profile),
                    avatarUrl = profile.avatarUrl,
                    isArrived = !live?.arrivedAt.isNullOrBlank(),
                    sharingEnabled = live?.sharingEnabled ?: true,
                    lat = live?.lat,
                    lng = live?.lng,
                    updatedAt = live?.updatedAt
                )
            }
            Result.success(guests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEventLiveSharing(
        eventId: String,
        sharingEnabled: Boolean,
        lat: Double? = null,
        lng: Double? = null
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val insert = EventLiveStatusInsert(
                eventId = eventId,
                userId = user.id,
                lat = if (sharingEnabled) lat else null,
                lng = if (sharingEnabled) lng else null,
                sharingEnabled = sharingEnabled
            )
            runCatching {
                supabase.from("event_live_status").insert(insert)
            }.getOrElse {
                supabase.from("event_live_status").update(
                    {
                        set("sharing_enabled", sharingEnabled)
                        set("lat", if (sharingEnabled) lat else null)
                        set("lng", if (sharingEnabled) lng else null)
                        if (!sharingEnabled) set("arrived_at", null as String?)
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", user.id)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markArrivedAtEvent(eventId: String, lat: Double?, lng: Double?): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val nowIso = OffsetDateTime.now().toString()
            val insert = EventLiveStatusInsert(
                eventId = eventId,
                userId = user.id,
                lat = lat,
                lng = lng,
                sharingEnabled = true,
                arrivedAt = nowIso
            )
            runCatching {
                supabase.from("event_live_status").insert(insert)
            }.getOrElse {
                supabase.from("event_live_status").update(
                    {
                        set("sharing_enabled", true)
                        set("arrived_at", nowIso)
                        set("lat", lat)
                        set("lng", lng)
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", user.id)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBringItemClaims(eventId: String): Result<List<BringItemClaimUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                val out = LocalDemoChatStore.bringClaims(eventId).map { (itemKey, userId) ->
                    val label = parseBringItems(LocalDemoChatStore.demoEvent().bringItems).firstOrNull {
                        normalizeBringItemKey(it) == itemKey
                    } ?: itemKey
                    val userLabel = when (userId) {
                        "demo-host-local" -> "Demo Host"
                        "demo-cohost-local" -> "Demo Co-host"
                        "demo-me-local" -> "You"
                        else -> "User"
                    }
                    BringItemClaimUi(
                        itemKey = itemKey,
                        itemLabel = label,
                        claimedByUserId = userId,
                        claimedByName = userLabel
                    )
                }
                return Result.success(out)
            }
            val rows = supabase.from("event_bring_item_claims")
                .select {
                    filter { eq("event_id", eventId) }
                }
                .decodeList<EventBringItemClaimRow>()
            val out = rows.map { row ->
                val profile = getProfileCached(row.claimedBy)
                BringItemClaimUi(
                    itemKey = row.itemKey,
                    itemLabel = row.itemLabel,
                    claimedByUserId = row.claimedBy,
                    claimedByName = displayName(profile)
                )
            }
            Result.success(out)
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun setBringItemClaim(eventId: String, itemLabel: String, claim: Boolean): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val key = normalizeBringItemKey(itemLabel)
            if (key.isBlank()) return Result.failure(IllegalArgumentException("Invalid item"))
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.setBringClaim(eventId, key, if (claim) userId else null)
                return Result.success(Unit)
            }
            if (claim) {
                runCatching {
                    supabase.from("event_bring_item_claims").delete {
                        filter {
                            eq("event_id", eventId)
                            eq("item_key", key)
                        }
                    }
                }
                supabase.from("event_bring_item_claims").insert(
                    EventBringItemClaimInsert(
                        eventId = eventId,
                        itemKey = key,
                        itemLabel = itemLabel.trim(),
                        claimedBy = userId
                    )
                )
            } else {
                supabase.from("event_bring_item_claims").delete {
                    filter {
                        eq("event_id", eventId)
                        eq("item_key", key)
                        eq("claimed_by", userId)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUpcomingHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val now = OffsetDateTime.now()
            val events = SupabaseRequestGuard.run {
                supabase.from("app_events")
                    .select {
                        filter { eq("host_id", user.id) }
                        order(column = "starts_at", order = Order.ASCENDING)
                    }
                    .decodeList<EventRow>()
            }.filter { e ->
                isEventStillRsvpable(e.startsAt, e.endsAt, now)
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Nearest upcoming or in-progress event that the user is hosting or already joined/accepted. */
    suspend fun listUpcomingJoinedOrHostingEvents(limit: Int = 24): Result<List<EventRow>> {
        return try {
            val now = OffsetDateTime.now()
            val rows = listMyHostingAndAttendingEvents()
                .getOrDefault(emptyList())
                .map { it.event }
                .filter { isEventStillRsvpable(it.startsAt, it.endsAt, now) }
                .sortedBy { parseDate(it.startsAt) ?: OffsetDateTime.MAX }
                .take(limit)
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** All events you host plus accepted invites (excluding host duplicates), newest first. */
    suspend fun listMyHostingAndAttendingEvents(): Result<List<MyEventHubItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val hosted = supabase.from("app_events")
                .let {
                    SupabaseRequestGuard.run {
                        it.select {
                            filter { eq("host_id", user.id) }
                            order(column = "starts_at", order = Order.DESCENDING)
                        }.decodeList<EventRow>()
                    }
                }
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
            val visibleHosted = hosted.filterNot { it.hostId in blockedIds }

            val acceptedInvites = SupabaseRequestGuard.run {
                supabase.from("event_invites")
                    .select {
                        filter {
                            eq("invitee_id", user.id)
                            eq("status", "accepted")
                        }
                    }
                    .decodeList<EventInviteRow>()
                    .map { it.eventId }
            }

            val activeMemberEventIds = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("event_members")
                        .select {
                            filter {
                                eq("user_id", user.id)
                                eq("status", "active")
                            }
                        }
                        .decodeList<EventMemberRow>()
                        .map { it.eventId }
                }
            }.getOrDefault(emptyList())

            val attendingEventIds = (acceptedInvites + activeMemberEventIds).distinct()

            val hostedIds = hosted.map { it.id }.toSet()
            val attendingOnly = getEventsByIds(attendingEventIds)
                .filter { it.id !in hostedIds }
                .filterNot { it.hostId in blockedIds }

            val items = visibleHosted.map { MyEventHubItem(event = it, isHosting = true) } +
                attendingOnly.map { MyEventHubItem(event = it, isHosting = false) }
            val sorted = items.sortedByDescending { parseDate(it.event.startsAt) ?: OffsetDateTime.MIN }
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Events listed in [public_event_invites], excluding ones you host (you already see those). */
    suspend fun listPublicDiscoverableEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = SupabaseRequestGuard.run {
                supabase.from("public_event_invites").select { }.decodeList<PublicEventInviteRow>()
            }
            if (rows.isEmpty()) return Result.success(emptyList())
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
            val now = OffsetDateTime.now()
            val events = getEventsByIds(rows.map { it.eventId })
                .filter { it.hostId != user.id }
                .filterNot { it.hostId in blockedIds }
                .filter { it.visibility != "invite_only" }
                .filter { isEventStillRsvpable(it.startsAt, it.endsAt, now) }
                .distinctBy { it.id }
                .sortedBy { parseDate(it.startsAt) ?: OffsetDateTime.MAX }
            Result.success(events)
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    /** Home feed: your hosted/attending events + pending invites + public events, newest first. */
    suspend fun listHomeFeedEvents(limit: Int = 24): Result<List<EventRow>> {
        return try {
            val myEvents = listMyHostingAndAttendingEvents().getOrDefault(emptyList()).map { it.event }
            val pendingIds = listPendingInvites().getOrDefault(emptyList()).map { it.eventId }.distinct()
            val pendingEvents = getEventsByIds(pendingIds)
            val publicEvents = listPublicDiscoverableEvents().getOrDefault(emptyList())

            val merged = (myEvents + pendingEvents + publicEvents)
                .distinctBy { it.id }
                .sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN }
                .take(limit)
            Result.success(merged)
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }
}
