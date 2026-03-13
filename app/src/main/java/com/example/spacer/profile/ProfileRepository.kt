package com.example.spacer.profile

import com.example.spacer.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.spacer.events.NotificationsRepository
import com.example.spacer.network.SupabaseManager
import com.example.spacer.network.SupabaseRequestGuard
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Serializable
data class ProfileRow(
    val id: String,
    val email: String? = null,
    val username: String? = null,
    /** Display name in DB column `name` (legacy docs used `full_name`). */
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("about_me")
    val aboutMe: String? = null,
    @SerialName("presence_status")
    val presenceStatus: String? = null
)

@Serializable
data class UserStatsRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("hosted_count")
    val hostedCount: Int = 0,
    @SerialName("attended_count")
    val attendedCount: Int = 0,
    @SerialName("friends_count")
    val friendsCount: Int = 0
)

data class ProfileSnapshot(
    val profile: ProfileRow,
    val stats: UserStatsRow
)

@Serializable
data class SearchUserRow(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("presence_status")
    val presenceStatus: String? = null
)

@Serializable
data class EventRow(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("host_id")
    val hostId: String,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val location: String? = null,
    @SerialName("bring_items")
    val bringItems: String? = null,
    @SerialName("max_attendees")
    val maxAttendees: Int? = null,
    /** [public] listed in discovery; [invite_only] friends / invited only. */
    val visibility: String? = null,
    val category: String? = null
)

@Serializable
private data class EventInviteAcceptedRow(
    @SerialName("event_id")
    val eventId: String,
    val status: String = "accepted"
)

@Serializable
private data class UserInviteStatusRow(
    @SerialName("event_id")
    val eventId: String,
    val status: String
)

@Serializable
private data class EventMemberStatusRow(
    @SerialName("event_id")
    val eventId: String,
    val status: String
)

enum class PastEventStatus { Attended, Declined, Missed, Other }

data class PastEventItem(
    val event: EventRow,
    val status: PastEventStatus
)

data class FriendListItem(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?,
    val presenceStatus: String? = "offline"
)

data class IncomingFriendRequestItem(
    val senderId: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?
)

data class BlockedUserItem(
    val userId: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?,
    val presenceStatus: String? = "offline"
)

enum class FriendshipState {
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    ACCEPTED
}

data class PublicProfileSnapshot(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?,
    val presenceStatus: String? = "offline",
    val aboutMe: String,
    val hostedEvents: List<EventRow>,
    val attendedEvents: List<EventRow>,
    val friends: List<FriendListItem>
)

fun ProfileRow.displayName(fallback: String = "User"): String {
    return fullName?.ifBlank { username ?: fallback } ?: (username ?: fallback)
}

fun SearchUserRow.displayName(fallback: String = "User"): String {
    return fullName?.ifBlank { username ?: fallback } ?: (username ?: fallback)
}

@Serializable
private data class FriendRequestInsert(
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val status: String = "pending"
)

@Serializable
private data class UserBlockInsert(
    @SerialName("blocker_id")
    val blockerId: String,
    @SerialName("blocked_id")
    val blockedId: String
)

@Serializable
private data class UserReportInsert(
    @SerialName("reporter_id")
    val reporterId: String,
    @SerialName("reported_id")
    val reportedId: String,
    val reason: String
)

@Serializable
private data class AccountDeletionRequestInsert(
    @SerialName("user_id")
    val userId: String,
    val reason: String? = null
)

/** In-app sample people so Find people works before extra Supabase profiles exist. */
private object FindPeopleDemoDirectory {
    private val rows: List<SearchUserRow> = listOf(
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111101",
            username = "alex_spacer",
            email = "alex.demo@spacer.app",
            fullName = "Alex Demo",
            avatarUrl = null
        ),
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111102",
            username = "sam_planner",
            email = "sam.demo@spacer.app",
            fullName = "Sam Planner",
            avatarUrl = null
        ),
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111103",
            username = "river_hosts",
            email = "river.demo@spacer.app",
            fullName = "River Chen",
            avatarUrl = null
        )
    )

    fun matches(safeQuery: String): List<SearchUserRow> {
        if (safeQuery.isBlank()) return emptyList()
        val q = safeQuery.lowercase()
        return rows.filter { r ->
            listOf(r.username, r.fullName, r.email)
                .mapNotNull { it?.lowercase() }
                .any { it.contains(q) }
        }
    }

    fun isDemoId(id: String): Boolean = rows.any { it.id == id }
}

private object DemoProfileDetailsDirectory {
    private data class DemoProfileDetails(
        val id: String,
        val fullName: String,
        val username: String,
        val aboutMe: String,
        val hostedEvents: List<EventRow>,
        val attendedEvents: List<EventRow>,
        val friends: List<FriendListItem>
    )

    private fun eventDate(daysFromNow: Long, hour24: Int): String {
        return OffsetDateTime.now()
            .plusDays(daysFromNow)
            .withHour(hour24)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toString()
    }

