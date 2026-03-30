package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.example.spacer.Navigation.AppRoutes
import com.example.spacer.location.PlacesRepository
import com.example.spacer.network.SessionPrefs
import com.example.spacer.profile.EventRow
import com.example.spacer.social.FindPeopleScreen
import com.example.spacer.ui.theme.SpacerCard
import com.example.spacer.ui.theme.SpacerChip
import com.example.spacer.ui.theme.SpacerChipSize
import com.example.spacer.ui.theme.SpacerChipTone
import com.example.spacer.ui.theme.SpacerScreenHeader
import com.example.spacer.ui.theme.SpacerSegRadio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val tabs = listOf("Invites & hosting", "Find people")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsHubScreen(
    innerEventsNav: NavHostController,
    outerNav: NavHostController,
    onOpenInvite: (String) -> Unit,
    onOpenHostEvent: (String) -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }
    val notificationsRepo = remember { NotificationsRepository() }
    val sessionPrefs = remember { SessionPrefs(context) }

    var pending by remember { mutableStateOf<List<PendingInviteUi>>(emptyList()) }
    var myEvents by remember { mutableStateOf<List<MyEventHubItem>>(emptyList()) }
    var publicEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var eventPhotoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    val innerRoute by innerEventsNav.currentBackStackEntryAsState()

    suspend fun loadHubLists() {
        loading = true
        withContext(Dispatchers.IO) {
            eventRepo.listPendingInvites()
        }.onSuccess {
            pending = it.filterNot { inv -> inv.eventId.startsWith("demo-") }
        }.onFailure {
            Toast.makeText(context, "We couldn't load your invitations right now. Please try again.", Toast.LENGTH_LONG).show()
            pending = emptyList()
        }
        withContext(Dispatchers.IO) {
            eventRepo.listMyHostingAndAttendingEvents()
        }.onSuccess {
            myEvents = it.filterNot { item -> item.event.id.startsWith("demo-") }
        }.onFailure {
            Toast.makeText(context, "We couldn't load your events right now. Please try again.", Toast.LENGTH_LONG).show()
            myEvents = emptyList()
        }
        withContext(Dispatchers.IO) {
            eventRepo.listPublicDiscoverableEvents()
        }.onSuccess {
            val stateCode = extractUsStateCode(sessionPrefs.getLocationLabel())
            val cleaned = it.filterNot { ev -> ev.id.startsWith("demo-") }
            publicEvents = if (stateCode == null) cleaned else cleaned.filter { ev ->
                val eventState = extractUsStateCode(ev.location)
                eventState == null || eventState == stateCode
            }
        }.onFailure {
            publicEvents = emptyList()
        }
        runCatching {
            val eventLocations = (myEvents.map { it.event.id to (it.event.location ?: "") } +
                pending.map { it.eventId to (it.location ?: "") } +
                publicEvents.map { it.id to (it.location ?: "") })
                .distinctBy { it.first }
                .filter { it.second.isNotBlank() }
            eventPhotoUrls = withContext(Dispatchers.IO) {
                if (!PlacesRepository.isApiKeyConfigured()) return@withContext emptyMap()
                eventLocations.mapNotNull { (eventId, location) ->
                    val photoName = runCatching {
                        placesRepo.searchText(location)
                            .getOrDefault(emptyList())
                            .firstOrNull()
                            ?.primaryPhotoName
                    }.getOrNull()
                    val url = photoName?.let { placesRepo.photoMediaUrl(it, 350) }
                    if (url != null) eventId to url else null
                }.toMap()
            }
        }
        runCatching {
            UserNotificationDispatcher.flushUnreadToPhone(context, notificationsRepo)
        }
        loading = false
    }

    LaunchedEffect(innerRoute?.destination?.route, tabIndex) {
        if (tabIndex == 0 && innerRoute?.destination?.route == "events_hub") {
            loadHubLists()
        }
    }

    DisposableEffect(outerNav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == AppRoutes.Events) {
                scope.launch { loadHubLists() }
            }
        }
        outerNav.addOnDestinationChangedListener(listener)
        onDispose { outerNav.removeOnDestinationChangedListener(listener) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val titleStart = if (tabIndex == 0) "Invites &" else "Find"
            val titleAccent = if (tabIndex == 0) "Hosting" else "people"
            val kicker = if (tabIndex == 0) "Your circle" else "Discover people"

            SpacerScreenHeader(
                kicker = kicker,
                title = titleStart,
                italic = titleAccent,
                paddingTop = 56.dp,
                paddingBottom = 12.dp
            )

            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                SpacerSegRadio(
                    options = tabs,
                    selected = tabIndex,
                    onSelected = { tabIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (tabIndex) {
                0 -> InvitesHostingTab(
                    loading = loading,
                    pending = pending,
                    myEvents = myEvents,
                    publicEvents = publicEvents,
                    eventPhotoUrls = eventPhotoUrls,
                    onOpenInvite = onOpenInvite,
                    onOpenHostEvent = onOpenHostEvent
                )
                1 -> FindPeopleScreen(
                    onOpenProfile = onOpenPublicProfile,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun extractUsStateCode(value: String?): String? {
    val raw = value?.uppercase(Locale.US) ?: return null
    val match = Regex("\\b([A-Z]{2})\\s+\\d{5}(?:-\\d{4})?\\b").find(raw)
    return match?.groupValues?.getOrNull(1)
}

@Composable
private fun InvitesHostingTab(
    loading: Boolean,
    pending: List<PendingInviteUi>,
    myEvents: List<MyEventHubItem>,
    publicEvents: List<EventRow>,
    eventPhotoUrls: Map<String, String>,
    onOpenInvite: (String) -> Unit,
    onOpenHostEvent: (String) -> Unit
) {
    val invitedOrJoinedIds: Set<String> = remember(myEvents, pending) {
        (myEvents.map { it.event.id } + pending.map { it.eventId }).toSet()
    }
    val visiblePublic: List<EventRow> = remember(publicEvents, invitedOrJoinedIds) {
        publicEvents.filterNot { it.id in invitedOrJoinedIds }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HubSubHeader(
                title = "Your events",
                sub = "Newest first · 6 shown"
            )
        }
        if (loading) {
            item { LoadingCard() }
        } else if (myEvents.isEmpty()) {
            item {
                EmptyCard(
                    text = "No personal events yet. Create one from the Create tab, accept an invite below, or browse public events."
                )
            }
        } else {
            items(myEvents.take(6), key = { "my-${it.event.id}" }) { item ->
                val ev = item.event
                // Past events (already ended) get a retrospective "Hosted" / "Attended"
                // badge; upcoming events keep the present-tense "Hosting" / "Going" badge.
                val ended = isEventPast(ev.startsAt, ev.endsAt)
                val rowRole = when {
                    ended && item.isHosting -> RowRole.Hosted
                    ended -> RowRole.Attended
                    item.isHosting -> RowRole.Hosting
                    else -> RowRole.Going
                }
                EventRowCard(
                    title = ev.title,
                    dateLine = formatEventDateNoTime(ev.startsAt),
                    location = ev.location,
                    imageUrl = eventPhotoUrls[ev.id],
                    role = rowRole,
                    countLine = null,
                    onClick = {
                        if (item.isHosting) onOpenHostEvent(ev.id)
                        else onOpenInvite(ev.id)
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HubSubHeader(
                title = "Pending invitations",
                sub = "When a host adds you, invitations appear here"
            )
        }
        if (!loading && pending.isEmpty()) {
            item { EmptyEnvelopeCard() }
        } else {
            items(pending, key = { "pending-${it.inviteId}" }) { inv ->
                EventRowCard(
                    title = inv.title,
                    dateLine = formatEventDateNoTime(inv.startsAt),
                    location = inv.location,
                    imageUrl = eventPhotoUrls[inv.eventId],
                    role = RowRole.Invited,
                    countLine = "From ${inv.hostDisplayName}",
                    onClick = { onOpenInvite(inv.eventId) }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HubSubHeader(
                title = "Public events",
                sub = "Discover open events nearby"
            )
        }
        if (!loading && visiblePublic.isEmpty()) {
            item {
                EmptyCard(
                    text = "No public listings yet. New events you create are registered here when the table exists."
                )
            }
        } else if (!loading) {
            items(visiblePublic.take(6), key = { "public-${it.id}" }) { ev ->
                EventRowCard(
                    title = ev.title,
                    dateLine = formatEventDateNoTime(ev.startsAt),
                    location = ev.location,
                    imageUrl = eventPhotoUrls[ev.id],
                    role = RowRole.Public,
                    countLine = null,
                    onClick = { onOpenInvite(ev.id) }
                )
            }
        }
    }
}

@Composable
private fun HubSubHeader(title: String, sub: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Text(
                text = sub,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

private enum class RowRole { Hosting, Going, Joined, Public, Invited, Hosted, Attended }

/**
 * True when the event has finished. Uses `endsAt` if present, otherwise treats
 * `startsAt` as the cutoff so past events without an end time still flip to "ended".
 */
private fun isEventPast(startsAt: String, endsAt: String?): Boolean {
    val now = java.time.OffsetDateTime.now()
    val end = endsAt?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() }
    if (end != null) return end.isBefore(now)
    val start = runCatching { java.time.OffsetDateTime.parse(startsAt) }.getOrNull() ?: return false
    return start.isBefore(now)
}

@Composable
private fun EventRowCard(
    title: String,
    dateLine: String,
    location: String?,
    imageUrl: String?,
    role: RowRole,
    countLine: String?,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        SpacerCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 14.dp,
            onClick = onClick
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EventThumb(imageUrl = imageUrl)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dateLine,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 12.5.sp
                    )
                    location?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoleChip(role)
                        if (!countLine.isNullOrBlank()) {
                            Text(
                                text = countLine,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChip(role: RowRole) {
    when (role) {
        RowRole.Hosting -> SpacerChip(text = "Hosting", tone = SpacerChipTone.Gold, size = SpacerChipSize.Sm)
        RowRole.Going -> SpacerChip(text = "Going", tone = SpacerChipTone.Purple, size = SpacerChipSize.Sm)
        RowRole.Joined -> SpacerChip(text = "Joined", tone = SpacerChipTone.Purple, size = SpacerChipSize.Sm)
        RowRole.Public -> SpacerChip(text = "Public", tone = SpacerChipTone.Success, size = SpacerChipSize.Sm)
        RowRole.Invited -> SpacerChip(text = "Invited", tone = SpacerChipTone.Gold, size = SpacerChipSize.Sm)
        RowRole.Hosted -> SpacerChip(text = "Hosted", tone = SpacerChipTone.Success, size = SpacerChipSize.Sm)
        RowRole.Attended -> SpacerChip(text = "Attended", tone = SpacerChipTone.Success, size = SpacerChipSize.Sm)
    }
}

@Composable
private fun EventThumb(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Event place image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("📍", fontSize = 22.sp)
        }
    }
}

@Composable
private fun LoadingCard() {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
            Text(
                text = "Loading…",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.5.sp
            )
        }
    }
}

@Composable
private fun EmptyEnvelopeCard() {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✉️", fontSize = 28.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No pending invites",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Once a host invites you, it'll show up here automatically.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
