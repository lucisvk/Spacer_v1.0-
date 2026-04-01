package com.example.spacer.events

import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.net.toUri
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spacer.network.SessionPrefs
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
fun InviteEventScreen(
    eventId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenEventChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val scope = rememberCoroutineScope()
    val repo = remember { EventRepository() }

    var event by remember { mutableStateOf<EventRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    var isNonHostViewer by remember { mutableStateOf(false) }
    var distanceLabel by remember { mutableStateOf<String?>(null) }
    // Set after a successful accept/join so we can prompt the user to share when
    // they're free. Cleared when they save or dismiss the prompt.
    var availabilityPromptOpen by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        loading = true
        loadError = null
        isNonHostViewer = false
        val evResult = runCatching {
            withContext(Dispatchers.IO) { repo.getEvent(eventId) }
        }.getOrElse { Result.failure(it) }
        val ev = evResult.getOrNull()
        event = ev
        if (ev == null) {
            loadError = friendlyErrorMessage(
                error = evResult.exceptionOrNull(),
                fallback = "This event is unavailable."
            )
            loading = false
            return@LaunchedEffect
        }
        val status = runCatching {
            withContext(Dispatchers.IO) { repo.getInviteStatusForEvent(eventId).getOrNull() }
        }.getOrNull()
        inviteStatus = status
        val uid = SupabaseManager.client.auth.currentUserOrNull()?.id
        isNonHostViewer = uid != null && ev.hostId != uid
        distanceLabel = runCatching {
            if (ev.location.isNullOrBlank()) null else withContext(Dispatchers.IO) {
                estimateDistanceFromUser(context, ev.location)
            }
        }.getOrNull()
        loading = false
    }

    val scroll = rememberScrollState()

    if (loadError != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Event unavailable",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = loadError ?: "Couldn't load this event right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    if (loading || event == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val currentEvent = event ?: return

    val handleCalendarClick: () -> Unit = {
        val startMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            parseStartMillis(currentEvent.startsAt)
        } else null
        if (startMillis == null) {
            Toast.makeText(context, "Couldn't add this to calendar right now.", Toast.LENGTH_SHORT).show()
        } else {
            val parsedEnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                parseEndMillis(currentEvent.endsAt)
            } else null
            val endMillis = parsedEnd ?: (startMillis + 60L * 60L * 1000L)
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, currentEvent.title)
                putExtra(CalendarContract.Events.DESCRIPTION, currentEvent.description ?: "")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "Add to calendar")) }
                .onFailure {
                    Toast.makeText(context, "No calendar app found.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val handleMapsClick: () -> Unit = {
        val location = currentEvent.location?.trim().orEmpty()
        if (location.isBlank()) {
            Toast.makeText(context, "No venue address on this event.", Toast.LENGTH_SHORT).show()
        } else {
            openGoogleMapsForPlace(context, location)
        }
    }

    val handleAccept: () -> Unit = {
        scope.launch {
            val previous = inviteStatus
            inviteStatus = "accepted"
            val r = runCatching {
                withContext(Dispatchers.IO) { repo.respondToInvite(eventId, accept = true) }
            }.getOrElse { Result.failure(it) }
            r.onSuccess {
                runCatching { sessionPrefs.incrementAttendedCount() }
                Toast.makeText(context, "Invite accepted", Toast.LENGTH_SHORT).show()
                // If the user has "Share availability with hosts" turned on (Settings →
                // Calendar & availability), pull their saved weekly hours and share
                // automatically. Otherwise prompt them to share manually so the host
                // gets something either way.
                if (sessionPrefs.isCalendarAvailabilitySharingEnabled()) {
                    val auto = withContext(Dispatchers.IO) {
                        repo.shareAvailabilityFromWeeklyWindows(eventId)
                    }
                    if (auto.isSuccess) {
                        Toast.makeText(context, "Your usual hours were shared with the host.", Toast.LENGTH_SHORT).show()
                    } else {
                        availabilityPromptOpen = true
                    }
                } else {
                    availabilityPromptOpen = true
                }
            }.onFailure { err ->
                inviteStatus = previous
                Toast.makeText(
                    context,
                    friendlyErrorMessage(err, fallback = "Couldn't accept the invite. Please try again."),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val handleDecline: () -> Unit = {
        scope.launch {
            val previous = inviteStatus
            inviteStatus = "declined"
            val r = runCatching {
                withContext(Dispatchers.IO) { repo.respondToInvite(eventId, accept = false) }
            }.getOrElse { Result.failure(it) }
            r.onSuccess {
                Toast.makeText(context, "Invite declined", Toast.LENGTH_SHORT).show()
                onBack()
            }.onFailure { err ->
                inviteStatus = previous
                Toast.makeText(
                    context,
                    friendlyErrorMessage(err, fallback = "Couldn't decline the invite. Please try again."),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val handleJoin: () -> Unit = {
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { repo.joinPublicEvent(eventId) }
            }.getOrElse { Result.failure(it) }
            r.onSuccess {
                inviteStatus = "accepted"
                runCatching { sessionPrefs.incrementAttendedCount() }
                Toast.makeText(context, "Joined event", Toast.LENGTH_SHORT).show()
                // Mirror the accept path: auto-share when the toggle is on, otherwise prompt.
                if (sessionPrefs.isCalendarAvailabilitySharingEnabled()) {
                    val auto = withContext(Dispatchers.IO) {
                        repo.shareAvailabilityFromWeeklyWindows(eventId)
                    }
                    if (auto.isSuccess) {
                        Toast.makeText(context, "Your usual hours were shared with the host.", Toast.LENGTH_SHORT).show()
                    } else {
                        availabilityPromptOpen = true
                    }
                } else {
                    availabilityPromptOpen = true
                }
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    friendlyErrorMessage(err, fallback = "Couldn't join the event. Please try again."),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (availabilityPromptOpen) {
        AvailabilityPromptDialog(
            eventTitle = currentEvent.title,
            onDismiss = { availabilityPromptOpen = false },
            onSubmit = { presets, notes ->
                scope.launch {
                    val r = runCatching {
                        withContext(Dispatchers.IO) {
                            repo.submitAvailability(
                                eventId = eventId,
                                presetKeys = presets,
                                notes = notes.ifBlank { null }
                            )
                        }
                    }.getOrElse { Result.failure(it) }
                    r.onSuccess {
                        availabilityPromptOpen = false
                        Toast.makeText(context, "Thanks — your host can see this now.", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            friendlyErrorMessage(err, fallback = "Couldn't save your availability. Please try again."),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    if (isNonHostViewer) {
        PublicEventListingScreen(
            modifier = modifier,
            e = currentEvent,
            distanceLabel = distanceLabel,
            inviteStatus = inviteStatus,
            onBack = onBack,
            onCalendarClick = handleCalendarClick,
            onOpenMaps = handleMapsClick,
            onOpenEventChat = onOpenEventChat,
            onShareAvailability = { availabilityPromptOpen = true },
            onAccept = handleAccept,
            onDecline = handleDecline,
            onJoin = handleJoin
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Text(
            text = "Event details",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.padding(8.dp))
        OutlinedButton(onClick = handleCalendarClick, modifier = Modifier.fillMaxWidth()) {
            Text("Open in calendar app")
        }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = handleMapsClick, modifier = Modifier.fillMaxWidth()) {
            Text("Open in Google Maps")
        }
        Spacer(Modifier.padding(8.dp))
        OutlinedButton(onClick = onOpenEventChat, modifier = Modifier.fillMaxWidth()) {
            Text("Open event chat")
        }
        Spacer(Modifier.padding(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun parseStartMillis(startsAt: String): Long? {
    return try {
        val odt = OffsetDateTime.parse(startsAt)
        odt.atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun parseEndMillis(endsAt: String?): Long? {
    val end = endsAt?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val odt = OffsetDateTime.parse(end)
        odt.atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun openGoogleMapsForPlace(context: android.content.Context, location: String) {
    val encoded = URLEncoder.encode(location, "UTF-8")
    val mapsUri = "https://www.google.com/maps/search/?api=1&query=$encoded".toUri()
    val intent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, mapsUri))
            }
        }
}

private fun estimateDistanceFromUser(context: android.content.Context, locationText: String?): String? {
    val venue = locationText?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val geocoder = runCatching { android.location.Geocoder(context, Locale.getDefault()) }.getOrNull() ?: return null
    val venueAddress = runCatching { geocoder.getFromLocationName(venue, 1) }.getOrNull()?.firstOrNull() ?: return null
    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null
    val current = pickBestLocation(
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    ) ?: return null
    val resultMeters = FloatArray(1)
    Location.distanceBetween(
        current.latitude,
        current.longitude,
        venueAddress.latitude,
        venueAddress.longitude,
        resultMeters
    )
    val miles = resultMeters[0] / 1609.344f
    return "Approx. ${"%.1f".format(Locale.US, miles)} miles away"
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}

@Composable
internal fun PublicEventListingScreen(
    modifier: Modifier,
    e: EventRow,
    distanceLabel: String?,
    inviteStatus: String?,
    onBack: () -> Unit,
    onCalendarClick: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenEventChat: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onJoin: () -> Unit = {},
    onShareAvailability: () -> Unit = {}
) {
    val (headerTitle, headerSub) = when (inviteStatus) {
        "pending" -> "Invitation" to "You've been invited — accept or decline below"
        "accepted" -> "Event details" to "You're going to this event"
        "declined" -> "Event details" to "You declined this invitation"
        else -> "Public event" to "View only — ask the host for an invite to RSVP"
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            com.example.spacer.ui.theme.SpacerCompactHeader(
                title = headerTitle,
                sub = headerSub,
                onBack = onBack
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                PublicHeroCard(
                    title = e.title,
                    statusLabel = liveStatusLabel(e),
                    inviteStatus = inviteStatus
                )

                Spacer(Modifier.height(14.dp))

                PublicDetailsCard(
                    e = e,
                    distanceLabel = distanceLabel
                )

                if (!e.description.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    com.example.spacer.ui.theme.SpacerCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = 16.dp
                    ) {
                        Text(
                            text = e.description ?: "",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                RsvpButtonGroup(
                    inviteStatus = inviteStatus,
                    visibility = e.visibility,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onShareAvailability = onShareAvailability
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PublicActionTile(
                        emoji = "📅",
                        label = "Add to\nCalendar",
                        onClick = onCalendarClick,
                        modifier = Modifier.weight(1f)
                    )
                    PublicActionTile(
                        emoji = "🗺️",
                        label = "Maps",
                        onClick = onOpenMaps,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))
                com.example.spacer.ui.theme.SpacerButton(
                    label = "Open event chat",
                    onClick = onOpenEventChat,
                    kind = if (inviteStatus == "pending") com.example.spacer.ui.theme.SpacerButtonKind.Secondary
                    else com.example.spacer.ui.theme.SpacerButtonKind.Gold,
                    size = com.example.spacer.ui.theme.SpacerButtonSize.Lg,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PublicHeroCard(title: String, statusLabel: String?, inviteStatus: String?) {
    val purpleInk = MaterialTheme.colorScheme.primaryContainer
    val surface = MaterialTheme.colorScheme.surface
    val (kicker, kickerTone) = when (inviteStatus) {
        "pending" -> "INVITATION" to com.example.spacer.ui.theme.SpacerChipTone.Gold
        "accepted" -> "YOU'RE GOING" to com.example.spacer.ui.theme.SpacerChipTone.Purple
        "declined" -> "DECLINED" to com.example.spacer.ui.theme.SpacerChipTone.Ghost
        else -> "PUBLIC EVENT" to com.example.spacer.ui.theme.SpacerChipTone.Success
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(purpleInk, surface)
                        )
                    )
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!statusLabel.isNullOrBlank()) {
                        com.example.spacer.ui.theme.SpacerChip(
                            text = statusLabel,
                            tone = com.example.spacer.ui.theme.SpacerChipTone.Success,
                            size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                        )
                    }
                }
                if (!statusLabel.isNullOrBlank()) Spacer(Modifier.height(12.dp))
                Text(
                    text = kicker,
                    color = when (kickerTone) {
                        com.example.spacer.ui.theme.SpacerChipTone.Gold -> MaterialTheme.colorScheme.secondary
                        com.example.spacer.ui.theme.SpacerChipTone.Purple -> MaterialTheme.colorScheme.primary
                        com.example.spacer.ui.theme.SpacerChipTone.Ghost -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.4).sp
                )
            }
        }
    }
}

@Composable
private fun PublicDetailsCard(e: EventRow, distanceLabel: String?) {
    com.example.spacer.ui.theme.SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.example.spacer.ui.theme.SpacerKeyVal(
                label = "Date & time",
                value = formatPublicDateTimeLine(e.startsAt, e.endsAt),
                valueSize = 15.sp
            )
            val venue = buildVenueLines(e.location, distanceLabel)
            Column {
                com.example.spacer.ui.theme.SpacerKeyVal(
                    label = "Venue",
                    value = venue.first,
                    valueSize = 15.sp
                )
                if (!venue.second.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = venue.second!!,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicActionTile(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.example.spacer.ui.theme.SpacerCard(
        modifier = modifier,
        padding = 14.dp,
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * One unified RSVP button pair driven by `inviteStatus` so the user gets a
 * consistent join/decline experience regardless of whether they were invited
 * or are joining a public event.
 */
@Composable
private fun RsvpButtonGroup(
    inviteStatus: String?,
    visibility: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onShareAvailability: () -> Unit = {}
) {
    val isInviteOnly = visibility == "invite_only"
    when (inviteStatus) {
        "pending" -> {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Accept invite")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text("Decline")
            }
        }
        "accepted" -> {
            com.example.spacer.ui.theme.SpacerCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 14.dp
            ) {
                Text(
                    text = "You're going. See you there!",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            // Share-availability action — primary entry point when the host nudges
            // for replies. Tapping the nudge notification deep-links here, where
            // this button opens the same preset picker the accept flow uses.
            Button(onClick = onShareAvailability, modifier = Modifier.fillMaxWidth()) {
                Text("Share availability")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text("Leave event")
            }
        }
        "declined" -> {
            com.example.spacer.ui.theme.SpacerCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 14.dp
            ) {
                Text(
                    text = "You declined this invitation. Changed your mind?",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Change to going")
            }
        }
        else -> {
            // null status — public event with no RSVP yet, OR invite-only without an invite.
            if (isInviteOnly) {
                com.example.spacer.ui.theme.SpacerCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 16.dp
                ) {
                    Text(
                        text = "This is an invite-only event. Ask the host for an invite to RSVP.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                    Text("Join event")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                    Text("Not interested")
                }
            }
        }
    }
}

private fun buildVenueLines(location: String?, distanceLabel: String?): Pair<String, String?> {
    val raw = location?.trim().orEmpty()
    if (raw.isEmpty()) return "Venue TBD" to null
    val parts = raw.split("·", "•", "—", "-").map { it.trim() }.filter { it.isNotBlank() }
    val primary: String
    val rest: String?
    if (parts.size >= 2) {
        primary = parts.first()
        val tail = parts.drop(1).joinToString(" · ")
        rest = if (distanceLabel.isNullOrBlank()) tail else "$tail · $distanceLabel"
    } else {
        primary = raw
        rest = distanceLabel
    }
    return primary to rest
}

private fun formatPublicDateTimeLine(startsAt: String, endsAt: String?): String {
    val datePart = formatEventDateNoTime(startsAt)
    val timePart = formatEventTimeRange(startsAt, endsAt)
    return "$datePart · $timePart"
}

private fun liveStatusLabel(e: EventRow): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return try {
        val start = OffsetDateTime.parse(e.startsAt)
        val now = OffsetDateTime.now()
        val end = e.endsAt?.takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it) }
        val sameDay = start.toLocalDate() == now.toLocalDate()
        when {
            end != null && !now.isBefore(start) && now.isBefore(end) -> "LIVE NOW"
            sameDay && start.hour >= 17 -> "LIVE TONIGHT"
            sameDay -> "TODAY"
            start.toLocalDate() == now.toLocalDate().plusDays(1) -> "TOMORROW"
            else -> null
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Convert any throwable into a short, user-friendly message.
 * Strips technical content (SQL, HTTP codes, stack-trace-ish text, table names)
 * and falls back to [fallback] when nothing meaningful is left.
 */
internal fun friendlyErrorMessage(error: Throwable?, fallback: String): String {
    val raw = error?.message?.trim().orEmpty()
    if (raw.isBlank()) return fallback
    val lower = raw.lowercase()
    val technicalSignals = listOf(
        "sql", "supabase", "postgrest", "postgres", "rest api", "http ",
        "401", "403", "404", "409", "500", "502", "503", "504",
        "exception", "stack", "constraint", "foreign key", "primary key",
        "duplicate key", "23505", "rls", "violates", "row-level",
        "json", "kotlinx", "ktor", "io.ktor", "io.github",
        "table ", "column ", "schema ", "select ", "update ", "insert ",
        "from public", "auth.users", "event_invites", "event_members",
        "app_events", "profiles."
    )
    if (technicalSignals.any { it in lower }) return fallback
    if (raw.length > 140) return fallback
    return raw
}

/**
 * Quick "share when you're free" sheet shown right after a guest accepts an
 * invite or joins a public event. Writes into [submitAvailability] so the
 * host's Guest Availability dashboard reflects the reply immediately.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityPromptDialog(
    eventTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (Set<String>, String) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }
    val presets = listOf(
        "morning" to "Morning",
        "midday" to "Midday",
        "afternoon" to "Afternoon",
        "evening" to "Evening"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When can you make it?") },
        text = {
            Column {
                Text(
                    "Share which parts of the day work for you for \"$eventTitle\". " +
                        "Your host uses this to pick a time that fits the most people.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (key, label) ->
                        val on = key in selected
                        com.example.spacer.ui.theme.SpacerChip(
                            text = label,
                            tone = if (on) com.example.spacer.ui.theme.SpacerChipTone.PurpleSolid
                            else com.example.spacer.ui.theme.SpacerChipTone.Default,
                            onClick = {
                                selected = if (on) selected - key else selected + key
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. only Saturday afternoon works") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(selected, notes.trim()) },
                enabled = selected.isNotEmpty() || notes.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Maybe later") }
        }
    )
}
