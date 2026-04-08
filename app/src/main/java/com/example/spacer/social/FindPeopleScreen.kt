package com.example.spacer.social

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.profile.FriendshipState
import com.example.spacer.profile.PresenceStatus
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SearchUserRow
import com.example.spacer.profile.displayName
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import com.example.spacer.ui.theme.SpacerCard
import com.example.spacer.ui.theme.SpacerChip
import com.example.spacer.ui.theme.SpacerChipTone
import com.example.spacer.ui.theme.SpacerSectionLabel
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(FlowPreview::class)
@Composable
fun FindPeopleScreen(
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchUserRow>>(emptyList()) }
    var friendshipStates by remember { mutableStateOf<Map<String, FriendshipState>>(emptyMap()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun refreshFriendshipStates(current: List<SearchUserRow>) {
        val ids = current.map { it.id }
        val states = withContext(Dispatchers.IO) { repository.getFriendshipStates(ids) }
            .getOrDefault(emptyMap())
        friendshipStates = states
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshToken) {
        snapshotFlow { query }
            .debounce(350L)
            .distinctUntilChanged()
            .collectLatest { q ->
                loading = true
                try {
                    val result = withContext(Dispatchers.IO) { repository.searchUsers(q) }
                    result
                        .onSuccess {
                            results = it
                            refreshFriendshipStates(it)
                        }
                        .onFailure {
                            Toast.makeText(context, "Couldn't search right now. Please try again.", Toast.LENGTH_LONG).show()
                            results = emptyList()
                            friendshipStates = emptyMap()
                        }
                } finally {
                    if (coroutineContext.isActive) loading = false
                }
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    loading = loading
                )
            }

            SpacerSectionLabel("Results")

            val trimmed = query.trim()
            val queryReady = trimmed.isNotEmpty()

            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        !queryReady && !loading -> SearchHintCard("Search to discover more people")
                        loading && results.isEmpty() -> SearchHintCard("Searching…")
                        queryReady && !loading && results.isEmpty() -> SearchHintCard(
                            "No users match that search. Try another name, username, or email prefix."
                        )
                        else -> {
                            results.forEach { user ->
                                UserResultCard(
                                    user = user,
                                    onViewProfile = { onOpenProfile(user.id) },
                                    friendshipState = friendshipStates[user.id] ?: FriendshipState.NONE,
                                    onPrimaryAction = {
                                        scope.launch {
                                            val state = friendshipStates[user.id] ?: FriendshipState.NONE
                                            val result = withContext(Dispatchers.IO) {
                                                when (state) {
                                                    FriendshipState.NONE -> repository.sendFriendRequest(user.id)
                                                    FriendshipState.INCOMING_PENDING ->
                                                        repository.respondToFriendRequest(user.id, accept = true)
                                                    FriendshipState.ACCEPTED -> repository.unfriend(user.id)
                                                    FriendshipState.OUTGOING_PENDING -> Result.success(Unit)
                                                }
                                            }
                                            result.onSuccess {
                                                val msg = when (state) {
                                                    FriendshipState.NONE -> "Friend request sent"
                                                    FriendshipState.INCOMING_PENDING -> "Friend request accepted"
                                                    FriendshipState.ACCEPTED -> "Friend removed"
                                                    FriendshipState.OUTGOING_PENDING -> "Request already sent"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                refreshFriendshipStates(results)
                                            }.onFailure {
                                                Toast.makeText(context, "Action failed. Try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onBlock = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repository.blockUser(user.id) }
                                                .onSuccess {
                                                    Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                                    results = results.filterNot { it.id == user.id }
                                                    friendshipStates = friendshipStates - user.id
                                                }
                                                .onFailure {
                                                    Toast.makeText(context, "Couldn't block right now. Try again.", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    },
                                    onReport = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                repository.reportUser(
                                                    targetUserId = user.id,
                                                    reason = "Reported from Find People"
                                                )
                                            }.onSuccess {
                                                Toast.makeText(context, "User report submitted", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "Couldn't submit report right now.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                "Search by name or @handle",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        },
        leadingIcon = { Text("🔍", fontSize = 16.sp) },
        trailingIcon = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
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
private fun SearchHintCard(text: String) {
    SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 22.dp) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UserResultCard(
    user: SearchUserRow,
    friendshipState: FriendshipState,
    onViewProfile: () -> Unit,
    onPrimaryAction: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val avatarSize = when {
        screenWidth < 360 -> 44.dp
        screenWidth > 420 -> 52.dp
        else -> 48.dp
    }
    SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.avatarUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName().take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = user.avatarUrl),
                        contentDescription = "User avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${user.username ?: "user"}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 12.5.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(PresenceStatus.fromDb(user.presenceStatus).dotColor)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = PresenceStatus.fromDb(user.presenceStatus).label,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                }
                FriendshipStateChip(friendshipState)
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpacerButton(
                    label = "View",
                    onClick = onViewProfile,
                    kind = SpacerButtonKind.Primary,
                    size = SpacerButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
                val primaryLabel = when (friendshipState) {
                    FriendshipState.NONE -> "Add"
                    FriendshipState.OUTGOING_PENDING -> "Sent"
                    FriendshipState.INCOMING_PENDING -> "Accept"
                    FriendshipState.ACCEPTED -> "Unfriend"
                }
                SpacerButton(
                    label = primaryLabel,
                    onClick = onPrimaryAction,
                    kind = SpacerButtonKind.Secondary,
                    size = SpacerButtonSize.Sm,
                    enabled = friendshipState != FriendshipState.OUTGOING_PENDING,
                    modifier = Modifier.weight(1f)
                )
                SpacerButton(
                    label = "Block",
                    onClick = onBlock,
                    kind = SpacerButtonKind.Secondary,
                    size = SpacerButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            SpacerButton(
                label = "Report user",
                onClick = onReport,
                kind = SpacerButtonKind.Danger,
                size = SpacerButtonSize.Sm,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FriendshipStateChip(state: FriendshipState) {
    when (state) {
        FriendshipState.NONE -> Unit
        FriendshipState.OUTGOING_PENDING -> SpacerChip(text = "Pending", tone = SpacerChipTone.Default)
        FriendshipState.INCOMING_PENDING -> SpacerChip(text = "Invited you", tone = SpacerChipTone.Gold)
        FriendshipState.ACCEPTED -> SpacerChip(text = "Friend", tone = SpacerChipTone.Purple)
    }
}
