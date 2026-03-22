package com.example.spacer.location

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacer.events.EventCategories
import com.example.spacer.events.EventRepository
import com.example.spacer.profile.AvailabilityRepository
import com.example.spacer.profile.FriendListItem
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SearchUserRow
import com.example.spacer.profile.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(FlowPreview::class)
@Composable
fun CreateEventDetailsScreen(
    place: PlaceUi,
    eventTitle: String,
    onEventTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepository = remember { ProfileRepository() }
    val eventRepository = remember { EventRepository() }
    val availabilityRepository = remember { AvailabilityRepository() }

    var eventDescription by remember { mutableStateOf("") }
    var bringItems by remember { mutableStateOf(listOf<String>()) }
    var bringItemInput by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }
    var startTimeStr by remember { mutableStateOf("6:00 PM") }
    var endTimeStr by remember { mutableStateOf("") }
    var invitedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cohostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var friends by remember { mutableStateOf<List<FriendListItem>>(emptyList()) }
    var inviteSearchQuery by remember { mutableStateOf("") }
    var inviteSearchResults by remember { mutableStateOf<List<SearchUserRow>>(emptyList()) }
    var loadingInviteSearch by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var visibilityPublic by remember { mutableStateOf(true) }
    var publicJoinLimitInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var chatMode by remember { mutableStateOf("all_members") }

    LaunchedEffect(Unit) {
        val fr = withContext(Dispatchers.IO) { profileRepository.getFriends() }
        fr.onSuccess { friends = it }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { inviteSearchQuery }
            .debounce(350L)
            .distinctUntilChanged()
            .collectLatest { q ->
                val t = q.trim()
                if (t.isEmpty()) {
                    inviteSearchResults = emptyList()
                    return@collectLatest
                }
                loadingInviteSearch = true
                try {
                    val r = withContext(Dispatchers.IO) { profileRepository.searchUsers(t) }
                    r.onSuccess { inviteSearchResults = it }
                        .onFailure {
                            inviteSearchResults = emptyList()
                            Toast.makeText(context, "Couldn't search right now. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                } finally {
                    loadingInviteSearch = false
                }
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            item {
                Text(
                    text = "STEP 02 / 02",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Event details",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = "Description, schedule, and invites.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                com.example.spacer.ui.theme.SpacerButton(
                    label = "Back to venue",
                    onClick = onBack,
                    kind = com.example.spacer.ui.theme.SpacerButtonKind.Secondary,
                    size = com.example.spacer.ui.theme.SpacerButtonSize.Sm,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Venue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            place.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (place.address.isNotBlank()) {
                            Text(
                                place.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Title",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = onEventTitleChange,
                            label = { Text("Event title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = detailsFieldColors()
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = { eventDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = detailsFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bringItemInput,
                    onValueChange = { bringItemInput = it },
                    label = { Text("Add item people can bring (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    colors = detailsFieldColors()
                )
                OutlinedButton(
                    onClick = {
                        val item = bringItemInput.trim()
                        if (item.isBlank()) return@OutlinedButton
                        if (bringItems.none { it.equals(item, ignoreCase = true) }) {
                            bringItems = bringItems + item
                        }
                        bringItemInput = ""
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Add bring item") }
                if (bringItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    bringItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                bringItems = bringItems.filterNot { it.equals(item, ignoreCase = true) }
                            }) { Text("Remove") }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Date & time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    placeholder = { Text("2026-04-20") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = detailsFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTimeStr,
                        onValueChange = { startTimeStr = it },
                        label = { Text("Start") },
                        placeholder = { Text("6:00 PM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = detailsFieldColors()
                    )
                    OutlinedTextField(
                        value = endTimeStr,
                        onValueChange = { endTimeStr = it },
                        label = { Text("End (optional)") },
                        placeholder = { Text("9:00 PM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = detailsFieldColors()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    "Who can discover this?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visibilityPublic,
                        onClick = { visibilityPublic = true },
                        label = { Text("Public") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = !visibilityPublic,
                        onClick = { visibilityPublic = false },
                        label = { Text("Friends / invites only") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
                Text(
                    "Public events appear on Home discovery and the public list. Invite-only stays with people you invite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
                if (visibilityPublic) {
                    OutlinedTextField(
                        value = publicJoinLimitInput,
                        onValueChange = { raw ->
                            publicJoinLimitInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Public join limit (optional)") },
                        placeholder = { Text("e.g. 50") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = detailsFieldColors()
                    )
                    Text(
                        "Leave empty for unlimited public joins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                }
                Text(
                    "Event chat permissions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatMode == "all_members",
                        onClick = { chatMode = "all_members" },
                        label = { Text("All can chat") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = chatMode == "host_cohosts_only",
                        onClick = { chatMode = "host_cohosts_only" },
                        label = { Text("Hosts only") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                    FilterChip(
                        selected = chatMode == "disabled",
                        onClick = { chatMode = "disabled" },
                        label = { Text("Disabled") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Category tag",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventCategories.all.forEach { cat ->
                        val on = selectedCategory == cat
                        FilterChip(
                            selected = on,
                            onClick = {
                                selectedCategory = if (on) null else cat
                            },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    "Co-hosts (optional)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Pick trusted helpers — co-hosts can manage roster and chat settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                if (friends.isEmpty()) {
                    Text(
                        "No friends yet — use Find people under Events to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        friends.filterNot { ProfileRepository.isOfflineDemoProfile(it.id) }.forEach { f ->
                            val coOn = f.id in cohostIds
                            AssistChip(
                                onClick = {
                                    cohostIds = if (coOn) cohostIds - f.id else cohostIds + f.id
                                    invitedIds = invitedIds - f.id
                                },
                                label = { Text(f.fullName) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (coOn) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Invite people",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Toggle friends or search by name to add invitees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                if (friends.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        friends.filterNot { ProfileRepository.isOfflineDemoProfile(it.id) }.forEach { f ->
                            val on = f.id in invitedIds
                            AssistChip(
                                onClick = {
                                    invitedIds = if (on) invitedIds - f.id else invitedIds + f.id
                                    cohostIds = cohostIds - f.id
                                },
                                label = { Text(f.fullName) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (on) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = inviteSearchQuery,
                    onValueChange = { inviteSearchQuery = it },
                    label = { Text("Search users to invite") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = detailsFieldColors()
                )
                if (loadingInviteSearch) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                inviteSearchResults.filterNot { ProfileRepository.isOfflineDemoProfile(it.id) }.forEach { u ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                u.displayName(),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "@${u.username ?: "user"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )
                        }
                        val inviteOn = u.id in invitedIds
                        val coOn = u.id in cohostIds
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    invitedIds = if (inviteOn) invitedIds - u.id else invitedIds + u.id
                                    cohostIds = cohostIds - u.id
                                }
                            ) {
                                Text(if (inviteOn) "Remove invite" else "Invite")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    cohostIds = if (coOn) cohostIds - u.id else cohostIds + u.id
                                    invitedIds = invitedIds - u.id
                                }
                            ) {
                                Text(if (coOn) "Remove co-host" else "Co-host")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                val canPublish = eventDate.isNotBlank() && eventTitle.isNotBlank()
                Button(
                    onClick = {
                        val title = eventTitle.trim()
                        if (title.isEmpty()) {
                            Toast.makeText(context, "Add an event title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val startIso = runCatching {
                            buildOffsetIso(eventDate.trim(), startTimeStr.trim())
                        }.getOrElse {
                            Toast.makeText(
                                context,
                                "Use date YYYY-MM-DD and time like 6:00 PM",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        val endIso = endTimeStr.trim().takeIf { it.isNotEmpty() }?.let { et ->
                            runCatching { buildOffsetIso(eventDate.trim(), et) }.getOrNull()
                        }
                        val loc = buildString {
                            append(place.name)
                            if (place.address.isNotBlank()) {
                                append(" · ")
                                append(place.address)
                            }
                        }
                        val publicLimit = publicJoinLimitInput.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
                        if (visibilityPublic && publicJoinLimitInput.isNotBlank() && (publicLimit == null || publicLimit < 1)) {
                            Toast.makeText(context, "Public join limit must be a positive number", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        publishing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                eventRepository.createEventWithInvites(
                                    title = title,
                                    description = eventDescription.trim().ifBlank { null },
                                    startsAtIso = startIso,
                                    endsAtIso = endIso,
                                    locationLabel = loc,
                                    inviteeIds = invitedIds.toList(),
                                    cohostIds = cohostIds.toList(),
                                    visibility = if (visibilityPublic) "public" else "invite_only",
                                    maxAttendees = if (visibilityPublic) publicLimit else null,
                                    category = selectedCategory,
                                    bringItems = eventRepository.encodeBringItems(bringItems),
                                    chatMode = chatMode
                                )
                            }
                            publishing = false
                            result.onSuccess { outcome ->
                                val scheduleChecks =
                                    (invitedIds + cohostIds).filterNot { ProfileRepository.isOfflineDemoProfile(it) }
                                val conflicts = withContext(Dispatchers.IO) {
                                    availabilityRepository.scheduleConflictsForFriends(
                                        startsAtIso = startIso,
                                        endsAtIso = endIso,
                                        friendIds = scheduleChecks.distinct().toList(),
                                        zoneId = ZoneId.systemDefault()
                                    ).getOrDefault(emptyList())
                                }
                                if (conflicts.isNotEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        eventRepository.postAvailabilityConflictNotice(outcome.eventId, conflicts)
                                    }
                                }
                                val msg = when {
                                    outcome.invitesRequested == 0 ->
                                        "Event created"
                                    outcome.invitesSent == outcome.invitesRequested ->
                                        "Event created — ${outcome.invitesSent} invite(s) sent"
                                    outcome.invitesSent == 0 ->
                                        "Event created — invites are still pending."
                                    else ->
                                        "Event created — ${outcome.invitesSent} of ${outcome.invitesRequested} invite(s) sent."
                                }
                                val suffix = when {
                                    conflicts.isEmpty() -> ""
                                    conflicts.size == 1 ->
                                        " Friends schedule note posted to group chat."
                                    else ->
                                        " Friends schedule notes posted to group chat."
                                }
                                Toast.makeText(context, msg + suffix, Toast.LENGTH_LONG).show()
                                onPublished()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Couldn't create the event right now. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = canPublish && !publishing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (publishing) "Publishing…" else "Publish event & send invites")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun buildOffsetIso(dateStr: String, timeStr: String): String {
    val d = LocalDate.parse(dateStr)
    val t = parseFlexibleLocalTime(timeStr)
    return d.atTime(t).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
}

private fun parseFlexibleLocalTime(raw: String): LocalTime {
    val value = raw.trim()
    val formatters = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()),
        DateTimeFormatter.ofPattern("h:mma", Locale.getDefault()),
        DateTimeFormatter.ofPattern("H:mm", Locale.getDefault()),
        DateTimeFormatter.ISO_LOCAL_TIME
    )
    formatters.forEach { formatter ->
        try {
            return LocalTime.parse(value.uppercase(Locale.getDefault()), formatter)
        } catch (_: DateTimeParseException) {
        }
    }
    throw DateTimeParseException("Invalid time", raw, 0)
}

@Composable
private fun detailsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
)
