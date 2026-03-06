package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.ui.theme.SpacerCard
import com.example.spacer.ui.theme.SpacerSectionLabel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.Navigation.AppRoutes
import com.example.spacer.network.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHostedEvents: () -> Unit,
    onOpenAttendedEvents: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAvailability: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }
    val sessionPrefs = remember { SessionPrefs(context) }
    val scrollState = rememberScrollState()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val avatarSize = when {
        screenWidth < 360 -> 78.dp
        screenWidth > 420 -> 104.dp
        else -> 92.dp
    }

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    var hostedCount by remember { mutableStateOf(0) }
    var attendedCount by remember { mutableStateOf(0) }
    var friendsCount by remember { mutableStateOf(0) }
    var presenceStatus by remember { mutableStateOf(PresenceStatus.fromDb(sessionPrefs.getPresenceStatus())) }
    var showPresenceMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadFromCache() {
        val cachedName = sessionPrefs.getProfileName().trim()
        if (username.isBlank() && fullName.isBlank()) {
            fullName = cachedName
            username = cachedName
        }
        avatarUrl = avatarUrl ?: sessionPrefs.getProfileImageUri()
        hostedCount = sessionPrefs.getHostedCount()
        attendedCount = sessionPrefs.getAttendedCount()
        friendsCount = sessionPrefs.getFriendsCount()
    }

    suspend fun loadProfile() {
        isLoading = true
        loadError = null
        val result = withContext(Dispatchers.IO) { repository.load() }
        result
            .onSuccess { snapshot ->
                val cachedAvatar = sessionPrefs.getProfileImageUri()
                val resolvedAvatar = snapshot.profile.avatarUrl?.takeIf { it.isNotBlank() } ?: cachedAvatar
                fullName = snapshot.profile.fullName.orEmpty()
                username = snapshot.profile.username.orEmpty()
                email = snapshot.profile.email.orEmpty()
                avatarUrl = resolvedAvatar

                hostedCount = snapshot.stats.hostedCount
                attendedCount = snapshot.stats.attendedCount
                friendsCount = snapshot.stats.friendsCount
                presenceStatus = PresenceStatus.fromDb(snapshot.profile.presenceStatus)
                val label = displayLabelFromProfile(snapshot.profile)
                if (label.isNotBlank()) {
                    sessionPrefs.saveProfileName(label)
                }
                sessionPrefs.savePresenceStatus(presenceStatus.dbValue)
                sessionPrefs.saveProfileSnapshot(
                    profileName = label.ifBlank { username.ifBlank { fullName } },
                    aboutMe = snapshot.profile.aboutMe.orEmpty(),
                    profileImageUri = resolvedAvatar,
                    hostedCount = hostedCount,
                    attendedCount = attendedCount,
                    friendsCount = friendsCount
                )
            }
            .onFailure { e ->
                loadError = com.example.spacer.events.friendlyErrorMessage(
                    e,
                    fallback = "Couldn't load your profile right now."
                )
                loadFromCache()
            }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadFromCache()
        loadProfile()
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == AppRoutes.Profile) {
                scope.launch { loadProfile() }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                loadProfile()
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
        Text(
            text = "ACCOUNT",
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Profile",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val selectedImageUri = avatarUrl

                if (selectedImageUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showPresenceMenu = true }
                    ) {
                        PresenceDot(
                            status = presenceStatus,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clickable { showPresenceMenu = true }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = Uri.parse(selectedImageUri)),
                            contentDescription = "Profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        PresenceDot(
                            status = presenceStatus,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
                if (showPresenceMenu) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Set status",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            PresenceStatus.entries.forEach { option ->
                                val selected = option == presenceStatus
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .clickable {
                                            showPresenceMenu = false
                                            if (option == presenceStatus) return@clickable
                                            val previous = presenceStatus
                                            // Optimistic update so the UI reflects the change immediately.
                                            presenceStatus = option
                                            sessionPrefs.savePresenceStatus(option.dbValue)
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    repository.updatePresenceStatus(option)
                                                }
                                                result.onFailure {
                                                    presenceStatus = previous
                                                    sessionPrefs.savePresenceStatus(previous.dbValue)
                                                    Toast.makeText(
                                                        context,
                                                        "Couldn't update your status. Please try again.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PresenceDot(status = option, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val displayName = when {
                    isLoading -> ""
                    username.trim().isNotEmpty() -> username.trim()
                    fullName.trim().isNotEmpty() -> fullName.trim()
                    email.trim().isNotEmpty() -> email.trim().substringBefore("@")
                    else -> ""
                }
                val accountLabel = email.trim().takeIf { it.isNotEmpty() }?.substringBefore("@")
                val subtitle = when {
                    isLoading -> ""
                    username.isBlank() -> ""
                    fullName.isNotBlank() && !fullName.trim().equals(username.trim(), ignoreCase = true) ->
                        fullName.trim()
                    else -> ""
                }
                Text(
                    text = if (isLoading) {
                        "Loading..."
                    } else {
                        displayName.ifBlank { accountLabel ?: "Account" }
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresenceDot(status = presenceStatus, modifier = Modifier.size(10.dp))
                        Text(
                            text = presenceStatus.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatCard(
                value = hostedCount.toString(),
                label = "Hosted",
                modifier = Modifier.weight(1f),
                onClick = onOpenHostedEvents
            )
            ProfileStatCard(
                value = attendedCount.toString(),
                label = "Attended",
                modifier = Modifier.weight(1f),
                onClick = onOpenAttendedEvents
            )
            ProfileStatCard(
                value = friendsCount.toString(),
                label = "Friends",
                modifier = Modifier.weight(1f),
                onClick = onOpenFriends
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "ACCOUNT",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 4.dp) {
            Column {
                MenuRow("Edit Profile", onClick = onOpenEditProfile)
                MenuRow("Settings", onClick = onOpenSettings)
                MenuRow("Availability", onClick = onOpenAvailability)
                MenuRow("Messages", onClick = onOpenMessages)
                MenuRow("Change Password", last = true)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "SUPPORT",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 4.dp) {
            Column {
                MenuRow("Help & Support", last = true)
            }
        }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun PresenceDot(status: PresenceStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(status.dotColor)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

@Composable
private fun ProfileStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun MenuRow(title: String, onClick: (() -> Unit)? = null, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable { onClick() } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (!last) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
