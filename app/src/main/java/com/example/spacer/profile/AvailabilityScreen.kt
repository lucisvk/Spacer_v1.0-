package com.example.spacer.profile

import android.widget.Toast
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult

@Composable
fun AvailabilityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { AvailabilityRepository() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var loading by remember { mutableStateOf(true) }
    var tabWeekly by remember { mutableStateOf(true) }
    var calendarConnected by remember { mutableStateOf(false) }
    var calendarProvider by remember { mutableStateOf<String?>(null) }
    var showToFriendsOnly by remember { mutableStateOf(true) }
    var autoDeclineConflicts by remember { mutableStateOf(false) }
    var weeklyWindows by remember { mutableStateOf<List<WeeklyAvailabilityWindowUi>>(emptyList()) }
    var specificWindows by remember { mutableStateOf<List<SpecificAvailabilityWindowUi>>(emptyList()) }
    // Calendar picker state — opens when the user taps Connect.
    var showCalendarPicker by remember { mutableStateOf(false) }
    var calendarAccounts by remember {
        mutableStateOf<List<com.example.spacer.calendar.DeviceCalendarBusyChecker.CalendarAccount>>(emptyList())
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            calendarAccounts = com.example.spacer.calendar.DeviceCalendarBusyChecker
                .listDeviceCalendarAccounts(context)
        } else {
            showCalendarPicker = false
            Toast.makeText(
                context,
                "Calendar access is required to connect a calendar.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) { repo.loadPreferences() }
            .onSuccess {
                calendarConnected = it.calendarConnected
                calendarProvider = it.calendarProvider
                showToFriendsOnly = it.showToFriendsOnly
                autoDeclineConflicts = it.autoDeclineConflicts
            }
        withContext(Dispatchers.IO) { repo.listWeeklyWindows() }
            .onSuccess { weeklyWindows = it }
        withContext(Dispatchers.IO) { repo.listSpecificAvailability() }
            .onSuccess { specificWindows = it }
        loading = false
    }

    fun copyMondayToAll() {
        scope.launch {
            val mondaySlots = weeklyWindows.filter { it.dayOfWeek == DayOfWeek.Mon.dbValue }
            if (mondaySlots.isEmpty()) {
                Toast.makeText(context, "Set Monday hours first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                DayOfWeek.entries.filter { it != DayOfWeek.Mon }.forEach { day ->
                    weeklyWindows.filter { it.dayOfWeek == day.dbValue }.forEach { repo.removeWeeklyWindow(it.id) }
                    mondaySlots.forEach { slot ->
                        val start = runCatching { LocalTime.parse(slot.startsAt) }.getOrElse { LocalTime.of(18, 0) }
                        val end = runCatching { LocalTime.parse(slot.endsAt) }.getOrElse { LocalTime.of(22, 0) }
                        repo.addWeeklyWindow(day.dbValue, start, end)
                    }
                }
            }
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "TIME & CALENDAR",
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                ) { append("Avail") }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light
                    )
                ) { append("ability") }
            },
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.4).sp
        )
        Spacer(modifier = Modifier.height(2.dp))

        SectionLabel("CALENDAR SYNC")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column {
                            Text("Google Calendar", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(if (calendarConnected) Color(0xFF59D991) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
                                )
                                Text(
                                    if (calendarConnected) "Connected" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (calendarConnected) Color(0xFF59D991) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (calendarConnected) {
                                // Already connected — disconnect immediately.
                                calendarConnected = false
                                calendarProvider = null
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repo.savePreferences(
                                            calendarProvider = null,
                                            calendarConnected = false,
                                            showToFriendsOnly = showToFriendsOnly,
                                            autoDeclineConflicts = autoDeclineConflicts
                                        )
                                    }
                                    Toast.makeText(context, "Calendar disconnected", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Not connected yet — open the account picker.
                                showCalendarPicker = true
                            }
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(if (calendarConnected) "Disconnect" else "Connect")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            "Spacer reads your busy times and adds events you join to your calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        com.example.spacer.ui.theme.SpacerSegRadio(
            options = listOf("Weekly hours", "Specific dates"),
            selected = if (tabWeekly) 0 else 1,
            onSelected = { tabWeekly = it == 0 },
            modifier = Modifier.fillMaxWidth()
        )

        if (tabWeekly) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("WHEN YOU'RE USUALLY FREE")
                Text(
                    "+ Copy to all",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.clickable { copyMondayToAll() }
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DayOfWeek.entries.forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        val dayRows = weeklyWindows.filter { it.dayOfWeek == day.dbValue }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                day.shortLabel,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (dayRows.isEmpty()) {
                                    Text(
                                        "Unavailable",
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                dayRows.forEach { row ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                            modifier = Modifier.clickable {
                                                val start = runCatching { LocalTime.parse(row.startsAt) }.getOrElse { LocalTime.of(18, 0) }
                                                val end = runCatching { LocalTime.parse(row.endsAt) }.getOrElse { LocalTime.of(22, 0) }
                                                showTimeRangePicker(
                                                    context = context,
                                                    initialStart = start,
                                                    initialEnd = end
                                                ) { updatedStart, updatedEnd ->
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            repo.removeWeeklyWindow(row.id)
                                                            repo.addWeeklyWindow(day.dbValue, updatedStart, updatedEnd)
                                                        }
                                                        reload()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                "${formatClock(row.startsAt)} - ${formatClock(row.endsAt)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { repo.removeWeeklyWindow(row.id) }
                                                    reload()
                                                }
                                            },
                                            shape = RoundedCornerShape(999.dp)
                                        ) { Text("x") }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    showTimeRangePicker(
                                        context = context,
                                        initialStart = LocalTime.of(18, 0),
                                        initialEnd = LocalTime.of(22, 0)
                                    ) { start, end ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repo.addWeeklyWindow(day.dbValue, start, end) }
                                            reload()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(999.dp)
                            ) { Text("+") }
                        }
                    }
                }
            }

            SectionLabel("THIS WEEK'S PREVIEW")
            WeeklyPreviewCard(weeklyWindows)
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showDateTimeRangePicker(context) { start, end ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repo.addSpecificAvailability(
                                                startsAt = start,
                                                endsAt = end,
                                                isAvailable = false,
                                                note = "Calendar busy"
                                            )
                                        }
                                        reload()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp)
                        ) { Text("Add busy block") }

                        OutlinedButton(
                            onClick = {
                                showDateTimeRangePicker(context) { start, end ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repo.addSpecificAvailability(
                                                startsAt = start,
                                                endsAt = end,
                                                isAvailable = true,
                                                note = "Manual available override"
                                            )
                                        }
                                        reload()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp)
                        ) { Text("Add available") }
                    }

                    if (specificWindows.isEmpty()) {
                        Text(
                            "No specific-date overrides yet.",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        specificWindows.forEach { override ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        formatSpecificDate(override.startsAt),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        "${formatSpecificTime(override.startsAt)} - ${formatSpecificTime(override.endsAt)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        if (override.isAvailable) "Available override" else "Busy block",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (override.isAvailable) Color(0xFF59D991) else Color(0xFFE08B9C)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val initialStart = parseInstantOrNow(override.startsAt)
                                                .atZone(ZoneId.systemDefault()).toLocalDateTime()
                                            val initialEnd = parseInstantOrNow(override.endsAt)
                                                .atZone(ZoneId.systemDefault()).toLocalDateTime()
                                            showDateTimeRangePicker(
                                                context = context,
                                                initialStart = initialStart,
                                                initialEnd = initialEnd
                                            ) { start, end ->
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repo.removeSpecificAvailability(override.id)
                                                        repo.addSpecificAvailability(
                                                            startsAt = start,
                                                            endsAt = end,
                                                            isAvailable = override.isAvailable,
                                                            note = override.note
                                                        )
                                                    }
                                                    reload()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) { Text("Edit") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { repo.removeSpecificAvailability(override.id) }
                                                reload()
                                            }
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) { Text("x") }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        SectionLabel("PRIVACY")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show times to friends only", style = MaterialTheme.typography.bodyMedium)
                        Text("Strangers see \"ask host\" instead", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = showToFriendsOnly, onCheckedChange = { showToFriendsOnly = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-decline conflicts", style = MaterialTheme.typography.bodyMedium)
                        Text("Politely RSVP no when calendar is busy", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoDeclineConflicts, onCheckedChange = { autoDeclineConflicts = it })
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repo.savePreferences(
                            calendarProvider = calendarProvider,
                            calendarConnected = calendarConnected,
                            showToFriendsOnly = showToFriendsOnly,
                            autoDeclineConflicts = autoDeclineConflicts
                        )
                    }
                    if (result.isSuccess) {
                        Toast.makeText(context, "Availability saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) { Text("Save changes") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(999.dp)) {
            Text("Back")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showCalendarPicker) {
        // Triggered when the Connect button is tapped. Reload the account list
        // every time the picker opens so newly added accounts on the device show up.
        LaunchedEffect(showCalendarPicker) {
            val granted = androidx.core.content.PermissionChecker.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == androidx.core.content.PermissionChecker.PERMISSION_GRANTED
            if (granted) {
                calendarAccounts = com.example.spacer.calendar.DeviceCalendarBusyChecker
                    .listDeviceCalendarAccounts(context)
            } else {
                calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            }
        }
        CalendarAccountPickerDialog(
            accounts = calendarAccounts,
            onDismiss = { showCalendarPicker = false },
            onSelected = { account ->
                showCalendarPicker = false
                val nextProvider = if (account.accountType.isNotBlank()) {
                    "${account.accountType}:${account.accountName}"
                } else {
                    account.accountName
                }
                calendarConnected = true
                calendarProvider = nextProvider
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        repo.savePreferences(
                            calendarProvider = nextProvider,
                            calendarConnected = true,
                            showToFriendsOnly = showToFriendsOnly,
                            autoDeclineConflicts = autoDeclineConflicts
                        )
                    }
                    r.onFailure {
                        calendarConnected = false
                        calendarProvider = null
                        Toast.makeText(
                            context,
                            "Couldn't save your calendar choice. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onSuccess {
                        Toast.makeText(
                            context,
                            "Connected to ${account.displayName.ifBlank { account.accountName }}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

/**
 * Lists the device's calendar accounts so the user can pick which one to
 * connect to Spacer. Account names typically read as the email address for
 * Google / Outlook / Exchange / iCloud calendars.
 */
@Composable
private fun CalendarAccountPickerDialog(
    accounts: List<com.example.spacer.calendar.DeviceCalendarBusyChecker.CalendarAccount>,
    onDismiss: () -> Unit,
    onSelected: (com.example.spacer.calendar.DeviceCalendarBusyChecker.CalendarAccount) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a calendar") },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text(
                        text = "No calendar accounts found on this device. " +
                            "Add a Google, Outlook, or other calendar account in your phone settings, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                } else {
                    Text(
                        "Pick the calendar Spacer should read busy times from.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(10.dp))
                    val byAccount = accounts.groupBy { it.accountName.ifBlank { it.accountType } }
                    byAccount.forEach { (account, calendars) ->
                        val first = calendars.first()
                        val label = first.displayName.takeIf { it.isNotBlank() && it != account }
                            ?.let { "$account · $it" } ?: account
                        val providerLabel = friendlyProviderLabel(first.accountType)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelected(first) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    providerLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(if (accounts.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

private fun friendlyProviderLabel(accountType: String): String = when {
    accountType.contains("google", ignoreCase = true) -> "Google Calendar"
    accountType.contains("exchange", ignoreCase = true) -> "Outlook / Exchange"
    accountType.contains("samsung", ignoreCase = true) -> "Samsung Calendar"
    accountType.contains("local", ignoreCase = true) -> "Device calendar"
    accountType.isBlank() -> "Calendar"
    else -> accountType
}

@Suppress("unused")
@Composable
private fun SegmentedTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.2.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
    )
}

@Suppress("unused")
@Composable
private fun OldSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
private fun WeeklyPreviewCard(weeklyWindows: List<WeeklyAvailabilityWindowUi>) {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEachIndexed { index, day ->
                    val freeHours = weeklyWindows.filter { it.dayOfWeek == day.dbValue }.sumOf { slot ->
                        val start = runCatching { LocalTime.parse(slot.startsAt) }.getOrNull() ?: return@sumOf 0.0
                        val end = runCatching { LocalTime.parse(slot.endsAt) }.getOrNull() ?: return@sumOf 0.0
                        ChronoUnit.MINUTES.between(start, end).toDouble() / 60.0
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(day.shortLabel.uppercase(), style = MaterialTheme.typography.labelSmall)
                        Text(monday.plusDays(index.toLong()).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (freeHours > 0) Color(0xFF7B5BFF) else Color(0xFF5A4D76),
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (freeHours > 0) Color(0xFF6D4BFF)
                                    else Color(0xFF2A1F45),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                        Text(
                            if (freeHours > 0) "${freeHours.toInt()}h free" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (freeHours > 0) Color(0xFF63F5A2) else Color(0xFFB7AFCB)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LegendDot(Color(0xFF6D4BFF), "Free")
                Spacer(modifier = Modifier.size(12.dp))
                LegendDot(Color(0xFFE06B8E), "Calendar busy")
                Spacer(modifier = Modifier.size(12.dp))
                LegendDot(Color(0xFF2A1F45), "Off")
            }
        }
    }
}

private fun showTimeRangePicker(
    context: Context,
    initialStart: LocalTime,
    initialEnd: LocalTime,
    onPicked: (LocalTime, LocalTime) -> Unit
) {
    TimePickerDialog(
        context,
        { _, startHour, startMinute ->
            val start = LocalTime.of(startHour, startMinute)
            TimePickerDialog(
                context,
                { _, endHour, endMinute ->
                    val end = LocalTime.of(endHour, endMinute)
                    if (end.isAfter(start)) {
                        onPicked(start, end)
                    } else {
                        Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                    }
                },
                initialEnd.hour,
                initialEnd.minute,
                false
            ).apply { setTitle("End time") }.show()
        },
        initialStart.hour,
        initialStart.minute,
        false
    ).apply { setTitle("Start time") }.show()
}

private fun showDateTimeRangePicker(
    context: Context,
    initialStart: LocalDateTime = LocalDateTime.now().withHour(18).withMinute(0),
    initialEnd: LocalDateTime = LocalDateTime.now().withHour(22).withMinute(0),
    onPicked: (Instant, Instant) -> Unit
) {
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val pickedDate = LocalDate.of(year, month + 1, day)
            TimePickerDialog(
                context,
                { _, startHour, startMinute ->
                    val startDateTime = pickedDate.atTime(startHour, startMinute)
                    TimePickerDialog(
                        context,
                        { _, endHour, endMinute ->
                            val endDateTime = pickedDate.atTime(endHour, endMinute)
                            if (endDateTime.isAfter(startDateTime)) {
                                onPicked(
                                    startDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                                    endDateTime.atZone(ZoneId.systemDefault()).toInstant()
                                )
                            } else {
                                Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                            }
                        },
                        initialEnd.hour,
                        initialEnd.minute,
                        false
                    ).apply { setTitle("End time") }.show()
                },
                initialStart.hour,
                initialStart.minute,
                false
            ).apply { setTitle("Start time") }.show()
        },
        initialStart.year,
        initialStart.monthValue - 1,
        initialStart.dayOfMonth
    ).show()
}

private fun parseInstantOrNow(raw: String): Instant {
    return runCatching { Instant.parse(raw) }.getOrElse { Instant.now() }
}

private fun formatSpecificDate(raw: String): String {
    val instant = parseInstantOrNow(raw)
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return local.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
}

private fun formatSpecificTime(raw: String): String {
    val instant = parseInstantOrNow(raw)
    val local = instant.atZone(ZoneId.systemDefault()).toLocalTime()
    return local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

private enum class DayOfWeek(val dbValue: Int, val shortLabel: String) {
    Mon(1, "Mon"),
    Tue(2, "Tue"),
    Wed(3, "Wed"),
    Thu(4, "Thu"),
    Fri(5, "Fri"),
    Sat(6, "Sat"),
    Sun(7, "Sun")
}

private fun formatClock(raw: String): String {
    val parsed = runCatching { LocalTime.parse(raw) }.getOrNull() ?: return raw
    return parsed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}
