package com.example.spacer.profile

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.PermissionChecker
import com.example.spacer.calendar.DeviceCalendarBusyChecker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.network.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CalendarPermissionPending {
    None,
    DeviceRead,
    ConflictNotify
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var calendarSharingEnabled by remember {
        mutableStateOf(sessionPrefs.isCalendarAvailabilitySharingEnabled())
    }
    var calendarConflictNotify by remember {
        mutableStateOf(sessionPrefs.isCalendarConflictNotificationsEnabled())
    }
    var deviceCalendarRead by remember {
        mutableStateOf(sessionPrefs.isDeviceCalendarReadEnabled())
    }
    var eventLocationSharing by remember {
        mutableStateOf(sessionPrefs.isEventLocationSharingEnabled())
    }
    var pendingCalPermission by remember { mutableStateOf(CalendarPermissionPending.None) }
    var isRefreshing by remember { mutableStateOf(false) }
    val profileImageUri = remember { sessionPrefs.getProfileImageUri() }
    val presence = remember { PresenceStatus.fromDb(sessionPrefs.getPresenceStatus()) }

    val readCalendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when (pendingCalPermission) {
            CalendarPermissionPending.DeviceRead -> {
                if (granted) {
                    sessionPrefs.setDeviceCalendarReadEnabled(true)
                    deviceCalendarRead = true
                } else {
                    sessionPrefs.setDeviceCalendarReadEnabled(false)
                    deviceCalendarRead = false
                    Toast.makeText(
                        context,
                        "Calendar permission is required to read busy times on this device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            CalendarPermissionPending.ConflictNotify -> {
                if (granted) {
                    sessionPrefs.setCalendarConflictNotificationsEnabled(true)
                    calendarConflictNotify = true
                } else {
                    Toast.makeText(
                        context,
                        "Calendar permission is required for conflict notifications.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            CalendarPermissionPending.None -> Unit
        }
        pendingCalPermission = CalendarPermissionPending.None
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                calendarSharingEnabled = sessionPrefs.isCalendarAvailabilitySharingEnabled()
                calendarConflictNotify = sessionPrefs.isCalendarConflictNotificationsEnabled()
                deviceCalendarRead = sessionPrefs.isDeviceCalendarReadEnabled()
                eventLocationSharing = sessionPrefs.isEventLocationSharingEnabled()
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
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "PREFERENCES",
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(52.dp)) {
                    if (profileImageUri.isNullOrBlank()) {
                        Spacer(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                        )
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(model = Uri.parse(profileImageUri)),
                            contentDescription = "Profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(presence.dotColor)
                            .border(1.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                    )
                }
                Column {
                    Text(sessionPrefs.getProfileName().ifBlank { "User" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        presence.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "APPEARANCE",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Light mode")
                        Text(
                            if (isDarkTheme) "Currently disabled" else "Currently enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "CALENDAR & AVAILABILITY",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Let hosts see when you’re generally free on invites, and optionally warn you if a time clashes with your calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Share availability with hosts",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (calendarSharingEnabled) "Connected — hosts can see your saved invite availability." else "Disconnected — availability won’t be saved for invites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = calendarSharingEnabled,
                        onCheckedChange = { on ->
                            calendarSharingEnabled = on
                            sessionPrefs.setCalendarAvailabilitySharingEnabled(on)
                            if (!on) {
                                calendarConflictNotify = false
                                sessionPrefs.setCalendarConflictNotificationsEnabled(false)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Read calendar busy times",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when {
                                !deviceCalendarRead ->
                                    "Off — Spacer won’t read Google / Apple calendars on this phone for overlaps."
                                PermissionChecker.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CALENDAR
                                ) != PermissionChecker.PERMISSION_GRANTED ->
                                    "Turned on in Spacer — allow Calendar access in system settings to finish setup."
                                else ->
                                    "On — Spacer can compare invites to busy times on this device (read‑only)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = deviceCalendarRead,
                        onCheckedChange = { on ->
                            if (on) {
                                val hasPerm = DeviceCalendarBusyChecker.hasReadCalendarPermission(context)
                                if (hasPerm) {
                                    sessionPrefs.setDeviceCalendarReadEnabled(true)
                                    deviceCalendarRead = true
                                } else {
                                    pendingCalPermission = CalendarPermissionPending.DeviceRead
                                    readCalendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            } else {
                                sessionPrefs.setDeviceCalendarReadEnabled(false)
                                deviceCalendarRead = false
                                calendarConflictNotify = sessionPrefs.isCalendarConflictNotificationsEnabled()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Conflict notifications",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Notify when a pending invite overlaps busy time on this device’s calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = calendarConflictNotify,
                        enabled = calendarSharingEnabled && deviceCalendarRead,
                        onCheckedChange = { on ->
                            if (on && !DeviceCalendarBusyChecker.hasReadCalendarPermission(context)) {
                                pendingCalPermission = CalendarPermissionPending.ConflictNotify
                                readCalendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                                return@Switch
                            }
                            calendarConflictNotify = on
                            sessionPrefs.setCalendarConflictNotificationsEnabled(on)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "EVENT LOCATION",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Share location for events",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (eventLocationSharing) {
                                "On — friends can see your pin when you enable event tracking."
                            } else {
                                "Off — your location stays private and your pin is hidden."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = eventLocationSharing,
                        onCheckedChange = { on ->
                            eventLocationSharing = on
                            sessionPrefs.setEventLocationSharingEnabled(on)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "ACCOUNT",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SettingsNavRow("See blocked users", onOpenBlockedUsers)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                SettingsNavRow("Back to profile", onBack)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                SettingsNavRow("Log out", onLogout)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Delete account")
        }
    }
    PullRefreshIndicator(
        refreshing = isRefreshing,
        state = pullRefreshState,
        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
    )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account") },
            text = {
                Column {
                    Text("This creates an account deletion request and logs you out.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repository.requestAccountDeletion(deletionReason)
                            }
                            result.onSuccess {
                                Toast.makeText(context, "Deletion request submitted", Toast.LENGTH_LONG).show()
                                showDeleteDialog = false
                                onLogout()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Failed to submit deletion request",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Submit") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title)
        Text("›", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}