    private val alexId = "11111111-1111-4111-8111-111111111101"
    private val samId = "11111111-1111-4111-8111-111111111102"
    private val riverId = "11111111-1111-4111-8111-111111111103"

    private val rows: Map<String, DemoProfileDetails> = listOf(
        DemoProfileDetails(
            id = alexId,
            fullName = "Alex Demo",
            username = "alex_spacer",
            aboutMe = "Coffee runs, rooftop hangs, and finding new spots around the city.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-alex-host-1",
                    title = "Sunset Rooftop Mixer",
                    description = "Casual rooftop meetup with music and mocktails.",
                    hostId = alexId,
                    startsAt = eventDate(2, 19),
                    endsAt = eventDate(2, 21),
                    location = "230 Fifth Rooftop Bar, New York, NY",
                    visibility = "public",
                    category = "Social"
                ),
                EventRow(
                    id = "demo-alex-host-2",
                    title = "Sunday Brunch Club",
                    description = "Easy brunch and a walk after.",
                    hostId = alexId,
                    startsAt = eventDate(-6, 11),
                    endsAt = eventDate(-6, 13),
                    location = "Buvette, West Village, New York, NY",
                    visibility = "public",
                    category = "Food"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-alex-attend-1",
                    title = "Board Game Night",
                    hostId = samId,
                    startsAt = eventDate(-3, 18),
                    endsAt = eventDate(-3, 21),
                    location = "The Uncommons, Manhattan, NY",
                    visibility = "public",
                    category = "Games"
                ),
                EventRow(
                    id = "demo-alex-attend-2",
                    title = "Live Jazz Hang",
                    hostId = riverId,
                    startsAt = eventDate(-12, 20),
                    endsAt = eventDate(-12, 22),
                    location = "Blue Note Jazz Club, Greenwich Village, NY",
                    visibility = "public",
                    category = "Music"
                )
            ),
            friends = listOf(
                FriendListItem(id = samId, fullName = "Sam Planner", username = "sam_planner", avatarUrl = null),
                FriendListItem(id = riverId, fullName = "River Chen", username = "river_hosts", avatarUrl = null)
            )
        ),
        DemoProfileDetails(
            id = samId,
            fullName = "Sam Planner",
            username = "sam_planner",
            aboutMe = "I organize chill group plans and game nights.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-sam-host-1",
                    title = "Board Game Night",
                    description = "Beginner-friendly games and snacks.",
                    hostId = samId,
                    startsAt = eventDate(4, 18),
                    endsAt = eventDate(4, 21),
                    location = "The Uncommons, Manhattan, NY",
                    visibility = "public",
                    category = "Games"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-sam-attend-1",
                    title = "Morning Run + Coffee",
                    hostId = riverId,
                    startsAt = eventDate(-4, 9),
                    endsAt = eventDate(-4, 11),
                    location = "Central Park - Columbus Circle, New York, NY",
                    visibility = "public",
                    category = "Fitness"
                )
            ),
            friends = listOf(
                FriendListItem(id = alexId, fullName = "Alex Demo", username = "alex_spacer", avatarUrl = null),
                FriendListItem(id = riverId, fullName = "River Chen", username = "river_hosts", avatarUrl = null)
            )
        ),
        DemoProfileDetails(
            id = riverId,
            fullName = "River Chen",
            username = "river_hosts",
            aboutMe = "Always down for live music, art walks, and weekend activities.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-river-host-1",
                    title = "Live Jazz Hang",
                    description = "Meetup before the show and table together.",
                    hostId = riverId,
                    startsAt = eventDate(3, 20),
                    endsAt = eventDate(3, 22),
                    location = "Blue Note Jazz Club, Greenwich Village, NY",
                    visibility = "public",
                    category = "Music"
                ),
                EventRow(
                    id = "demo-river-host-2",
                    title = "Gallery Walk",
                    description = "Art walk through current exhibits.",
                    hostId = riverId,
                    startsAt = eventDate(7, 14),
                    endsAt = eventDate(7, 17),
                    location = "The Metropolitan Museum of Art, New York, NY",
                    visibility = "public",
                    category = "Arts"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-river-attend-1",
                    title = "Food Hall Meetup",
                    hostId = alexId,
                    startsAt = eventDate(-8, 13),
                    endsAt = eventDate(-8, 15),
                    location = "Chelsea Market, New York, NY",
                    visibility = "public",
                    category = "Food"
                )
            ),
            friends = listOf(
                FriendListItem(id = alexId, fullName = "Alex Demo", username = "alex_spacer", avatarUrl = null),
                FriendListItem(id = samId, fullName = "Sam Planner", username = "sam_planner", avatarUrl = null)
            )
        )
    ).associateBy { it.id }

    fun isDemoId(id: String): Boolean = rows.containsKey(id)

    fun profileFor(id: String): PublicProfileSnapshot? {
        val row = rows[id] ?: return null
        return PublicProfileSnapshot(
            id = row.id,
            fullName = row.fullName,
            username = row.username,
            avatarUrl = null,
            aboutMe = row.aboutMe,
            hostedEvents = row.hostedEvents.sortedByDescending { it.startsAt },
            attendedEvents = row.attendedEvents.sortedByDescending { it.startsAt },
            friends = row.friends
        )
    }
}

