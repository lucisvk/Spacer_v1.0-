package com.example.spacer.events

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.profile.EventRow
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import com.example.spacer.ui.theme.SpacerCard
import com.example.spacer.ui.theme.SpacerChip
import com.example.spacer.ui.theme.SpacerChipSize
import com.example.spacer.ui.theme.SpacerChipTone
import com.example.spacer.ui.theme.SpacerCompactHeader
import com.example.spacer.ui.theme.SpacerDanger
import com.example.spacer.ui.theme.SpacerGold
import com.example.spacer.ui.theme.SpacerLiveGreen
import com.example.spacer.ui.theme.SpacerSectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Hours = listOf("9a","10a","11a","12p","1p","2p","3p","4p","5p","6p","7p","8p","9p","10p")
private val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class GuestAvail(
    val name: String,
    val status: GuestStatus,
    val windowStart: Int?,
    val windowEnd: Int?,
    /** True when the guest is busy specifically at the currently proposed event slot. */
    val conflictsAtProposed: Boolean,
    val notes: String?
)

private enum class GuestStatus { Free, Partial, Conflict, Pending }

@Composable
fun GuestAvailabilityDashboardScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { EventRepository() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var event by remember { mutableStateOf<EventRow?>(null) }
    var availability by remember { mutableStateOf<List<AvailabilityEntryUi>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showRescheduleDialog by remember { mutableStateOf(false) }
    var startsAtDraft by remember { mutableStateOf("") }
    var endsAtDraft by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    suspend fun reload() {
        availability = withContext(Dispatchers.IO) {
            repo.listAvailabilityForHost(eventId).getOrDefault(emptyList())
        }
    }

    LaunchedEffect(eventId) {
        loading = true
        val ev = withContext(Dispatchers.IO) { repo.getEvent(eventId).getOrNull() }
        event = ev
        startsAtDraft = ev?.startsAt.orEmpty()
        endsAtDraft = ev?.endsAt.orEmpty()
        availability = withContext(Dispatchers.IO) {
            repo.listAvailabilityForHost(eventId).getOrElse {
                Toast.makeText(context, it.message ?: "Could not load availability", Toast.LENGTH_LONG).show()
                emptyList()
            }
        }
        loading = false
    }

    val parsed = remember(availability) { availability.map { parseGuest(it) } }
    val total = max(availability.size, 1)
    val freeCount = parsed.count { it.status == GuestStatus.Free }
    val conflictCount = parsed.count { it.status == GuestStatus.Conflict }
    val pendingCount = parsed.count { it.status == GuestStatus.Pending }
    // Derive the "proposed" indices from the actual event window so the dashboard
    // highlights what the host actually chose, not a hard-coded Tuesday slot.
    val proposed = remember(event) { eventWindowToHeatmapIndex(event) }
    val proposedDay = proposed.day
    val proposedFrom = proposed.from
    val proposedTo = proposed.to
    val heatmap = remember(parsed, proposedDay, proposedFrom, proposedTo) {
        computeHeatmap(parsed, Hours.size, Days.size, proposedDay, proposedFrom, proposedTo)
    }
    val bestWindows = remember(heatmap, proposedDay, proposedFrom, proposedTo) {
        rankBestWindows(heatmap, proposedDay, proposedFrom, proposedTo)
    }
    val proposedDayLabel = remember(event) { event?.startsAt?.let { eventDayLabel(it) } ?: "Today" }
    val proposedRangeLabel = remember(event) { event?.let { eventRangeLabel(it.startsAt, it.endsAt) } ?: "" }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SpacerCompactHeader(
                    title = "Guest availability",
                    sub = event?.let { "${it.title} · ${availability.size} replied" } ?: "Loading…",
                    onBack = onBack
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryTile("Free", freeCount, total, SpacerLiveGreen, modifier = Modifier.weight(1f))
                    SummaryTile("Conflicts", conflictCount, total, SpacerDanger, modifier = Modifier.weight(1f))
                    SummaryTile("No reply", pendingCount, total, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                }
            }

            item { SpacerSectionLabel(text = "Best windows this week") }

            // Best-window suggestions are meaningful once any guest has shared an
            // actual availability window. Conflict guests with a window still
            // contribute everywhere except the proposed-time cell, which is what
            // lets the ranker find a slot that clears the conflict.
            val haveAnyReplies = parsed.any { it.windowStart != null }
            if (!haveAnyReplies) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                            Text(
                                "Best windows show up once guests share when they can make it. " +
                                    "Tap \"Nudge no-replies\" below to remind them.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else {
                items(bestWindows.size) { i ->
                    val w = bestWindows[i]
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        BestWindowCard(
                            rank = "%02d".format(i + 1),
                            day = Days[w.day],
                            range = "${Hours[w.from]} – ${Hours[w.to]}",
                            free = w.minFree,
                            total = parsed.size.coerceAtLeast(1),
                            highlighted = i == 0,
                            clearsConflict = w.clearsProposedConflict && conflictCount > 0
                        )
                    }
                }
            }

            item { SpacerSectionLabel(text = "Proposed time") }

            item {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    ProposedTimeCard(
                        dayLabel = proposedDayLabel,
                        rangeLabel = proposedRangeLabel.ifBlank { "Time TBD" },
                        conflictNames = parsed.filter { it.status == GuestStatus.Conflict }.map { it.name },
                        heatmapDay = heatmap.getOrNull(proposedDay) ?: IntArray(Hours.size),
                        proposedFrom = proposedFrom,
                        proposedTo = proposedTo,
                        total = parsed.size.coerceAtLeast(1),
                        onReschedule = { showRescheduleDialog = true },
                        onNudge = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { repo.nudgeNoReplyMembers(eventId) }
                                r.onSuccess { count ->
                                    val msg = if (count == 0) "Everyone has replied." else "Nudged $count guest${if (count == 1) "" else "s"}."
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "Couldn't nudge right now.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }

            item { SpacerSectionLabel(text = "Group heatmap") }
            item {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (haveAnyReplies) {
                        GroupHeatmap(
                            heatmap = heatmap,
                            total = parsed.size.coerceAtLeast(1),
                            proposedDay = proposedDay,
                            proposedFrom = proposedFrom,
                            proposedTo = proposedTo
                        )
                    } else {
                        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                            Text(
                                "No availability replies yet. The heatmap fills in as guests confirm " +
                                    "when they can make it.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }

            item {
                SpacerSectionLabel(
                    text = "Guests · ${parsed.size - pendingCount}/${parsed.size} replied"
                )
            }

            if (loading && parsed.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                            Text(
                                "Loading availability…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else if (parsed.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                            Text(
                                "No replies yet. Guests can open their invite and tap time presets after accepting.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else {
                items(parsed.size) { i ->
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        GuestRow(
                            guest = parsed[i],
                            proposedFrom = proposedFrom,
                            proposedTo = proposedTo
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SpacerButton(
                            label = "Export",
                            onClick = {
                                val ev = event
                                if (ev == null) {
                                    Toast.makeText(context, "Loading event…", Toast.LENGTH_SHORT).show()
                                } else {
                                    val text = buildExportText(ev, parsed, bestWindows)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Guest availability — ${ev.title}")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(send, "Share availability"))
                                    }.onFailure {
                                        Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            kind = SpacerButtonKind.Secondary,
                            size = SpacerButtonSize.Lg,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SpacerButton(
                        label = "Apply best window",
                        onClick = {
                            // When there are conflicts at the proposed time, prefer
                            // the highest-ranked window that actually clears them.
                            // Otherwise fall back to the top-ranked window.
                            val best = if (conflictCount > 0) {
                                bestWindows.firstOrNull { it.clearsProposedConflict }
                                    ?: bestWindows.firstOrNull()
                            } else {
                                bestWindows.firstOrNull()
                            }
                            if (best == null) {
                                Toast.makeText(
                                    context,
                                    "Not enough replies yet to pick a window.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@SpacerButton
                            }
                            val ev = event
                            if (ev == null) {
                                Toast.makeText(context, "Loading event…", Toast.LENGTH_SHORT).show()
                                return@SpacerButton
                            }
                            scope.launch {
                                val newStart = computeIsoForBestWindow(ev.startsAt, best.day, best.from)
                                val newEnd = computeIsoForBestWindow(ev.startsAt, best.day, best.to + 1)
                                if (newStart == null || newEnd == null) {
                                    Toast.makeText(
                                        context,
                                        "Couldn't read the current event time. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                val r = withContext(Dispatchers.IO) {
                                    repo.updateEventCoreDetails(
                                        eventId = eventId,
                                        startsAtIso = newStart,
                                        endsAtIso = newEnd,
                                        bringItems = ev.bringItems
                                    )
                                }
                                r.onSuccess {
                                    val msg = when {
                                        conflictCount > 0 && best.clearsProposedConflict ->
                                            "Moved to ${Days[best.day]} ${Hours[best.from]} — cleared $conflictCount conflict${if (conflictCount == 1) "" else "s"}."
                                        else -> "Event time updated."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    event = withContext(Dispatchers.IO) { repo.getEvent(eventId).getOrNull() }
                                    reload()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        friendlyErrorMessage(it, fallback = "Couldn't apply window. Please try again."),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        kind = SpacerButtonKind.Primary,
                        size = SpacerButtonSize.Lg,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }

    if (showRescheduleDialog) {
        AlertDialog(
            onDismissRequest = { showRescheduleDialog = false },
            title = { Text("Reschedule event") },
            text = {
                Column {
                    Text(
                        text = "Tap to pick a new date and time. Times are shown in 12-hour format using your device timezone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    SpacerDateTimeField(
                        label = "Starts",
                        isoValue = startsAtDraft,
                        onPick = { showStartPicker = true }
                    )
                    Spacer(Modifier.height(10.dp))
                    SpacerDateTimeField(
                        label = "Ends (optional)",
                        isoValue = endsAtDraft,
                        onPick = { showEndPicker = true },
                        placeholder = "Pick an end time (optional)"
                    )
                    if (endsAtDraft.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Clear end time",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { endsAtDraft = "" }
                        )
                    }
                }
            },
            confirmButton = {
                SpacerButton(
                    label = "Save",
                    onClick = {
                        val ev = event
                        val startIso = startsAtDraft.trim()
                        if (ev == null || startIso.isBlank()) {
                            Toast.makeText(context, "Pick a start time first.", Toast.LENGTH_SHORT).show()
                            return@SpacerButton
                        }
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                repo.updateEventCoreDetails(
                                    eventId = eventId,
                                    startsAtIso = startIso,
                                    endsAtIso = endsAtDraft.trim().ifBlank { null },
                                    bringItems = ev.bringItems
                                )
                            }
                            r.onSuccess {
                                Toast.makeText(context, "Event rescheduled.", Toast.LENGTH_SHORT).show()
                                event = withContext(Dispatchers.IO) { repo.getEvent(eventId).getOrNull() }
                                reload()
                                showRescheduleDialog = false
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Couldn't reschedule.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    kind = SpacerButtonKind.Primary,
                    size = SpacerButtonSize.Md
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { showRescheduleDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStartPicker && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SpacerDateTimePickerDialog(
            title = "Start",
            initialIso = startsAtDraft.ifBlank { event?.startsAt.orEmpty() },
            onDismiss = { showStartPicker = false },
            onConfirm = { iso ->
                startsAtDraft = iso
                showStartPicker = false
            }
        )
    }

    if (showEndPicker && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SpacerDateTimePickerDialog(
            title = "End",
            initialIso = endsAtDraft.ifBlank { startsAtDraft.ifBlank { event?.startsAt.orEmpty() } },
            onDismiss = { showEndPicker = false },
            onConfirm = { iso ->
                endsAtDraft = iso
                showEndPicker = false
            }
        )
    }
}

private fun buildExportText(
    event: EventRow,
    parsed: List<GuestAvail>,
    bestWindows: List<BestWindow>
): String {
    val sb = StringBuilder()
    sb.append("Guest availability — ").append(event.title).append('\n')
    sb.append("Time: ").append(event.startsAt)
    if (!event.endsAt.isNullOrBlank()) sb.append(" – ").append(event.endsAt)
    sb.append("\n\n")
    sb.append("Summary:\n")
    sb.append("  Free: ").append(parsed.count { it.status == GuestStatus.Free }).append('\n')
    sb.append("  Conflicts: ").append(parsed.count { it.status == GuestStatus.Conflict }).append('\n')
    sb.append("  No reply: ").append(parsed.count { it.status == GuestStatus.Pending }).append('\n')
    sb.append('\n').append("Best windows this week:\n")
    bestWindows.forEachIndexed { i, w ->
        sb.append("  ${i + 1}. ${Days[w.day]} ${Hours[w.from]} – ${Hours[w.to]}  (${w.minFree} free)\n")
    }
    sb.append('\n').append("Guests:\n")
    parsed.forEach { g ->
        sb.append("  • ").append(g.name).append(" — ").append(g.status.name)
        if (!g.notes.isNullOrBlank()) sb.append(" (").append(g.notes).append(')')
        sb.append('\n')
    }
    return sb.toString()
}

private fun computeIsoForBestWindow(eventStartsAtIso: String, dayIndex: Int, hourSlot: Int): String? {
    val original = runCatching { java.time.OffsetDateTime.parse(eventStartsAtIso) }.getOrNull() ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val localOriginal = original.atZoneSameInstant(zone)
    val baseDay = localOriginal.toLocalDate()
    val targetDow = (dayIndex % 7) + 1
    val baseDow = baseDay.dayOfWeek.value
    val daysUntil = (targetDow - baseDow + 7) % 7
    val newDate = baseDay.plusDays(daysUntil.toLong())
    val newHour = (9 + hourSlot).coerceIn(0, 23)
    val newLocal = newDate.atTime(newHour, 0).atZone(zone).toOffsetDateTime()
    return newLocal.toString()
}

private fun parseGuest(entry: AvailabilityEntryUi): GuestAvail {
    val slots = entry.presetSlots.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
    val (windowStart, windowEnd) = mapPresetSlotsToWindow(slots)
    val anyConflictSignal = entry.calendarBusyOverlapsEvent ||
        entry.specificBusyOverlapsEvent ||
        entry.outsideWeeklyWindows
    val status = when {
        // Pending = haven't replied AND we have no general data either.
        entry.pendingReply && !anyConflictSignal && slots.isEmpty() && entry.notes.isNullOrBlank() ->
            GuestStatus.Pending
        anyConflictSignal -> GuestStatus.Conflict
        slots.size == 1 || (windowEnd != null && windowStart != null && (windowEnd - windowStart) <= 4) ->
            GuestStatus.Partial
        else -> GuestStatus.Free
    }
    return GuestAvail(
        name = entry.displayName,
        status = status,
        windowStart = windowStart,
        windowEnd = windowEnd,
        conflictsAtProposed = anyConflictSignal,
        notes = entry.notes
    )
}

private fun mapPresetSlotsToWindow(slots: List<String>): Pair<Int?, Int?> {
    if (slots.isEmpty()) return null to null
    var start = Int.MAX_VALUE
    var end = Int.MIN_VALUE
    slots.forEach { s ->
        val (a, b) = when {
            s.contains("morning") -> 0 to 3
            s.contains("midday") || s.contains("noon") -> 3 to 6
            s.contains("afternoon") -> 6 to 9
            s.contains("evening") -> 9 to 13
            else -> 0 to Hours.size - 1
        }
        start = min(start, a)
        end = max(end, b)
    }
    if (start == Int.MAX_VALUE) return null to null
    return start to end
}

private fun computeHeatmap(
    parsed: List<GuestAvail>,
    hours: Int,
    days: Int,
    proposedDay: Int,
    proposedFrom: Int,
    proposedTo: Int
): List<IntArray> {
    // For each (day, hour) cell, count guests whose self-reported window covers
    // that hour. Guests flagged as `conflictsAtProposed` are excluded ONLY from the
    // proposed (day, hour) range — elsewhere their stated window still counts, so
    // best-window ranking can find a time that actually clears the conflict.
    return List(days) { dayIdx ->
        IntArray(hours) { h ->
            parsed.count { g ->
                if (g.status == GuestStatus.Pending) return@count false
                val ws = g.windowStart ?: return@count false
                val we = g.windowEnd ?: return@count false
                val inWindow = h in ws..we
                val inProposedConflict = g.conflictsAtProposed &&
                    dayIdx == proposedDay && h in proposedFrom..proposedTo
                inWindow && !inProposedConflict
            }
        }
    }
}

/**
 * Map the event's start/end times into (day-of-week, fromHourSlot, toHourSlot).
 * The heatmap covers Mon..Sun and 9 AM..10 PM in 1-hour slots.
 */
private data class HeatmapIndex(val day: Int, val from: Int, val to: Int)

private fun eventWindowToHeatmapIndex(event: EventRow?): HeatmapIndex {
    if (event == null) return HeatmapIndex(0, 9, 12)
    val start = runCatching { java.time.OffsetDateTime.parse(event.startsAt) }.getOrNull()
        ?: return HeatmapIndex(0, 9, 12)
    val zone = java.time.ZoneId.systemDefault()
    val localStart = start.atZoneSameInstant(zone)
    // Days[0]=Mon … Days[6]=Sun, java.time DayOfWeek MONDAY=1
    val day = (localStart.dayOfWeek.value - 1).coerceIn(0, 6)
    val startHour = localStart.hour
    // 9 AM is column 0; 10 PM is column 13.
    val from = (startHour - 9).coerceIn(0, Hours.size - 1)
    val end = event.endsAt?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() }
    val toHourLocal = end?.atZoneSameInstant(zone)?.hour ?: (startHour + 2)
    val to = (toHourLocal - 9).coerceIn(from, Hours.size - 1)
    return HeatmapIndex(day, from, to)
}

private fun eventDayLabel(startsAtIso: String): String {
    val start = runCatching { java.time.OffsetDateTime.parse(startsAtIso) }.getOrNull() ?: return "Today"
    val zone = java.time.ZoneId.systemDefault()
    val local = start.atZoneSameInstant(zone)
    return local.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US))
}

private fun eventRangeLabel(startsAtIso: String, endsAtIso: String?): String {
    val start = runCatching { java.time.OffsetDateTime.parse(startsAtIso) }.getOrNull() ?: return ""
    val zone = java.time.ZoneId.systemDefault()
    val timeFmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
    val sLocal = start.atZoneSameInstant(zone).format(timeFmt)
    val end = endsAtIso?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() }
    return if (end != null) {
        val eLocal = end.atZoneSameInstant(zone).format(timeFmt)
        "$sLocal – $eLocal"
    } else sLocal
}

private data class BestWindow(
    val day: Int,
    val from: Int,
    val to: Int,
    val minFree: Int,
    val clearsProposedConflict: Boolean = false
)

private fun rankBestWindows(
    heatmap: List<IntArray>,
    proposedDay: Int,
    proposedFrom: Int,
    proposedTo: Int
): List<BestWindow> {
    val width = 3
    val candidates = mutableListOf<BestWindow>()
    heatmap.forEachIndexed { dayIdx, hours ->
        for (start in 0..(hours.size - width)) {
            val end = start + width - 1
            val minFree = (start..end).minOf { hours[it] }
            // A candidate "clears" the proposed conflict if it doesn't overlap the
            // proposed (day, hour) range — picking it should free guests who are
            // flagged conflictsAtProposed.
            val overlapsProposed = dayIdx == proposedDay &&
                !(end < proposedFrom || start > proposedTo)
            candidates.add(BestWindow(dayIdx, start, end, minFree, clearsProposedConflict = !overlapsProposed))
        }
    }
    // Rank by free count first, then prefer windows that clear the conflict so
    // ties surface alternatives the host can switch to safely.
    return candidates
        .sortedWith(compareByDescending<BestWindow> { it.minFree }.thenByDescending { it.clearsProposedConflict })
        .distinctBy { it.day }
        .take(3)
}

@Composable
private fun SummaryTile(
    label: String,
    n: Int,
    total: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$n",
                    color = accent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = "/$total",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun BestWindowCard(
    rank: String,
    day: String,
    range: String,
    free: Int,
    total: Int,
    highlighted: Boolean,
    clearsConflict: Boolean
) {
    val border = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rank,
                    color = SpacerGold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
            ) {
                Text(
                    day,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    range,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.5.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$free/$total free",
                    color = if (free >= total) SpacerLiveGreen else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (free >= total) {
                    Text(
                        "FITS ALL",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (clearsConflict) {
                    Text(
                        "CLEARS CONFLICT",
                        color = SpacerLiveGreen,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposedTimeCard(
    dayLabel: String,
    rangeLabel: String,
    conflictNames: List<String>,
    heatmapDay: IntArray,
    proposedFrom: Int,
    proposedTo: Int,
    total: Int,
    onReschedule: () -> Unit,
    onNudge: () -> Unit
) {
    SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        dayLabel.uppercase(),
                        color = SpacerGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        rangeLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.4).sp
                    )
                }
                if (conflictNames.isEmpty()) {
                    SpacerChip(text = "All clear", tone = SpacerChipTone.Success, size = SpacerChipSize.Md)
                } else {
                    SpacerChip(text = "${conflictNames.size} conflict", tone = SpacerChipTone.Danger, size = SpacerChipSize.Md)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mini overlap bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                heatmapDay.forEachIndexed { i, n ->
                    val isProposed = i in proposedFrom..proposedTo
                    val ratio = (n.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    val color = when {
                        isProposed && ratio >= 0.85f -> SpacerLiveGreen
                        isProposed && ratio >= 0.5f -> MaterialTheme.colorScheme.primary
                        isProposed -> SpacerDanger
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f + ratio * 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(color)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("9 AM", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("3 PM", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("10 PM", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            if (conflictNames.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(SpacerDanger.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("!", color = SpacerDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            buildString {
                                append(conflictNames.joinToString(", "))
                                append(" can't make this time. Tap a smart pick above to retime.")
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpacerButton(
                    label = "Reschedule",
                    onClick = onReschedule,
                    kind = SpacerButtonKind.Secondary,
                    size = SpacerButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
                SpacerButton(
                    label = "Nudge no-replies",
                    onClick = onNudge,
                    kind = SpacerButtonKind.Ghost,
                    size = SpacerButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GroupHeatmap(
    heatmap: List<IntArray>,
    total: Int,
    proposedDay: Int,
    proposedFrom: Int,
    proposedTo: Int
) {
    SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.widthIn(min = 32.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("9a", "12p", "3p", "6p", "9p").forEach {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Days.forEachIndexed { di, dayLabel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dayLabel,
                        color = if (di == proposedDay) SpacerGold
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = if (di == proposedDay) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.widthIn(min = 32.dp)
                    )
                    Box(modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            heatmap.getOrElse(di) { IntArray(Hours.size) }.forEach { n ->
                                val ratio = (n.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                val color = when {
                                    ratio >= 0.85f -> SpacerLiveGreen
                                    ratio >= 0.6f -> MaterialTheme.colorScheme.primary
                                    ratio >= 0.4f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    ratio >= 0.2f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(color, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                        if (di == proposedDay) {
                            val leftFrac = proposedFrom.toFloat() / Hours.size.toFloat()
                            val widthFrac = (proposedTo - proposedFrom + 1).toFloat() / Hours.size.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(widthFrac)
                                        .padding(start = 0.dp)
                                        .height(26.dp)
                                        .border(1.5.dp, SpacerGold, RoundedCornerShape(5.dp))
                                        .padding(start = 0.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("FEW",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.primary,
                        SpacerLiveGreen
                    ).forEach { c ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(c, RoundedCornerShape(2.dp))
                        )
                    }
                }
                Text("ALL",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
            }
        }
    }
}

@Composable
private fun GuestRow(
    guest: GuestAvail,
    proposedFrom: Int,
    proposedTo: Int
) {
    val tone = when (guest.status) {
        GuestStatus.Free -> SpacerChipTone.Success
        GuestStatus.Partial -> SpacerChipTone.Gold
        GuestStatus.Conflict -> SpacerChipTone.Danger
        GuestStatus.Pending -> SpacerChipTone.Default
    }
    val label = when (guest.status) {
        GuestStatus.Free -> "Free"
        GuestStatus.Partial -> "Partial"
        GuestStatus.Conflict -> "Conflict"
        GuestStatus.Pending -> "No reply"
    }

    SpacerCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.hsv(((abs(guest.name.hashCode()) % 36) * 10).toFloat(), 0.4f, 0.78f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        guest.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                ) {
                    Text(
                        guest.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val sub = when {
                        guest.windowStart != null && guest.windowEnd != null ->
                            "Available ${Hours[guest.windowStart]} — ${Hours[min(guest.windowEnd, Hours.size - 1)]}"
                        guest.status == GuestStatus.Conflict ->
                            "Calendar conflict · hasn't shared availability yet"
                        else -> "Hasn't shared availability yet"
                    }
                    Text(
                        sub,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                }
                SpacerChip(text = label, tone = tone, size = SpacerChipSize.Sm)
            }

            Spacer(Modifier.height(12.dp))

            // Day strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Hours.forEachIndexed { i, _ ->
                    val inWindow = guest.windowStart != null && guest.windowEnd != null &&
                        i >= guest.windowStart && i <= guest.windowEnd
                    val isProposed = i in proposedFrom..proposedTo
                    val color = when {
                        guest.status == GuestStatus.Pending -> Color.Transparent
                        guest.conflictsAtProposed && isProposed -> SpacerDanger
                        inWindow && isProposed -> SpacerLiveGreen
                        inWindow -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(color)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("9 AM", "2 PM", "6 PM", "10 PM").forEach {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
    }
}
