package com.example.spacer.events

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spacer.location.PlacesRepository
import com.example.spacer.profile.EventRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HostEventDetailScreen(
    eventId: String,
    onOpenEventChat: () -> Unit = {},
    onOpenAvailabilityDashboard: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }
    val scope = rememberCoroutineScope()
    var event by remember { mutableStateOf<EventRow?>(null) }
    var availability by remember { mutableStateOf<List<AvailabilityEntryUi>>(emptyList()) }
    var cohosts by remember { mutableStateOf<List<com.example.spacer.profile.FriendListItem>>(emptyList()) }
    var friends by remember { mutableStateOf<List<com.example.spacer.profile.FriendListItem>>(emptyList()) }
    var selectedCohostId by remember { mutableStateOf("") }
    var selectedCohostName by remember { mutableStateOf("") }
    var chatMode by remember { mutableStateOf("all_members") }
    var startsAtDraft by remember { mutableStateOf("") }
    var endsAtDraft by remember { mutableStateOf("") }
    var bringItemsDraft by remember { mutableStateOf(listOf<String>()) }
    var bringItemInput by remember { mutableStateOf("") }
    var bringClaims by remember { mutableStateOf<List<BringItemClaimUi>>(emptyList()) }
    var venuePhotoUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        loading = true
        withContext(Dispatchers.IO) { repo.getEvent(eventId) }
            .onSuccess {
                event = it
                startsAtDraft = it.startsAt
                endsAtDraft = it.endsAt ?: ""
                bringItemsDraft = repo.parseBringItems(it.bringItems)
                val query = it.location?.trim().orEmpty()
                if (query.isNotEmpty() && PlacesRepository.isApiKeyConfigured()) {
                    val photoName = withContext(Dispatchers.IO) {
                        placesRepo.searchText(query)
                            .getOrDefault(emptyList())
                            .firstOrNull()
                            ?.primaryPhotoName
                    }
                    venuePhotoUrl = photoName?.let { name -> placesRepo.photoMediaUrl(name, 900) }
                }
            }
            .onFailure {
                Toast.makeText(context, "Failed to load", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) { repo.listAvailabilityForHost(eventId) }
            .onSuccess { availability = it }
            .onFailure {
                Toast.makeText(context, "Could not load availability", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) { repo.listCohosts(eventId) }.onSuccess { cohosts = it }
        withContext(Dispatchers.IO) { repo.listBringItemClaims(eventId) }.onSuccess { bringClaims = it }
        withContext(Dispatchers.IO) { com.example.spacer.profile.ProfileRepository().getFriends() }
            .onSuccess { friends = it }
        chatMode = withContext(Dispatchers.IO) { repo.getEventChatMode(eventId) }.getOrDefault("all_members")
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (loading || event == null) {
            Text("Loading…")
            return@Column
        }

        val e = event ?: return@Column
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                com.example.spacer.ui.theme.SpacerCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 18.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                e.title.uppercase(),
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            com.example.spacer.ui.theme.SpacerChip(
                                text = "Host",
                                tone = com.example.spacer.ui.theme.SpacerChipTone.Gold,
                                size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                            )
                        }
                        Text(
                            "Guest availability",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatEventDateTime(e.startsAt) + "  •  " + formatEventTimeRange(e.startsAt, e.endsAt),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        e.location?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val bringPreview = repo.encodeBringItems(bringItemsDraft)
                        bringPreview?.takeIf { it.isNotBlank() }?.let {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                com.example.spacer.ui.theme.SpacerChip(
                                    text = "Bring: $it",
                                    tone = com.example.spacer.ui.theme.SpacerChipTone.Ghost,
                                    size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                                )
                            }
                        }
                    }
                }
            }

            item {
                venuePhotoUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Event venue photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            item {
                SectionCard(title = "Event chat") {
                    OutlinedButton(onClick = onOpenEventChat, modifier = Modifier.fillMaxWidth()) {
                        Text("Open event chat")
                    }
                    Text(
                        "Chat mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChatModeButton(
                            label = "All members",
                            selected = chatMode == "all_members",
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.setEventChatMode(eventId, "all_members") }
                                chatMode = "all_members"
                            }
                        }
                        ChatModeButton(
                            label = "Hosts only",
                            selected = chatMode == "host_cohosts_only",
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.setEventChatMode(eventId, "host_cohosts_only") }
                                chatMode = "host_cohosts_only"
                            }
                        }
                        ChatModeButton(
                            label = "Disabled",
                            selected = chatMode == "disabled",
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.setEventChatMode(eventId, "disabled") }
                                chatMode = "disabled"
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Co-hosts") {
                    Text(
                        "You (Host)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (cohosts.isEmpty()) {
                        Text("No co-hosts yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        cohosts.forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.width(32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        c.fullName.take(2).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                                Text(
                                    "${c.fullName} (Co-host)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.removeCohost(eventId, c.id) }
                                        cohosts = withContext(Dispatchers.IO) { repo.listCohosts(eventId) }.getOrDefault(emptyList())
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCohostName,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select a friend below") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val id = selectedCohostId.trim()
                                if (id.isBlank()) return@OutlinedButton
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.addCohost(eventId, id) }
                                        .onSuccess {
                                            Toast.makeText(context, "Co-host added", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "Couldn't add co-host", Toast.LENGTH_SHORT).show()
                                        }
                                    cohosts = withContext(Dispatchers.IO) { repo.listCohosts(eventId) }.getOrDefault(emptyList())
                                }
                            }
                        ) { Text("Add as co-host") }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(friends, key = { it.id }) { friend ->
                            OutlinedButton(
                                onClick = {
                                    selectedCohostId = friend.id
                                    selectedCohostName = friend.fullName
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedCohostId == friend.id) "${friend.fullName} ✓" else friend.fullName)
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Edit event") {
                    Text(
                        text = "Tap to pick a date and time. Times use 12-hour clock in your device timezone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    SpacerDateTimeField(
                        label = "Starts",
                        isoValue = startsAtDraft,
                        onPick = { showStartPicker = true }
                    )
                    Spacer(Modifier.height(10.dp))
                    SpacerDateTimeField(
                        label = "Ends (optional)",
                        isoValue = endsAtDraft,
                        onPick = { showEndPicker = true },
                        placeholder = "Pick an end time (optional)"
                    )
                    if (endsAtDraft.isNotBlank()) {
                        Text(
                            text = "Clear end time",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .padding(top = 4.dp, start = 4.dp)
                                .clickable { endsAtDraft = "" }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bringItemInput,
                            onValueChange = { bringItemInput = it },
                            label = { Text("Add bring item...") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val item = bringItemInput.trim()
                                if (item.isBlank()) return@OutlinedButton
                                if (bringItemsDraft.none { it.equals(item, ignoreCase = true) }) {
                                    bringItemsDraft = bringItemsDraft + item
                                }
                                bringItemInput = ""
                            }
                        ) { Text("+ Add") }
                    }
                    if (bringItemsDraft.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(bringItemsDraft, key = { it.lowercase() }) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val claim = bringClaims.firstOrNull { it.itemKey == item.trim().lowercase() }
                                    Text(
                                        if (claim == null) item else "$item — ${claim.claimedByName} bringing this",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    OutlinedButton(onClick = {
                                        bringItemsDraft = bringItemsDraft.filterNot { it.equals(item, ignoreCase = true) }
                                    }) { Text("Remove") }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.updateEventCoreDetails(
                                        eventId = eventId,
                                        startsAtIso = startsAtDraft,
                                        endsAtIso = endsAtDraft.ifBlank { null },
                                        bringItems = repo.encodeBringItems(bringItemsDraft)
                                    )
                                }.onSuccess {
                                    Toast.makeText(context, "Event updated", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Couldn't update event.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) { Text("Save event changes") }
                }
            }

            item {
                Text(
                    "When people are free (from the app)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Invitees choose time presets in Spacer. If they allow calendar read on their phone, “busy at event time” reflects their device calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                com.example.spacer.ui.theme.SpacerButton(
                    label = "Open availability dashboard",
                    onClick = onOpenAvailabilityDashboard,
                    kind = com.example.spacer.ui.theme.SpacerButtonKind.Gold,
                    size = com.example.spacer.ui.theme.SpacerButtonSize.Md,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (availability.isEmpty()) {
                item {
                    Text(
                        "No availability yet. Guests can open their invite and tap time presets after accepting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            } else {
                items(availability, key = { it.userId }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.displayName, fontWeight = FontWeight.SemiBold)
                            val slots = row.presetSlots.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(", ")
                            if (slots.isNotBlank()) {
                                Text("Times: $slots", style = MaterialTheme.typography.bodySmall)
                            }
                            row.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (row.calendarBusyOverlapsEvent) {
                                Text(
                                    "Calendar: busy at this event’s time on their device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }

    if (showStartPicker && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SpacerDateTimePickerDialog(
            title = "Start",
            initialIso = startsAtDraft.ifBlank { event?.startsAt.orEmpty() },
            onDismiss = { showStartPicker = false },
            onConfirm = { iso ->
                startsAtDraft = iso
                showStartPicker = false
            }
        )
    }

    if (showEndPicker && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SpacerDateTimePickerDialog(
            title = "End",
            initialIso = endsAtDraft.ifBlank { startsAtDraft.ifBlank { event?.startsAt.orEmpty() } },
            onDismiss = { showEndPicker = false },
            onConfirm = { iso ->
                endsAtDraft = iso
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    com.example.spacer.ui.theme.SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp
            )
            content()
        }
    }
}

@Composable
private fun RoleBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ChatModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Surface(
            modifier = modifier.height(38.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(38.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