class ProfileRepository {
    private val supabase = SupabaseManager.client
    private val notificationsRepo = NotificationsRepository()
    private suspend fun <T> guarded(block: suspend () -> T): T = SupabaseRequestGuard.run(block)

    companion object {
        fun isOfflineDemoProfile(_id: String): Boolean {
            return false
        }
    }

    private object Cache {
        private const val TTL_MS = 2 * 60 * 1000L
        private val friendsByUser = mutableMapOf<String, Pair<Long, List<FriendListItem>>>()
        private val publicProfilesByUser = mutableMapOf<String, Pair<Long, PublicProfileSnapshot>>()

        fun getFriends(userId: String): List<FriendListItem>? {
            val cached = friendsByUser[userId] ?: return null
            if (System.currentTimeMillis() - cached.first > TTL_MS) return null
            return cached.second
        }

        fun putFriends(userId: String, list: List<FriendListItem>) {
            friendsByUser[userId] = System.currentTimeMillis() to list
        }

        fun getPublicProfile(userId: String): PublicProfileSnapshot? {
            val cached = publicProfilesByUser[userId] ?: return null
            if (System.currentTimeMillis() - cached.first > TTL_MS) return null
            return cached.second
        }

        fun putPublicProfile(userId: String, snapshot: PublicProfileSnapshot) {
            publicProfilesByUser[userId] = System.currentTimeMillis() to snapshot
        }

        fun clearFriendsFor(userId: String) {
            friendsByUser.remove(userId)
        }

        fun clearPublicProfileFor(userId: String) {
            publicProfilesByUser.remove(userId)
        }
    }

    @Serializable
    private data class UserBlockRow(
        @SerialName("blocker_id")
        val blockerId: String,
        @SerialName("blocked_id")
        val blockedId: String
    )

