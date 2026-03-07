package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.EventRepository
import com.example.spacer.events.formatEventDateNoTime
import com.example.spacer.location.PlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Composable
fun HostedEventsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileRepository() }
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cancelTarget by remember { mutableStateOf<EventRow?>(null) }
    var cancelling by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        val result = withContext(Dispatchers.IO) { profileRepo.getAllHostedEvents() }
        val loaded = result.fold(
            onSuccess = { it },
            onFailure = { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load events", Toast.LENGTH_LONG).show()
                }
                emptyList()
            }
        )
        events = loaded
        val locs = loaded.map { it.id to (it.location ?: "") }.filter { it.second.isNotBlank() }
        photoUrls = withContext(Dispatchers.IO) {
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
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun isUpcoming(startsAt: String): Boolean {
        return try {
            OffsetDateTime.parse(startsAt).isAfter(OffsetDateTime.now())
        } catch (_: DateTimeParseException) {
            false
        }
    }

    cancelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel event?") },
            text = {
                Text(
                    "“${target.title}” will be removed and invitees will be notified.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !cancelling,
                    onClick = {
                        cancelling = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { eventRepo.cancelHostedEvent(target.id) }
                            cancelling = false
                            cancelTarget = null
                            r.onSuccess {
                                Toast.makeText(context, "Event cancelled", Toast.LENGTH_SHORT).show()
                                reload()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Couldn't cancel this event right now. Please try again in a moment.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text("Cancel event") }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }, enabled = !cancelling) { Text("Keep") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DetailScreenHeader(kicker = "Your events", title = "Hosted Events")
            Spacer(modifier = Modifier.height(10.dp))

            if (loading) {
                Text("Loading...")
            } else if (events.isEmpty()) {
                Text("No hosted events yet.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val url = photoUrls[event.id]
                                if (!url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Event image",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatEventDateNoTime(event.startsAt),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    event.location?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isUpcoming(event.startsAt)) {
                                        OutlinedButton(onClick = { cancelTarget = event }) {
                                            Text("Cancel event")
                                        }
                                    } else {
                                        // Past hosted event — show a clear "Hosted ✓" pill so the
                                        // user can tell at a glance which events have wrapped.
                                        HostedPastBadge()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) { Text("Back") }
    }
}

@Composable
fun AttendedEventsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PastEventsListScreen(
        title = "Past events",
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun PastEventsListScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<PastEventItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.IO) { repository.getPastInvitedEventsWithStatus() }
            .onSuccess { items = it }
            .onFailure { Toast.makeText(context, "Failed to load events", Toast.LENGTH_LONG).show() }
        loading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DetailScreenHeader(kicker = "Your events", title = title)
            Spacer(modifier = Modifier.height(10.dp))

            if (loading) {
                Text("Loading…")
            } else if (items.isEmpty()) {
                Text("No past events yet.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp)
                ) {
                    items(items, key = { it.event.id }) { item ->
                        PastEventCard(item)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) { Text("Back") }
    }
}

@Composable
private fun PastEventCard(item: PastEventItem) {
    val (badgeText, badgeColor) = when (item.status) {
        PastEventStatus.Attended -> "Attended" to com.example.spacer.ui.theme.SpacerLiveGreen
        PastEventStatus.Declined -> "Declined" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        PastEventStatus.Missed -> "Missed" to com.example.spacer.ui.theme.SpacerDanger
        PastEventStatus.Other -> "Past" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.event.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatEventDateNoTime(item.event.startsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                item.event.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = badgeColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    badgeText.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp
                    ),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EventListScreen(
    title: String,
    onBack: () -> Unit,
    fetcher: suspend (ProfileRepository) -> Result<List<EventRow>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<List<EventRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.IO) { fetcher(repository) }
            .onSuccess { events = it }
            .onFailure { Toast.makeText(context, "Failed to load events", Toast.LENGTH_LONG).show() }
        loading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DetailScreenHeader(kicker = "Your events", title = title)
            Spacer(modifier = Modifier.height(10.dp))

            if (loading) {
                Text("Loading...")
            } else if (events.isEmpty()) {
                Text("No past events yet.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp)
                ) {
                    items(events) { event ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(formatEventDateNoTime(event.startsAt), style = MaterialTheme.typography.bodySmall)
                                event.location?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) { Text("Back") }
    }
}

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenDm: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var friends by remember { mutableStateOf<List<FriendListItem>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<IncomingFriendRequestItem>>(emptyList()) }

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) { repository.getFriends() }
            .onSuccess { friends = it }
            .onFailure {
                Toast.makeText(context, "Failed to load friends", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) { repository.listIncomingFriendRequests() }
            .onSuccess { incomingRequests = it }
            .onFailure {
                Toast.makeText(context, "Failed to load friend requests", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DetailScreenHeader(kicker = "Your circle", title = "Friends")
            Spacer(modifier = Modifier.height(10.dp))

                if (loading) {
                    Text("Loading...")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp)
                    ) {
                        item {
                            Text(
                                "Friend requests",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (incomingRequests.isEmpty()) {
                            item { Text("No pending requests.") }
                        } else {
                            items(incomingRequests, key = { it.senderId }) { req ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenProfile(req.senderId) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (req.avatarUrl.isNullOrBlank()) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            } else {
                                                Image(
                                                    painter = rememberAsyncImagePainter(Uri.parse(req.avatarUrl)),
                                                    contentDescription = "Request avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.size(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(req.fullName, style = MaterialTheme.typography.titleSmall)
                                                Text("@${req.username}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repository.respondToFriendRequest(req.senderId, accept = true)
                                                    }.onSuccess {
                                                        Toast.makeText(context, "Request accepted", Toast.LENGTH_SHORT).show()
                                                        reload()
                                                    }.onFailure {
                                                        Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.Filled.Check, contentDescription = "Accept request")
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repository.respondToFriendRequest(req.senderId, accept = false)
                                                    }.onSuccess {
                                                        Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
                                                        reload()
                                                    }.onFailure {
                                                        Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Decline request")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Friends",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (friends.isEmpty()) {
                            item { Text("No friends yet.") }
                        } else {
                            items(friends) { friend ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenProfile(friend.id) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (friend.avatarUrl.isNullOrBlank()) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            } else {
                                                Image(
                                                    painter = rememberAsyncImagePainter(Uri.parse(friend.avatarUrl)),
                                                    contentDescription = "Friend avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.size(10.dp))
                                            Column {
                                                Text(friend.fullName, style = MaterialTheme.typography.titleSmall)
                                                Text("@${friend.username}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { onOpenDm(friend.id) }) { Text("Message") }
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { repository.unfriend(friend.id) }
                                                        .onSuccess {
                                                            Toast.makeText(context, "Friend removed", Toast.LENGTH_SHORT).show()
                                                            reload()
                                                        }
                                                        .onFailure {
                                                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                            }) { Text("Unfriend") }
                                            Button(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { repository.blockUser(friend.id) }
                                                        .onSuccess {
                                                            Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                                            reload()
                                                        }
                                                        .onFailure {
                                                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                            }) { Text("Block") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .align(Alignment.BottomCenter)
        ) { Text("Back") }
    }
}


@Composable
private fun DetailScreenHeader(kicker: String, title: String) {
    Column {
        Text(
            text = kicker.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp
        )
    }
}

@Composable
private fun HostedPastBadge() {
    val color = com.example.spacer.ui.theme.SpacerLiveGreen
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            "HOSTED ✓",
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
