package com.example.spacer.events

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DmChatScreen(
    peerUserId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { EventRepository() }
    val profileRepo = remember { ProfileRepository() }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var peerName by remember { mutableStateOf("User") }
    var peerAvatarUrl by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<DmMessageUi>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showedRealtimeFallbackToast by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    fun applyIfChanged(next: List<DmMessageUi>) {
        val currentLast = messages.lastOrNull()?.id
        val nextLast = next.lastOrNull()?.id
        if (messages.size != next.size || currentLast != nextLast) {
            messages = next
        }
    }

    LaunchedEffect(peerUserId) {
        currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        withContext(Dispatchers.IO) { profileRepo.getPublicProfile(peerUserId) }
            .onSuccess {
                peerName = it.fullName
                peerAvatarUrl = it.avatarUrl
            }
        val cid = withContext(Dispatchers.IO) { repo.getOrCreateDmConversation(peerUserId) }.getOrNull()
        if (cid == null) {
            Toast.makeText(context, "Can't open DM with this user.", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        conversationId = cid
        repo.subscribeDmMessages(cid).collect { result ->
            result.onSuccess { applyIfChanged(it) }
            result.onFailure {
                if (!showedRealtimeFallbackToast) {
                    showedRealtimeFallbackToast = true
                    Toast.makeText(context, "Realtime unavailable, using backup refresh.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect("dm-poll-$peerUserId") {
        while (true) {
            val cid = conversationId
            if (cid != null) {
                withContext(Dispatchers.IO) { repo.listDmMessages(cid) }
                    .onSuccess { applyIfChanged(it) }
            }
            delay(1500L)
        }
    }
    LaunchedEffect(messages.size) {
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
            ChatAvatar(name = peerName, avatarUrl = peerAvatarUrl, contentDescription = "Peer avatar")
            Column {
                Text(
                    "DIRECT MESSAGE",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    peerName,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )
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
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
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
                    val cid = conversationId ?: return@Button
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.sendDmMessage(cid, draft) }
                            .onSuccess {
                                draft = ""
                                withContext(Dispatchers.IO) { repo.listDmMessages(cid) }
                                    .onSuccess { applyIfChanged(it) }
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "Couldn't send DM.", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.height(54.dp)
            ) { Text("Send") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
