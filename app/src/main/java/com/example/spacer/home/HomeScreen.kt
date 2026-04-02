package com.example.spacer.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.EventCategories
import com.example.spacer.events.ChatPresenceUi
import com.example.spacer.events.EventRepository
import com.example.spacer.events.formatEventDateNoTime
import com.example.spacer.calendar.DeviceCalendarBusyChecker
import com.example.spacer.location.PlacesRepository
import com.example.spacer.location.getLastKnownLatLng
import com.example.spacer.network.SessionPrefs
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.PresenceStatus
import com.example.spacer.profile.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onViewAllEvents: () -> Unit = {},
    onHostOwnEvent: () -> Unit = onViewAllEvents,
    onOpenEventChat: (String) -> Unit = { onViewAllEvents() },
    onOpenEventDetails: (EventRow) -> Unit = { onViewAllEvents() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }
    val profileRepo = remember { ProfileRepository() }

    var userName by remember { mutableStateOf("User") }
    var locationLabel by remember { mutableStateOf("Not set") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var upcomingEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var myUpcomingEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var eventPhotoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var homeEventsLoading by remember { mutableStateOf(true) }
    var myPresence by remember { mutableStateOf(PresenceStatus.OFFLINE) }
    var selectedPublicEvent by remember { mutableStateOf<EventRow?>(null) }
    var joinedEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var attendeeCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedEventParticipants by remember { mutableStateOf<List<ChatPresenceUi>>(emptyList()) }
    var liveGuests by remember { mutableStateOf<List<com.example.spacer.events.EventLiveGuestUi>>(emptyList()) }
    var liveExpanded by remember { mutableStateOf(false) }
    var hasArrived by remember { mutableStateOf(false) }
    var eventLocationSharingEnabled by remember { mutableStateOf(sessionPrefs.isEventLocationSharingEnabled()) }
    val currentUserId = remember { SupabaseManager.client.auth.currentUserOrNull()?.id }
    // Tick every 30s so the 2-hour live-tracking window opens / closes without
    // the user navigating away and back. Without this, `remember(...)` would
    // capture the boolean once at first composition and stay stuck.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowTick = System.currentTimeMillis()
        }
    }
    val nextUpAny = remember(myUpcomingEvents, upcomingEvents) {
        (myUpcomingEvents + upcomingEvents)
            .distinctBy { it.id }
            .sortedBy { runCatching { OffsetDateTime.parse(it.startsAt) }.getOrNull() ?: OffsetDateTime.MAX }
            .firstOrNull()
    }

    suspend fun loadDiscoverable() {
        homeEventsLoading = true
        val publicList = withContext(Dispatchers.IO) { eventRepo.listPublicDiscoverableEvents() }
            .getOrDefault(emptyList())
            .filter { event ->
                runCatching { OffsetDateTime.parse(event.startsAt) }.getOrNull()?.isAfter(OffsetDateTime.now()) == true
            }
        val joinedOrHosted = withContext(Dispatchers.IO) { eventRepo.listUpcomingJoinedOrHostingEvents(limit = 12) }
            .getOrDefault(emptyList())
        upcomingEvents = publicList
        myUpcomingEvents = joinedOrHosted
        joinedEventIds = joinedOrHosted.map { it.id }.toSet()
        // Real attendee counts per event (single batched query).
        val allIds = (publicList.map { it.id } + joinedOrHosted.map { it.id }).distinct()
        attendeeCounts = withContext(Dispatchers.IO) {
            eventRepo.attendeeCountsByEventId(allIds)
        }
        // Pull venue photos for both the public-discovery list AND the events the
        // user is hosting/attending so the "Up next" hero card on home actually
        // has an image (it almost always picks an event from joinedOrHosted).
        val locs = (publicList + joinedOrHosted)
            .distinctBy { it.id }
            .map { it.id to (it.location ?: "") }
            .filter { it.second.isNotBlank() }
        eventPhotoUrls = withContext(Dispatchers.IO) {
            if (!PlacesRepository.isApiKeyConfigured()) return@withContext emptyMap()
            locs.mapNotNull { (eventId, location) ->
                val photoName = placesRepo.searchText(location)
                    .getOrDefault(emptyList())
                    .firstOrNull()
                    ?.primaryPhotoName
                val url = photoName?.let { placesRepo.photoMediaUrl(it, 350) }
                if (url != null) eventId to url else null
            }.toMap()
        }
        homeEventsLoading = false
    }

    LaunchedEffect(Unit) {
        userName = sessionPrefs.getProfileName().ifBlank { "User" }
        locationLabel = sessionPrefs.getLocationLabel().ifBlank { "Not set" }
        profileImageUri = sessionPrefs.getProfileImageUri()
        eventLocationSharingEnabled = sessionPrefs.isEventLocationSharingEnabled()
        myPresence = PresenceStatus.fromDb(sessionPrefs.getPresenceStatus())
        val live = withContext(Dispatchers.IO) { profileRepo.load().getOrNull() }?.profile?.presenceStatus
        if (!live.isNullOrBlank()) {
            myPresence = PresenceStatus.fromDb(live)
            sessionPrefs.savePresenceStatus(myPresence.dbValue)
        }
        loadDiscoverable()
    }
    // Bucketed key: only re-key when the open/close transition flips OR when
    // the user toggles sharing. Avoids hammering the DB on every minute tick
    // while still picking up the window edge as soon as it crosses.
    val liveWindowOpen = remember(
        nextUpAny?.startsAt,
        nextUpAny?.endsAt,
        eventLocationSharingEnabled,
        nowTick
    ) {
        nextUpAny != null &&
            eventLocationSharingEnabled &&
            isWithinLiveTrackingWindow(nextUpAny.startsAt, nextUpAny.endsAt)
    }
    LaunchedEffect(nextUpAny?.id, liveWindowOpen) {
        val ev = nextUpAny ?: return@LaunchedEffect
        if (!liveWindowOpen) {
            liveGuests = emptyList()
            hasArrived = false
            return@LaunchedEffect
        }
        val eventId = ev.id
        liveGuests = withContext(Dispatchers.IO) { eventRepo.listEventLiveGuests(eventId).getOrDefault(emptyList()) }
        hasArrived = liveGuests.firstOrNull { it.userId == currentUserId }?.isArrived == true
        val point = getLastKnownLatLng(context)
        withContext(Dispatchers.IO) {
            eventRepo.updateEventLiveSharing(
                eventId = eventId,
                sharingEnabled = true,
                lat = point?.latitude,
                lng = point?.longitude
            )
        }
        liveGuests = withContext(Dispatchers.IO) { eventRepo.listEventLiveGuests(eventId).getOrDefault(emptyList()) }
    }
    LaunchedEffect(selectedPublicEvent?.id) {
        val eventId = selectedPublicEvent?.id
        if (eventId == null) {
            selectedEventParticipants = emptyList()
            return@LaunchedEffect
        }
        selectedEventParticipants = withContext(Dispatchers.IO) {
            eventRepo.listEventChatPresence(eventId).getOrDefault(emptyList())
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val label = resolveLocationLabel(context)
            locationLabel = label
            sessionPrefs.saveLocationLabel(label)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                userName = sessionPrefs.getProfileName().ifBlank { "User" }
                profileImageUri = sessionPrefs.getProfileImageUri()

                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val label = withContext(Dispatchers.IO) { resolveLocationLabel(context) }
                    locationLabel = label
                    sessionPrefs.saveLocationLabel(label)
                }
                loadDiscoverable()
                isRefreshing = false
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            HeaderRow(
                userName = userName,
                locationLabel = locationLabel,
                profileImageUri = profileImageUri,
                presenceStatus = myPresence
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "WHAT'S ON",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    ) { append("Tonight & ") }
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Light
                        )
                    ) { append("this week") }
                },
                fontSize = 32.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
            Spacer(modifier = Modifier.height(20.dp))

            val tagOptions = remember(upcomingEvents) {
                val fromDb = upcomingEvents.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()
                if (fromDb.isEmpty()) EventCategories.all else fromDb.sorted()
            }
            val filtered = remember(upcomingEvents, selectedCategory) {
                if (selectedCategory == null) upcomingEvents
                else upcomingEvents.filter { it.category == selectedCategory }
            }
            val userStateCode = remember(locationLabel) { extractUsStateCode(locationLabel) }
            val zoned = remember(filtered, searchQuery, userStateCode) {
                if (searchQuery.trim().isNotBlank() || userStateCode == null) {
                    filtered
                } else {
                    filtered.filter { ev ->
                        val eventState = extractUsStateCode(ev.location)
                        eventState == null || eventState == userStateCode
                    }
                }
            }
            val searched = remember(zoned, searchQuery) {
                val q = searchQuery.trim().lowercase()
                if (q.isBlank()) zoned
                else zoned.filter { ev ->
                    ev.title.lowercase().contains(q) ||
                        (ev.location?.lowercase()?.contains(q) == true) ||
                        (ev.category?.lowercase()?.contains(q) == true)
                }
            }
            val nearYouRows = remember(searched) { searched.take(4) }

            if (homeEventsLoading) {
                Text(
                    "Loading events…",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (nextUpAny == null) {
                Text(
                    "No upcoming events right now.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val showLiveMap = remember(
                    nextUpAny.startsAt,
                    nextUpAny.endsAt,
                    eventLocationSharingEnabled,
                    nowTick
                ) {
                    eventLocationSharingEnabled &&
                        isWithinLiveTrackingWindow(nextUpAny.startsAt, nextUpAny.endsAt)
                }
                if (showLiveMap) {
                    TrackingHeroCard(
                        event = nextUpAny,
                        guests = liveGuests,
                        currentUserId = currentUserId,
                        hasArrived = hasArrived,
                        locationSharingEnabled = eventLocationSharingEnabled,
                        onExpand = { liveExpanded = true },
                        onArrived = {
                            scope.launch {
                                val point = getLastKnownLatLng(context)
                                withContext(Dispatchers.IO) {
                                    eventRepo.markArrivedAtEvent(
                                        eventId = nextUpAny.id,
                                        lat = point?.latitude,
                                        lng = point?.longitude
                                    )
                                }
                                hasArrived = true
                                liveGuests = withContext(Dispatchers.IO) {
                                    eventRepo.listEventLiveGuests(nextUpAny.id).getOrDefault(emptyList())
                                }
                            }
                        },
                        onOpenChat = { onOpenEventChat(nextUpAny.id) },
                        onDetails = { onOpenEventDetails(nextUpAny) }
                    )
                } else {
                    UpNextSimpleCard(
                        event = nextUpAny,
                        imageUrl = eventPhotoUrls[nextUpAny.id],
                        liveTrackingDisabled = !eventLocationSharingEnabled,
                        onOpenChat = { onOpenEventChat(nextUpAny.id) },
                        onDetails = { onOpenEventDetails(nextUpAny) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Spacer(modifier = Modifier.height(10.dp))
            CategoryChipRow(
                tags = tagOptions,
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Near you", "View all", onAction = onViewAllEvents)
            Spacer(modifier = Modifier.height(10.dp))

            if (!homeEventsLoading && nearYouRows.isNotEmpty()) {
                nearYouRows.forEach { ev ->
                    DiscoverCategoryRow(
                        event = ev,
                        imageUrl = eventPhotoUrls[ev.id],
                        isJoined = ev.id in joinedEventIds,
                        attendeeCount = attendeeCounts[ev.id] ?: 0,
                        onOpenDetails = { selectedPublicEvent = ev }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HostOwnCard(onClick = onHostOwnEvent)
            Spacer(modifier = Modifier.height(28.dp))
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        selectedPublicEvent?.let { detailsEvent ->
            val alreadyJoined = detailsEvent.id in joinedEventIds
            AlertDialog(
                onDismissRequest = { selectedPublicEvent = null },
                title = { Text(detailsEvent.title) },
                text = {
                    val place = detailsEvent.location?.takeIf { it.isNotBlank() } ?: "Location TBD"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildString {
                                append(formatEventDateNoTime(detailsEvent.startsAt))
                                append("\n")
                                append(place)
                                detailsEvent.description?.takeIf { it.isNotBlank() }?.let {
                                    append("\n\n")
                                    append(it)
                                }
                                detailsEvent.maxAttendees?.let { cap ->
                                    append("\n\nCapacity: ")
                                    append(cap)
                                }
                            }
                        )
                        if (selectedEventParticipants.isNotEmpty()) {
                            Text(
                                "Joined attendees",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                selectedEventParticipants.take(8).forEach { p ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (p.avatarUrl.isNullOrBlank()) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(p.displayName.take(1), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        } else {
                                            AsyncImage(
                                                model = p.avatarUrl,
                                                contentDescription = "${p.displayName} avatar",
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(p.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!alreadyJoined) {
                        Button(onClick = {
                            scope.launch {
                                runCatching {
                                    val hasConflict = if (
                                        sessionPrefs.isDeviceCalendarReadEnabled() &&
                                        DeviceCalendarBusyChecker.hasReadCalendarPermission(context)
                                    ) {
                                        val window = DeviceCalendarBusyChecker.eventWindowMillis(
                                            detailsEvent.startsAt,
                                            detailsEvent.endsAt
                                        )
                                        if (window != null) {
                                            DeviceCalendarBusyChecker.eventOverlapsBusyTime(
                                                context = context,
                                                eventStartMillis = window.first,
                                                eventEndMillis = window.second
                                            )
                                        } else false
                                    } else false
                                    val joined = withContext(Dispatchers.IO) {
                                        eventRepo.joinPublicEvent(detailsEvent.id, calendarBusyConflict = hasConflict)
                                    }
                                    joined.onSuccess {
                                        sessionPrefs.incrementAttendedCount()
                                        val message = if (hasConflict) {
                                            "Joined. You and the host were notified about the time conflict."
                                        } else {
                                            "Joined event"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        loadDiscoverable()
                                        selectedPublicEvent = null
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            com.example.spacer.events.friendlyErrorMessage(
                                                it,
                                                fallback = "Couldn't join the event. Please try again."
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        com.example.spacer.events.friendlyErrorMessage(
                                            it,
                                            fallback = "Couldn't join the event. Please try again."
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }) { Text("Join") }
                    } else {
                        Button(onClick = { selectedPublicEvent = null }) { Text("Joined ✓") }
                    }
                },
                dismissButton = {
                    Button(onClick = { selectedPublicEvent = null }) { Text("Close") }
                }
            )
        }

        if (liveExpanded && nextUpAny != null) {
            LiveMapExpandedDialog(
                event = nextUpAny,
                guests = liveGuests,
                currentUserId = currentUserId,
                hasArrived = hasArrived,
                onDismiss = { liveExpanded = false },
                onArrived = {
                    scope.launch {
                        val point = getLastKnownLatLng(context)
                        withContext(Dispatchers.IO) {
                            eventRepo.markArrivedAtEvent(nextUpAny.id, point?.latitude, point?.longitude)
                        }
                        hasArrived = true
                        liveGuests = withContext(Dispatchers.IO) {
                            eventRepo.listEventLiveGuests(nextUpAny.id).getOrDefault(emptyList())
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderRow(
    userName: String,
    locationLabel: String,
    profileImageUri: String?,
    presenceStatus: PresenceStatus
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val avatarSize = when {
        screenWidth < 360 -> 40.dp
        screenWidth > 420 -> 48.dp
        else -> 44.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            if (profileImageUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(profileImageUri),
                    contentDescription = "User profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(avatarSize).clip(CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(presenceStatus.dotColor)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Hey,",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                userName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "LOCATION",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                locationLabel,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun StarFieldBackground() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val starBase = MaterialTheme.colorScheme.onBackground
    val stars = remember {
        List(100) {
            val x = Random(1234 + it).nextFloat()
            val y = Random(888 + it).nextFloat()
            val radius = 0.8f + Random(333 + it).nextFloat() * 1.2f
            val alpha = 0.2f + Random(222 + it).nextFloat() * 0.6f
            Star(x, y, radius, alpha)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val a = if (dark) star.alpha else star.alpha * 0.35f
            drawCircle(
                color = starBase.copy(alpha = a),
                radius = star.radius,
                center = androidx.compose.ui.geometry.Offset(
                    x = star.x * size.width,
                    y = star.y * size.height
                )
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search events, places, or tags…",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        },
        leadingIcon = { Text("🔍", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp
            )
        )
        Text(
            action,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clickable { onAction() }
        )
    }
}

@Composable
private fun CategoryChipRow(
    tags: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val allSelected = selected == null
        com.example.spacer.ui.theme.SpacerChip(
            text = "All",
            tone = if (allSelected) com.example.spacer.ui.theme.SpacerChipTone.PurpleSolid
            else com.example.spacer.ui.theme.SpacerChipTone.Default,
            size = com.example.spacer.ui.theme.SpacerChipSize.Lg,
            onClick = { onSelected(null) }
        )
        tags.forEach { tag ->
            val on = selected == tag
            com.example.spacer.ui.theme.SpacerChip(
                text = tag,
                tone = if (on) com.example.spacer.ui.theme.SpacerChipTone.PurpleSolid
                else com.example.spacer.ui.theme.SpacerChipTone.Default,
                size = com.example.spacer.ui.theme.SpacerChipSize.Lg,
                onClick = { onSelected(if (on) null else tag) }
            )
        }
    }
}

@Composable
private fun DiscoverEventCard(
    event: EventRow,
    imageUrl: String?,
    onJoin: () -> Unit
) {
    val place = event.location?.takeIf { it.isNotBlank() } ?: "Location TBD"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Event image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatEventDateNoTime(event.startsAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                place,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.category?.takeIf { it.isNotBlank() }?.let { cat ->
                Text(
                    cat,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("JOIN NOW")
            }
        }
    }
}

@Composable
private fun DiscoverCategoryRow(
    event: EventRow,
    imageUrl: String?,
    isJoined: Boolean,
    attendeeCount: Int,
    onOpenDetails: () -> Unit
) {
    val place = event.location?.takeIf { it.isNotBlank() }?.let { shortenAddressNoStateZip(it) } ?: "Location TBD"
    val start = runCatching { OffsetDateTime.parse(event.startsAt) }.getOrNull()
    val month = start?.month?.name?.take(3) ?: "TBD"
    val day = start?.dayOfMonth?.toString() ?: "--"
    val time = formatTime12Hour(event.startsAt)
    com.example.spacer.ui.theme.SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 14.dp,
        onClick = if (isJoined) null else onOpenDetails
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        month,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)
                    )
                    Text(
                        day,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        time,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    place,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isJoined) {
                        com.example.spacer.ui.theme.SpacerChip(
                            text = "Joined",
                            tone = com.example.spacer.ui.theme.SpacerChipTone.Purple,
                            size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                        )
                    } else {
                        com.example.spacer.ui.theme.SpacerChip(
                            text = "Public",
                            tone = com.example.spacer.ui.theme.SpacerChipTone.Success,
                            size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                        )
                    }
                    Text(
                        if (attendeeCount > 0) "$attendeeCount going" else "Be the first to join",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun TrackingHeroCard(
    event: EventRow,
    guests: List<com.example.spacer.events.EventLiveGuestUi>,
    currentUserId: String?,
    hasArrived: Boolean,
    locationSharingEnabled: Boolean,
    onExpand: () -> Unit,
    onArrived: () -> Unit,
    onOpenChat: () -> Unit,
    onDetails: () -> Unit
) {
    val visiblePins = guests.filter { it.sharingEnabled && it.lat != null && it.lng != null }
    val arrivedCount = guests.count { it.isArrived }
    val etaMinutes = guests.firstOrNull { it.userId == currentUserId && !it.isArrived }?.let {
        val fallback = ((it.userId.hashCode() and 0x7fffffff) % 11) + 3
        fallback
    } ?: 7
    val enRoutePercent = if (guests.isEmpty()) 0 else ((guests.count { !it.isArrived } * 100f) / guests.size).roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TONIGHT · LIVE",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp)
        )
        com.example.spacer.ui.theme.SpacerChip(
            text = timeUntilLabel(event.startsAt),
            tone = com.example.spacer.ui.theme.SpacerChipTone.Gold,
            size = com.example.spacer.ui.theme.SpacerChipSize.Sm
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    com.example.spacer.ui.theme.SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 14.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (hasArrived) "YOU'RE HERE" else event.title,
                        color = if (hasArrived) Color(0xFF9EF4B8) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        if (hasArrived) "$arrivedCount arrived" else "$enRoutePercent% en route · $arrivedCount arrived",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Details", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), modifier = Modifier.clickable { onDetails() })
                    Text("Expand", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onExpand() })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            StylizedMapPreview(guests = visiblePins, arrived = hasArrived, selfUserId = currentUserId)
            Spacer(modifier = Modifier.height(10.dp))
            AvatarStackRow(guests = guests, max = 5)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasArrived) "You're here"
                    else if (!locationSharingEnabled) "Sharing off"
                    else "Your ETA ${etaMinutes} min",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (hasArrived) {
                    com.example.spacer.ui.theme.SpacerButton(
                        label = "Open chat",
                        onClick = onOpenChat,
                        kind = com.example.spacer.ui.theme.SpacerButtonKind.Primary,
                        size = com.example.spacer.ui.theme.SpacerButtonSize.Md
                    )
                } else {
                    Button(
                        onClick = onArrived,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0A44A), contentColor = Color(0xFF1A1533))
                    ) { Text("I'm here") }
                }
            }
        }
    }
}

@Composable
private fun LiveMapExpandedDialog(
    event: EventRow,
    guests: List<com.example.spacer.events.EventLiveGuestUi>,
    currentUserId: String?,
    hasArrived: Boolean,
    onDismiss: () -> Unit,
    onArrived: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.example.spacer.tracking.LiveMapScreenForLiveData(
                eventTitle = event.title,
                guests = guests,
                currentUserId = currentUserId,
                arrived = hasArrived,
                onBack = onDismiss,
                onArrive = onArrived
            )
        }
    }
}

@Composable
private fun StylizedMapPreview(
    guests: List<com.example.spacer.events.EventLiveGuestUi>,
    arrived: Boolean,
    selfUserId: String? = null,
    modifier: Modifier = Modifier
) {
    val mapped = remember(guests, selfUserId, arrived) {
        com.example.spacer.tracking.trackedFromLive(guests, selfUserId, arrived)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        com.example.spacer.tracking.SpacerMap(
            modifier = Modifier.fillMaxSize(),
            guests = mapped,
            showRoute = !arrived,
            showSelf = true
        )
    }
}

@Composable
private fun AvatarStackRow(guests: List<com.example.spacer.events.EventLiveGuestUi>, max: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
        guests.take(max).forEach { guest ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(guest.displayName.take(1), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (guests.size > max) {
            Text("+${guests.size - max}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}

private fun timeUntilLabel(startsAt: String): String {
    val start = runCatching { OffsetDateTime.parse(startsAt) }.getOrNull() ?: return "Soon"
    val now = OffsetDateTime.now()
    val mins = java.time.Duration.between(now, start).toMinutes()
    if (mins <= 0) return "Started"
    if (mins < 60) return "In ${mins}m"
    val hours = mins / 60
    if (hours < 24) return "In ${hours}h"
    val days = hours / 24
    return "In ${days}d"
}

private fun distanceFallbackLabel(seed: String): String {
    val miles = (((seed.hashCode() and 0x7fffffff) % 14) + 1) / 10.0
    return String.format(Locale.US, "%.1f mi", miles)
}

@Composable
private fun HostOwnCard(onClick: () -> Unit) {
    com.example.spacer.ui.theme.SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 16.dp,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Host your own",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Plan something with friends",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun resolveLocationLabel(context: Context): String {
    return try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val location = pickBestLocation(gpsLocation, networkLocation) ?: return "Location unavailable"

        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val first = addresses?.firstOrNull()
        val city = first?.locality ?: first?.subAdminArea ?: "Unknown city"
        val state = first?.adminArea?.takeIf { it.isNotBlank() } ?: ""
        val code = first?.postalCode ?: "Unknown ZIP"
        if (state.isNotBlank()) "$city, $state $code" else "$city, $code"
    } catch (_: Exception) {
        "Location unavailable"
    }
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}

private fun extractUsStateCode(value: String?): String? {
    val raw = value?.uppercase(Locale.US) ?: return null
    val match = Regex("\\b([A-Z]{2})\\s+\\d{5}(?:-\\d{4})?\\b").find(raw)
    return match?.groupValues?.getOrNull(1)
}

private fun formatTime12Hour(startsAt: String): String {
    val value = runCatching { OffsetDateTime.parse(startsAt) }.getOrNull() ?: return "--:--"
    return value.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}

private fun shortenAddressNoStateZip(full: String): String {
    val parts = full.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0]}, ${parts[1]}"
        parts.size == 1 -> parts[0]
        else -> full
    }
}

/**
 * True when the event is starting within ~2 hours or is currently happening.
 * Live tracking only activates inside this window so we don't burn battery on
 * events scheduled for next week.
 */
private fun isWithinLiveTrackingWindow(startsAt: String, endsAt: String?): Boolean {
    val now = OffsetDateTime.now()
    val start = runCatching { OffsetDateTime.parse(startsAt) }.getOrNull() ?: return false
    val end = endsAt?.takeIf { it.isNotBlank() }
        ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        ?: start.plusHours(2)
    if (now.isAfter(end)) return false
    val opensAt = start.minusHours(2)
    return !now.isBefore(opensAt)
}

@Composable
private fun UpNextSimpleCard(
    event: EventRow,
    imageUrl: String?,
    liveTrackingDisabled: Boolean,
    onOpenChat: () -> Unit,
    onDetails: () -> Unit
) {
    // Editorial header: small-caps kicker on the left, gold "in N min/h/d" chip on the right.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "UP NEXT FOR YOU",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp)
        )
        com.example.spacer.ui.theme.SpacerChip(
            text = timeUntilLabel(event.startsAt),
            tone = com.example.spacer.ui.theme.SpacerChipTone.Gold,
            size = com.example.spacer.ui.theme.SpacerChipSize.Sm
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    // Featured-card layout: full-bleed venue photo on top, padded content below.
    // Mirrors the design bundle's "Up next" pattern from screens-home.jsx.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // Venue hero. Falls back to a soft purple-tinted band when there's no photo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${event.title} venue",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                }
            }
            Column(modifier = Modifier.padding(18.dp)) {
                // Two-column body: editorial title + place on the left, date/time stack on the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.location?.takeIf { it.isNotBlank() } ?: "Location TBD",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatEventDateNoTime(event.startsAt).uppercase(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            formatTime12Hour(event.startsAt),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (liveTrackingDisabled) {
                        "Live tracking is off. Turn it on in Settings to share location during the event."
                    } else {
                        "The live map opens here within 2 hours of the event."
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.example.spacer.ui.theme.SpacerButton(
                        label = "Open chat",
                        onClick = onOpenChat,
                        kind = com.example.spacer.ui.theme.SpacerButtonKind.Primary,
                        size = com.example.spacer.ui.theme.SpacerButtonSize.Md,
                        modifier = Modifier.weight(1f)
                    )
                    com.example.spacer.ui.theme.SpacerButton(
                        label = "Details",
                        onClick = onDetails,
                        kind = com.example.spacer.ui.theme.SpacerButtonKind.Secondary,
                        size = com.example.spacer.ui.theme.SpacerButtonSize.Md
                    )
                }
            }
        }
    }
}
