package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EventChatScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { EventRepository() }
    var messages by remember { mutableStateOf<List<EventChatMessageUi>>(emptyList()) }
    var presence by remember { mutableStateOf<List<ChatPresenceUi>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("all_members") }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var eventTitle by remember { mutableStateOf("Event chat") }
    val listState = rememberLazyListState()
    fun applyIfChanged(next: List<EventChatMessageUi>) {
        val currentLast = messages.lastOrNull()?.id
        val nextLast = next.lastOrNull()?.id
        if (messages.size != next.size || currentLast != nextLast) {
            messages = next
        }
    }

    LaunchedEffect(eventId) {
        currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        withContext(Dispatchers.IO) { repo.getEvent(eventId) }
            .onSuccess { eventTitle = it.title }
        mode = withContext(Dispatchers.IO) { repo.getEventChatMode(eventId) }.getOrDefault("all_members")
        repo.subscribeEventChatMessages(eventId).collect { result ->
            result.onSuccess { applyIfChanged(it) }
        }
    }
    // Always-on backup poll keeps messages live even when realtime succeeds silently.
    LaunchedEffect(eventId) {
        while (true) {
            delay(3000L)
            withContext(Dispatchers.IO) { repo.listEventChatMessages(eventId) }
                .onSuccess { applyIfChanged(it) }
        }
    }
    LaunchedEffect("presence-$eventId") {
        repo.subscribeEventChatPresence(eventId).collect { result ->
            result.onSuccess { presence = it }
        }
    }
    // Auto-scroll to the latest message whenever a new one arrives.
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    "EVENT CHAT",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    eventTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (presence.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presence.take(4).forEach { member ->
                    ChatAvatar(
                        name = member.displayName,
                        avatarUrl = member.avatarUrl,
                        contentDescription = "${member.displayName} profile avatar"
                    )
                }
                if (presence.size > 4) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "+${presence.size - 4}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            when (mode) {
                                "host_cohosts_only" -> "Only host/co-host can send messages"
                                "disabled" -> "Chat is disabled for this event"
                                else -> "All event members can chat"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        if (presence.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "In chat: " + presence.joinToString(", ") {
                                    when (it.role) {
                                        "host" -> "${it.displayName} (Host)"
                                        "cohost" -> "${it.displayName} (Co-host)"
                                        else -> it.displayName
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AnimatedMessageRow {
                            val mine = currentUserId != null && msg.senderId == currentUserId
                            ChatMessageBubble(
                                mine = mine,
                                senderName = msg.senderName,
                                body = msg.body
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Message...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Button(
                        onClick = {
                            val outgoing = draft.trim()
                            if (outgoing.isBlank()) return@Button
                            scope.launch {
                                draft = ""
                                withContext(Dispatchers.IO) { repo.sendEventChatMessage(eventId, outgoing) }
                                    .onSuccess {
                                        withContext(Dispatchers.IO) { repo.listEventChatMessages(eventId) }
                                            .onSuccess { applyIfChanged(it) }
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.lastIndex)
                                        }
                                    }
                                    .onFailure {
                                        draft = outgoing
                                        Toast.makeText(context, it.message ?: "Couldn't send message.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.height(54.dp)
                    ) { Text("Send") }
                }
            }
        }
    }
}
