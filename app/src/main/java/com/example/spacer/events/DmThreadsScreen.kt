package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import com.example.spacer.ui.theme.SpacerCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DmThreadsScreen(
    onOpenThread: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { EventRepository() }
    var threads by remember { mutableStateOf<List<DmThreadUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { repo.listDmThreads() }
            .onSuccess { threads = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Couldn't load messages.", Toast.LENGTH_SHORT).show()
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp)
        ) {
            Text(
                text = "INBOX",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Messages",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp
            )
            Spacer(Modifier.height(20.dp))
            if (threads.isEmpty()) {
                SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 22.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✉️", fontSize = 28.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No conversations yet",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Once you RSVP to events, group threads will show up here automatically.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(threads, key = { it.conversationId }) { item ->
                        DmThreadRow(
                            item = item,
                            onClick = { onOpenThread(item.conversationId, item.peerId) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SpacerButton(
                label = "Back",
                onClick = onBack,
                kind = SpacerButtonKind.Secondary,
                size = SpacerButtonSize.Md,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DmThreadRow(item: DmThreadUi, onClick: () -> Unit) {
    SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 14.dp,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatAvatar(
                name = item.peerName,
                avatarUrl = item.peerAvatarUrl,
                contentDescription = "Peer avatar"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.peerName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val previewLine = item.lastMessagePreview?.let { msg ->
                    val sender = item.lastMessageSenderName ?: "User"
                    "$sender: \"$msg\""
                } ?: "No messages yet"
                Text(
                    previewLine,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