    private suspend fun blockedUserIdsFor(userId: String): Set<String> {
        val blockedByMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocker_id", userId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockedId }
        }.getOrDefault(emptyList())
        val blockedMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocked_id", userId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockerId }
        }.getOrDefault(emptyList())
        return (blockedByMe + blockedMe).toSet()
    }

    private suspend fun isBlockedRelationship(userId: String, otherUserId: String): Boolean {
        val blocksByMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter {
                        eq("blocker_id", userId)
                        eq("blocked_id", otherUserId)
                    }
                    limit(1)
                }
                .decodeList<UserBlockRow>()
        }.getOrDefault(emptyList())
        if (blocksByMe.isNotEmpty()) return true
        val blocksMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter {
                        eq("blocker_id", otherUserId)
                        eq("blocked_id", userId)
                    }
                    limit(1)
                }
                .decodeList<UserBlockRow>()
        }.getOrDefault(emptyList())
        return blocksMe.isNotEmpty()
    }

    private fun parseDateSafely(value: String): OffsetDateTime? {
        return try {
            OffsetDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private suspend fun pastHostedEventRows(hostUserId: String): List<EventRow> {
        val now = OffsetDateTime.now()
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
                order(column = "starts_at", order = Order.DESCENDING)
            }
            .decodeList<EventRow>()
            .filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }
    }

    private suspend fun allHostedEventRows(hostUserId: String): List<EventRow> {
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
                order(column = "starts_at", order = Order.DESCENDING)
            }
            .decodeList<EventRow>()
    }

    private suspend fun pastAcceptedAttendedEventRows(inviteeUserId: String): List<EventRow> {
        val now = OffsetDateTime.now()
        val invites = supabase.from("event_invites")
            .select {
                filter {
                    eq("invitee_id", inviteeUserId)
                    eq("status", "accepted")
                }
            }
            .decodeList<EventInviteAcceptedRow>()
        return invites.mapNotNull { inv ->
            runCatching {
                supabase.from("app_events")
                    .select {
                        filter { eq("id", inv.eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
            }.getOrNull()
        }
            .filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }
            .sortedByDescending { parseDateSafely(it.startsAt) }
    }

    private suspend fun countAcceptedFriends(userId: String): Int {
        val outgoing = supabase.from("friend_requests")
            .select {
                filter {
                    eq("sender_id", userId)
                    eq("status", "accepted")
                }
            }
            .decodeList<FriendRequestInsert>()
            .map { it.receiverId }
        val incoming = supabase.from("friend_requests")
            .select {
                filter {
                    eq("receiver_id", userId)
                    eq("status", "accepted")
                }
            }
            .decodeList<FriendRequestInsert>()
            .map { it.senderId }
        return (outgoing + incoming).distinct().size
    }

    private suspend fun countAllHostedEvents(hostUserId: String): Int {
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
            }
            .decodeList<EventRow>()
            .size
    }

    /**
     * Count of past events the user actually attended.
     *
     * Mirrors [getPastInvitedEventsWithStatus] so the "Attended" tile on the
     * profile matches the attended-events list. An event counts when the user
     * either had an accepted invite, or was an active member of a public
     * event without an invite — and the event has already ended.
     */
    private suspend fun countAllAcceptedInvites(inviteeUserId: String): Int {
        val now = OffsetDateTime.now()

        val acceptedInviteEventIds = runCatching {
            supabase.from("event_invites")
                .select {
                    filter {
                        eq("invitee_id", inviteeUserId)
                        eq("status", "accepted")
                    }
                }
                .decodeList<EventInviteAcceptedRow>()
                .map { it.eventId }
        }.getOrDefault(emptyList())

        val memberEventIds = runCatching {
            supabase.from("event_members")
                .select {
                    filter {
                        eq("user_id", inviteeUserId)
                        eq("status", "active")
                    }
                }
                .decodeList<EventMemberStatusRow>()
                .map { it.eventId }
        }.getOrDefault(emptyList())

        val attendedIds = (acceptedInviteEventIds + memberEventIds).distinct()
        if (attendedIds.isEmpty()) return 0

        val events = runCatching {
            supabase.from("app_events")
                .select {
                    filter { isIn("id", attendedIds) }
                }
                .decodeList<EventRow>()
        }.getOrDefault(emptyList())

        return events.count { event ->
            isEventInPast(event.startsAt, event.endsAt, now) == true
        }
    }

    private fun mergeProfileWithAuthSession(profile: ProfileRow, user: io.github.jan.supabase.auth.user.UserInfo): ProfileRow {
        val metaFull = user.userMetadata
            ?.get("full_name")
            ?.toString()
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
        val metaName = user.userMetadata
            ?.get("name")
            ?.toString()
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
        val email = profile.email?.takeIf { it.isNotBlank() } ?: user.email
        val username = profile.username?.takeIf { it.isNotBlank() } ?: metaName
        val fullName = profile.fullName?.takeIf { it.isNotBlank() } ?: metaFull
        return profile.copy(email = email, username = username, fullName = fullName)
    }

    suspend fun load(): Result<ProfileSnapshot> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val profileRow = guarded {
                supabase.from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
            }

            val profile = mergeProfileWithAuthSession(profileRow, user)

            val stats = UserStatsRow(
                userId = user.id,
                hostedCount = countAllHostedEvents(user.id),
                attendedCount = countAllAcceptedInvites(user.id),
                friendsCount = countAcceptedFriends(user.id)
            )

            Result.success(ProfileSnapshot(profile = profile, stats = stats))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fullName: String, aboutMe: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            ensureProfileRowExists(user)
            SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .update(
                        {
                            set("name", fullName)
                            set("about_me", aboutMe)
                        }
                    ) {
                        filter { eq("id", user.id) }
                    }
            }
            Cache.clearPublicProfileFor(user.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAvatarUrl(avatarUrl: String?): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            ensureProfileRowExists(user)
            val cleanedUrl = avatarUrl?.trim()?.ifBlank { null }
            SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .update(
                        {
                            set("avatar_url", cleanedUrl)
                        }
                    ) {
                        filter { eq("id", user.id) }
                    }
            }
            // Read back so a silent UPDATE-no-rows-affected surfaces as a clear error
            // instead of a fake success.
            val confirmed = runCatching {
                SupabaseRequestGuard.run {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", user.id) }
                            limit(1)
                        }
                        .decodeList<ProfileRow>()
                        .firstOrNull()
                        ?.avatarUrl
                }
            }.getOrNull()
            if ((confirmed?.trim()?.ifBlank { null }) != cleanedUrl) {
                return Result.failure(IllegalStateException("Couldn't save your photo. Please try again."))
            }
            Cache.clearPublicProfileFor(user.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileAvatarJpeg(imageBytes: ByteArray): Result<String> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (imageBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Image data is empty"))
            }
            val objectPath = "avatars/${user.id}.jpg"
            supabase.storage.from("Profile_photos").upload(
                path = objectPath,
                data = imageBytes
            ) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/Profile_photos/$objectPath"
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePresenceStatus(status: PresenceStatus): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Sign in to update your status."))
            // OAuth users may not have a profiles row yet; without one, UPDATE
            // silently affects 0 rows. Bootstrap the row first so the UPDATE lands.
            ensureProfileRowExists(user)
            com.example.spacer.network.SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .update(
                        {
                            set("presence_status", status.dbValue)
                            set("presence_updated_at", OffsetDateTime.now().toString())
                        }
                    ) {
                        filter { eq("id", user.id) }
                    }
            }
            // Read back so we surface a clear error if the write didn't apply.
            val confirmed = runCatching {
                com.example.spacer.network.SupabaseRequestGuard.run {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", user.id) }
                            limit(1)
                        }
                        .decodeList<ProfileRow>()
                        .firstOrNull()
                        ?.presenceStatus
                }
            }.getOrNull()
            if (confirmed != status.dbValue) {
                return Result.failure(IllegalStateException("Couldn't save your status. Please try again."))
            }
            Cache.clearPublicProfileFor(user.id)
            Cache.clearFriendsFor(user.id)
            Result.success(Unit)
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Couldn't save your status. Please try again."))
        }
    }

    /**
     * Ensure a profiles row exists for the current auth user. Required because
     * OAuth sign-in doesn't always auto-create one and `profiles` has NOT NULL
     * `username`/`email` columns, so UPDATE on a missing row silently no-ops.
     */
    private suspend fun ensureProfileRowExists(user: io.github.jan.supabase.auth.user.UserInfo) {
        val exists = runCatching {
            com.example.spacer.network.SupabaseRequestGuard.run {
                supabase.from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                        limit(1)
                    }
                    .decodeList<ProfileRow>()
                    .isNotEmpty()
            }
        }.getOrDefault(false)
        if (exists) return
        val email = user.email?.trim().orEmpty()
        val rawMeta = user.userMetadata
        val metaName = rawMeta?.get("full_name")?.toString()?.trim('"')?.trim().orEmpty()
            .ifEmpty { rawMeta?.get("name")?.toString()?.trim('"')?.trim().orEmpty() }
        val usernameBase = if (email.isNotEmpty()) email.substringBefore("@").lowercase()
        else user.id.take(8)
        val display = metaName.ifEmpty { usernameBase }
        val row = buildMap<String, Any?> {
            put("id", user.id)
            put("username", usernameBase)
            put("name", display)
            put("about_me", "")
            if (email.isNotEmpty()) put("email", email) else put("email", "$usernameBase@unknown.local")
        }
        runCatching {
            com.example.spacer.network.SupabaseRequestGuard.run {
                supabase.from("profiles").upsert(row)
            }
        }
    }

    private fun isNonFatalSocialMutation(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return m.contains("duplicate") ||
            m.contains("already exists") ||
            m.contains("conflict") ||
            m.contains("23505")
    }

    suspend fun searchUsers(query: String): Result<List<SearchUserRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val q = query.trim()
            if (q.isEmpty()) return Result.success(emptyList())
            if (q.length < 1) return Result.success(emptyList())

            val safe = q.replace("%", "").replace("_", "").trim()
            if (safe.isEmpty()) return Result.success(emptyList())
            val pattern = "%$safe%"

            // Separate queries avoid PostgREST `or` + `neq` edge cases and work better with RLS.
            val byUsername = guarded {
                supabase.from("profiles")
                    .select {
                        filter {
                            ilike("username", pattern)
                            neq("id", user.id)
                        }
                        limit(40)
                    }
                    .decodeList<SearchUserRow>()
            }

            val byName = guarded {
                supabase.from("profiles")
                    .select {
                        filter {
                            ilike("name", pattern)
                            neq("id", user.id)
                        }
                        limit(40)
                    }
                    .decodeList<SearchUserRow>()
            }

            val byEmail = runCatching {
                guarded {
                    supabase.from("profiles")
                        .select {
                            filter {
                                ilike("email", pattern)
                                neq("id", user.id)
                            }
                            limit(40)
                        }
                        .decodeList<SearchUserRow>()
                }
            }.getOrElse { emptyList() }

            var merged = (byUsername + byName + byEmail).distinctBy { it.id }

            if (merged.isEmpty() && safe.isNotEmpty()) {
                val broad = guarded {
                    supabase.from("profiles")
                        .select {
                            filter { neq("id", user.id) }
                            limit(200)
                        }
                        .decodeList<SearchUserRow>()
                }
                val needle = safe.lowercase()
                merged = broad.filter { row ->
                    listOf(row.username, row.fullName, row.email)
                        .mapNotNull { it?.lowercase() }
                        .joinToString(" ")
                        .contains(needle)
                }.take(40)
            }

            val out = merged.take(40)
            val blockedIds = blockedUserIdsFor(user.id)
            Result.success(out.filterNot { it.id in blockedIds })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't add yourself"))
            }

            val outgoing = runCatching {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("sender_id", user.id)
                            eq("receiver_id", targetUserId)
                        }
                        limit(1)
                    }
                    .decodeList<FriendRequestInsert>()
                    .firstOrNull()
            }.getOrNull()
            if (outgoing?.status == "accepted" || outgoing?.status == "pending") {
                return Result.success(Unit)
            }
            if (outgoing?.status == "declined") {
                supabase.from("friend_requests").update(
                    {
                        set("status", "pending")
                        set("responded_at", null as String?)
                    }
                ) {
                    filter {
                        eq("sender_id", user.id)
                        eq("receiver_id", targetUserId)
                    }
                }
                runCatching {
                    val sender = supabase.from("profiles")
                        .select {
                            filter { eq("id", user.id) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    val senderLabel = sender.fullName?.ifBlank { null } ?: sender.username ?: "Someone"
                    notificationsRepo.createForUser(
                        userId = targetUserId,
                        title = "New friend request",
                        body = "$senderLabel sent you a friend request.",
                        deepLink = NotificationsRepository.DeepLinks.socialRequests()
                    )
                }
                Cache.clearFriendsFor(user.id)
                Cache.clearPublicProfileFor(targetUserId)
                return Result.success(Unit)
            }

            val incoming = runCatching {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("sender_id", targetUserId)
                            eq("receiver_id", user.id)
                        }
                        limit(1)
                    }
                    .decodeList<FriendRequestInsert>()
                    .firstOrNull()
            }.getOrNull()

            if (incoming?.status == "accepted") {
                return Result.success(Unit)
            }
            if (incoming?.status == "pending") {
                supabase.from("friend_requests").update(
                    {
                        set("status", "accepted")
                    }
                ) {
                    filter {
                        eq("sender_id", targetUserId)
                        eq("receiver_id", user.id)
                    }
                }
                runCatching {
                    notificationsRepo.createForUser(
                        userId = targetUserId,
                        title = "Friend request accepted",
                        body = "Your friend request was accepted.",
                        deepLink = NotificationsRepository.DeepLinks.publicProfile(user.id)
                    )
                }
                Cache.clearFriendsFor(user.id)
                Cache.clearPublicProfileFor(targetUserId)
                return Result.success(Unit)
            }

            supabase.from("friend_requests").insert(
                FriendRequestInsert(
                    senderId = user.id,
                    receiverId = targetUserId
                )
            )
            runCatching {
                val sender = supabase.from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
                val senderLabel = sender.fullName?.ifBlank { null } ?: sender.username ?: "Someone"
                notificationsRepo.createForUser(
                    userId = targetUserId,
                    title = "New friend request",
                    body = "$senderLabel sent you a friend request.",
                    deepLink = NotificationsRepository.DeepLinks.socialRequests()
                )
            }
            Cache.clearFriendsFor(user.id)
            Cache.clearPublicProfileFor(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            if (isNonFatalSocialMutation(e.message)) Result.success(Unit) else Result.failure(e)
        }
    }

    suspend fun getFriendshipStates(targetUserIds: List<String>): Result<Map<String, FriendshipState>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserIds.isEmpty()) return Result.success(emptyMap())

            val normalized = targetUserIds.distinct().toSet()
            val outgoing = supabase.from("friend_requests")
                .select {
                    filter { eq("sender_id", user.id) }
                }
                .decodeList<FriendRequestInsert>()
                .filter { it.receiverId in normalized }

            val incoming = supabase.from("friend_requests")
                .select {
                    filter { eq("receiver_id", user.id) }
                }
                .decodeList<FriendRequestInsert>()
                .filter { it.senderId in normalized }

            val map = mutableMapOf<String, FriendshipState>()
            normalized.forEach { id -> map[id] = FriendshipState.NONE }
            val blockedIds = blockedUserIdsFor(user.id)
            outgoing.forEach {
                if (it.receiverId in blockedIds) return@forEach
                map[it.receiverId] = when (it.status) {
                    "accepted" -> FriendshipState.ACCEPTED
                    "pending" -> FriendshipState.OUTGOING_PENDING
                    else -> map[it.receiverId] ?: FriendshipState.NONE
                }
            }
            incoming.forEach {
                if (it.senderId in blockedIds) return@forEach
                map[it.senderId] = when (it.status) {
                    "accepted" -> FriendshipState.ACCEPTED
                    "pending" -> FriendshipState.INCOMING_PENDING
                    else -> map[it.senderId] ?: FriendshipState.NONE
                }
            }
            Result.success(map)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listIncomingFriendRequests(): Result<List<IncomingFriendRequestItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val incoming = supabase.from("friend_requests")
                .select {
                    filter {
                        eq("receiver_id", user.id)
                        eq("status", "pending")
                    }
                }
                .decodeList<FriendRequestInsert>()
            val blockedIds = blockedUserIdsFor(user.id)

            val rows = incoming.mapNotNull { req ->
                if (req.senderId in blockedIds) return@mapNotNull null
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", req.senderId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    IncomingFriendRequestItem(
                        senderId = req.senderId,
                        fullName = p.displayName(),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(senderId: String, accept: Boolean): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (accept) {
                supabase.from("friend_requests").update(
                    {
                        set("status", "accepted")
                    }
                ) {
                    filter {
                        eq("sender_id", senderId)
                        eq("receiver_id", user.id)
                        eq("status", "pending")
                    }
                }
                runCatching {
                    notificationsRepo.createForUser(
                        userId = senderId,
                        title = "Friend request accepted",
                        body = "Your friend request was accepted.",
                        deepLink = NotificationsRepository.DeepLinks.publicProfile(user.id)
                    )
                }
            } else {
                supabase.from("friend_requests").delete {
                    filter {
                        eq("sender_id", senderId)
                        eq("receiver_id", user.id)
                        eq("status", "pending")
                    }
                }
            }
            Cache.clearFriendsFor(user.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't block yourself"))
            }
            supabase.from("user_blocks").insert(
                UserBlockInsert(
                    blockerId = user.id,
                    blockedId = targetUserId
                )
            )
            // Ensure block immediately takes effect in friends/profile caches and remove relationship.
            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", user.id)
                    eq("receiver_id", targetUserId)
                }
            }
            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", targetUserId)
                    eq("receiver_id", user.id)
                }
            }
            Cache.clearFriendsFor(user.id)
            Cache.clearPublicProfileFor(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            if (isNonFatalSocialMutation(e.message)) Result.success(Unit) else Result.failure(e)
        }
    }

    suspend fun listBlockedUsers(): Result<List<BlockedUserItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val blocked = supabase.from("user_blocks")
                .select {
                    filter { eq("blocker_id", user.id) }
                }
                .decodeList<UserBlockRow>()
            val out = blocked.mapNotNull { row ->
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", row.blockedId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    BlockedUserItem(
                        userId = p.id,
                        fullName = p.displayName(),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl,
                        presenceStatus = p.presenceStatus
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("user_blocks").delete {
                filter {
                    eq("blocker_id", user.id)
                    eq("blocked_id", targetUserId)
                }
            }
            Cache.clearFriendsFor(user.id)
            Cache.clearPublicProfileFor(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(targetUserId: String, reason: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val cleanedReason = reason.trim()
            if (cleanedReason.length < 4) {
                return Result.failure(IllegalArgumentException("Please provide a report reason"))
            }
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't report yourself"))
            }
            supabase.from("user_reports").insert(
                UserReportInsert(
                    reporterId = user.id,
                    reportedId = targetUserId,
                    reason = cleanedReason
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            if (isNonFatalSocialMutation(e.message)) Result.success(Unit) else Result.failure(e)
        }
    }

    suspend fun requestAccountDeletion(reason: String?): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("account_deletion_requests").insert(
                AccountDeletionRequestInsert(
                    userId = user.id,
                    reason = reason?.trim()?.ifBlank { null }
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(pastHostedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Past and upcoming, newest first — used for profile hosted list with cancel on upcoming. */
    suspend fun getAllHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(allHostedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastAttendedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(pastAcceptedAttendedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Past events the user was invited to, paired with what actually happened.
     * Used by the attended-events screen to show an Attended/Declined/Missed badge.
     */
    suspend fun getPastInvitedEventsWithStatus(): Result<List<PastEventItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val now = OffsetDateTime.now()

            // All invites the user got, in any state.
            val invites = guarded {
                supabase.from("event_invites")
                    .select {
                        filter { eq("invitee_id", user.id) }
                    }
                    .decodeList<UserInviteStatusRow>()
            }
            // Public events the user joined (member rows without invites).
            val members = runCatching {
                guarded {
                    supabase.from("event_members")
                        .select {
                            filter {
                                eq("user_id", user.id)
                                eq("status", "active")
                            }
                        }
                        .decodeList<EventMemberStatusRow>()
                }
            }.getOrDefault(emptyList())

            val invitedById = invites.associateBy { it.eventId }
            val memberOnlyIds = members.map { it.eventId }.filter { it !in invitedById.keys }
            val allEventIds = (invites.map { it.eventId } + memberOnlyIds).distinct()
            if (allEventIds.isEmpty()) return Result.success(emptyList())

            val events = guarded {
                supabase.from("app_events")
                    .select {
                        filter { isIn("id", allEventIds) }
                    }
                    .decodeList<EventRow>()
            }

            val items = events.mapNotNull { event ->
                val ended = isEventInPast(event.startsAt, event.endsAt, now) ?: return@mapNotNull null
                if (!ended) return@mapNotNull null
                val invite = invitedById[event.id]
                val status = when {
                    invite?.status == "declined" -> PastEventStatus.Declined
                    invite?.status == "accepted" -> PastEventStatus.Attended
                    invite == null && event.id in memberOnlyIds -> PastEventStatus.Attended
                    invite?.status == "pending" -> PastEventStatus.Missed
                    else -> PastEventStatus.Other
                }
                PastEventItem(event = event, status = status)
            }.sortedByDescending { parseDateSafely(it.event.startsAt) ?: OffsetDateTime.MIN }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isEventInPast(startsAt: String, endsAt: String?, now: OffsetDateTime): Boolean? {
        val end = endsAt?.takeIf { it.isNotBlank() }?.let { parseDateSafely(it) }
        if (end != null) return end.isBefore(now)
        val start = parseDateSafely(startsAt) ?: return null
        return start.isBefore(now)
    }

    suspend fun getFriends(): Result<List<FriendListItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Cache.getFriends(user.id)?.let { cached ->
                return Result.success(cached)
            }

            val outgoing = guarded {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("sender_id", user.id)
                            eq("status", "accepted")
                        }
                    }
                    .decodeList<FriendRequestInsert>()
                    .map { it.receiverId }
            }

            val incoming = guarded {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("receiver_id", user.id)
                            eq("status", "accepted")
                        }
                    }
                    .decodeList<FriendRequestInsert>()
                    .map { it.senderId }
            }

            val friendIds = (outgoing + incoming).distinct()
            val blockedIds = blockedUserIdsFor(user.id)
            val friends = friendIds.mapNotNull { id ->
                if (id in blockedIds) return@mapNotNull null
                runCatching {
                    val p = guarded {
                        supabase.from("profiles")
                            .select {
                                filter { eq("id", id) }
                                limit(1)
                            }
                            .decodeSingle<ProfileRow>()
                    }
                    FriendListItem(
                        id = p.id,
                        fullName = p.displayName(),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl,
                        presenceStatus = p.presenceStatus
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }

            Cache.putFriends(user.id, friends)
            Result.success(friends)
        } catch (e: Exception) {
            val userId = supabase.auth.currentUserOrNull()?.id
            val cached = userId?.let { Cache.getFriends(it) }
            if (cached != null) Result.success(cached) else Result.failure(e)
        }
    }

    suspend fun getPublicProfile(userId: String): Result<PublicProfileSnapshot> {
        return try {
            Cache.getPublicProfile(userId)?.let { cached ->
                return Result.success(cached)
            }
            val viewer = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (isBlockedRelationship(viewer.id, userId)) {
                return Result.failure(IllegalStateException("Profile unavailable."))
            }
            val p = guarded {
                supabase.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
            }

            val hosted = guarded {
                supabase.from("app_events")
                    .select {
                        filter { eq("host_id", userId) }
                        order(column = "starts_at", order = Order.DESCENDING)
                        limit(8)
                    }
                    .decodeList<EventRow>()
                    .filter { it.visibility != "invite_only" }
            }

            val invites = runCatching {
                guarded {
                    supabase.from("event_invites")
                        .select {
                            filter {
                                eq("invitee_id", userId)
                                eq("status", "accepted")
                            }
                            limit(20)
                        }
                        .decodeList<EventInviteAcceptedRow>()
                }
            }.getOrDefault(emptyList())

            val attended = invites.mapNotNull { inv ->
                runCatching {
                    guarded {
                        supabase.from("app_events")
                            .select {
                                filter { eq("id", inv.eventId) }
                                limit(1)
                            }
                            .decodeSingle<EventRow>()
                    }
                }.getOrNull()
            }.sortedByDescending { it.startsAt }

            val outgoing = runCatching {
                guarded {
                    supabase.from("friend_requests")
                        .select {
                            filter {
                                eq("sender_id", userId)
                                eq("status", "accepted")
                            }
                        }
                        .decodeList<FriendRequestInsert>()
                        .map { it.receiverId }
                }
            }.getOrDefault(emptyList())
            val incoming = runCatching {
                guarded {
                    supabase.from("friend_requests")
                        .select {
                            filter {
                                eq("receiver_id", userId)
                                eq("status", "accepted")
                            }
                        }
                        .decodeList<FriendRequestInsert>()
                        .map { it.senderId }
                }
            }.getOrDefault(emptyList())

            val friends = (outgoing + incoming).distinct().mapNotNull { id ->
                runCatching {
                    val f = guarded {
                        supabase.from("profiles")
                            .select {
                                filter { eq("id", id) }
                                limit(1)
                            }
                            .decodeSingle<ProfileRow>()
                    }
                    FriendListItem(
                        id = f.id,
                        fullName = f.displayName(),
                        username = f.username ?: "user",
                        avatarUrl = f.avatarUrl,
                        presenceStatus = f.presenceStatus
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }

            val snapshot = PublicProfileSnapshot(
                id = p.id,
                fullName = p.displayName(),
                username = p.username ?: "user",
                avatarUrl = p.avatarUrl,
                presenceStatus = p.presenceStatus,
                aboutMe = p.aboutMe?.ifBlank { "No bio yet." } ?: "No bio yet.",
                hostedEvents = hosted,
                attendedEvents = attended,
                friends = friends
            )
            Cache.putPublicProfile(userId, snapshot)
            Result.success(snapshot)
        } catch (e: Exception) {
            val cached = Cache.getPublicProfile(userId)
            if (cached != null) Result.success(cached) else Result.failure(e)
        }
    }

    suspend fun unfriend(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", user.id)
                    eq("receiver_id", targetUserId)
                }
            }

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", targetUserId)
                    eq("receiver_id", user.id)
                }
            }
            Cache.clearFriendsFor(user.id)
            Cache.clearPublicProfileFor(targetUserId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

