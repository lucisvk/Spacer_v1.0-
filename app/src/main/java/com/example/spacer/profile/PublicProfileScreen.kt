package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.formatEventDateNoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var loading by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf<PublicProfileSnapshot?>(null) }
    var friendshipState by remember { mutableStateOf(FriendshipState.NONE) }

    suspend fun load() {
        loading = true
        withContext(Dispatchers.IO) { repository.getPublicProfile(userId) }
            .onSuccess { snapshot = it }
            .onFailure {
                Toast.makeText(context, "Couldn't open this profile right now.", Toast.LENGTH_LONG).show()
            }
        friendshipState = withContext(Dispatchers.IO) {
            repository.getFriendshipStates(listOf(userId)).getOrDefault(emptyMap())[userId] ?: FriendshipState.NONE
        }
        loading = false
    }

    LaunchedEffect(userId) { load() }
    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { load() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (loading) {
            Text("Loading profile...")
            return@Column
        }
        val s = snapshot
        if (s == null) {
            Text("Profile unavailable.")
            return@Column
        }

        val now = OffsetDateTime.now()
        fun eventSortKey(value: String): Pair<Int, Long> {
            val parsed = try {
                OffsetDateTime.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
            val epoch = parsed?.toEpochSecond() ?: Long.MIN_VALUE
            val upcomingGroup = if (parsed != null && parsed.isAfter(now)) 0 else 1
            val order = if (upcomingGroup == 0) epoch else -epoch
            return upcomingGroup to order
        }
        val hostedSorted = s.hostedEvents.sortedWith(
            compareBy<EventRow> { eventSortKey(it.startsAt).first }
                .thenBy { eventSortKey(it.startsAt).second }
        )
        val attendedSorted = s.attendedEvents.sortedWith(
            compareBy<EventRow> { eventSortKey(it.startsAt).first }
                .thenBy { eventSortKey(it.startsAt).second }
        )
        val friendsSorted = s.friends.sortedBy { it.fullName.lowercase() }
        val presence = PresenceStatus.fromDb(s.presenceStatus)

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Text(
                        text = s.fullName.firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    PresenceStatusDot(presence, Modifier.align(Alignment.BottomEnd))
                }
            } else {
                Box(modifier = Modifier.size(88.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(s.avatarUrl)),
                        contentDescription = "Profile image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    PresenceStatusDot(presence, Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                s.fullName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${s.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(presence.dotColor)
                    )
                    Text(presence.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "\"${s.aboutMe.ifBlank { "No bio yet" }}\"",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PublicStatCard(s.hostedEvents.size.toString(), "Hosted", Modifier.weight(1f))
            PublicStatCard(s.attendedEvents.size.toString(), "Attended", Modifier.weight(1f))
            PublicStatCard(s.friends.size.toString(), "Friends", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
        val primaryLabel = when (friendshipState) {
            FriendshipState.NONE -> "Send request"
            FriendshipState.OUTGOING_PENDING -> "Sent ✓"
            FriendshipState.INCOMING_PENDING -> "Accept"
            FriendshipState.ACCEPTED -> "Unfriend"
        }
        val primaryEnabled = friendshipState != FriendshipState.OUTGOING_PENDING
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                enabled = primaryEnabled,
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            when (friendshipState) {
                                FriendshipState.NONE -> repository.sendFriendRequest(s.id)
                                FriendshipState.INCOMING_PENDING ->
                                    repository.respondToFriendRequest(s.id, accept = true)
                                FriendshipState.ACCEPTED -> repository.unfriend(s.id)
                                FriendshipState.OUTGOING_PENDING -> Result.success(Unit)
                            }
                        }
                        result.onSuccess {
                            val msg = when (friendshipState) {
                                FriendshipState.NONE -> "Friend request sent"
                                FriendshipState.INCOMING_PENDING -> "Friend request accepted"
                                FriendshipState.ACCEPTED -> "Unfriended"
                                FriendshipState.OUTGOING_PENDING -> "Request already sent"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            friendshipState = withContext(Dispatchers.IO) {
                                repository.getFriendshipStates(listOf(s.id))
                                    .getOrDefault(emptyMap())[s.id] ?: FriendshipState.NONE
                            }
                        }.onFailure {
                            Toast.makeText(context, "Action failed right now.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(primaryLabel) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.blockUser(s.id) }
                            .onSuccess {
                                Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            .onFailure { Toast.makeText(context, "Couldn't block right now.", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Block") }
        }
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { repository.reportUser(s.id, "Reported from profile") }
                        .onSuccess { Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Couldn't send report right now.", Toast.LENGTH_SHORT).show() }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
        ) { Text("Report user", color = MaterialTheme.colorScheme.error) }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("HOSTED EVENTS")
        if (hostedSorted.isEmpty()) {
            EmptySectionCard("No hosted events yet")
        } else {
            hostedSorted.take(4).forEach { ev ->
                PublicEventRowCard(
                    title = ev.title,
                    subtitle = "${formatEventDateNoTime(ev.startsAt)}${ev.location?.let { " · $it" } ?: ""}"
                )
            }
            if (hostedSorted.size > 4) {
                PublicEventRowCard(
                    title = "+ ${hostedSorted.size - 4} more events",
                    subtitle = "Tap to see full history"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("ATTENDED EVENTS")
        if (attendedSorted.isEmpty()) {
            EmptySectionCard("No attended events yet")
        } else {
            attendedSorted.take(4).forEach { ev ->
                PublicEventRowCard(
                    title = ev.title,
                    subtitle = "${formatEventDateNoTime(ev.startsAt)} · hosted by friend"
                )
            }
            if (attendedSorted.size > 4) {
                PublicEventRowCard(
                    title = "+ ${attendedSorted.size - 4} more events",
                    subtitle = "Tap to see full history"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionTitle("FRIENDS")
        if (friendsSorted.isEmpty()) {
            EmptySectionCard("No friends yet")
        } else {
            friendsSorted.take(2).forEach { f ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(f.fullName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${f.username}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (friendsSorted.size > 2) {
                PublicEventRowCard(
                    title = "${friendsSorted.size - 2} more friends",
                    subtitle = "Tap to see all"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun PresenceStatusDot(status: PresenceStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(status.dotColor)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

@Composable
private fun PublicStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun EmptySectionCard(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        )
    }
}

@Composable
private fun PublicEventRowCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
