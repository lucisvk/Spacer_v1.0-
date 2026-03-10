package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var blockedUsers by remember { mutableStateOf<List<BlockedUserItem>>(emptyList()) }

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) { repository.listBlockedUsers() }
            .onSuccess { blockedUsers = it }
            .onFailure {
                Toast.makeText(context, "Failed to load blocked users.", Toast.LENGTH_LONG).show()
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
            Text(
                "PRIVACY",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Blocked users",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (loading) {
                Text("Loading...")
            } else if (blockedUsers.isEmpty()) {
                Text("You have not blocked anyone.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp)
                ) {
                    items(blockedUsers, key = { it.userId }) { blocked ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(blocked.userId) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (blocked.avatarUrl.isNullOrBlank()) {
                                        Spacer(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    } else {
                                        Image(
                                            painter = rememberAsyncImagePainter(Uri.parse(blocked.avatarUrl)),
                                            contentDescription = "Blocked user avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(blocked.fullName, style = MaterialTheme.typography.titleSmall)
                                        Text("@${blocked.username}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { repository.unblockUser(blocked.userId) }
                                                    .onSuccess {
                                                        Toast.makeText(context, "User unblocked", Toast.LENGTH_SHORT).show()
                                                        reload()
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(context, "Couldn't unblock right now.", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        }
                                    ) { Text("Unblock") }
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
