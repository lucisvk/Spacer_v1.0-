package com.example.spacer.events

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val displayFormatter12h: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a", Locale.getDefault())

/**
 * Display ISO datetime in a friendly 12-hour format.
 * Returns "Pick a date & time" placeholder if blank.
 * Falls back to raw value if parsing fails.
 */
fun formatIsoFor12hDisplay(iso: String?, placeholder: String = "Pick a date & time"): String {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return placeholder
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return raw
    return try {
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(displayFormatter12h)
    } catch (_: DateTimeParseException) {
        raw
    } catch (_: Throwable) {
        raw
    }
}

/**
 * A friendly trigger row that shows the current selected date/time and opens a picker on tap.
 */
@Composable
fun SpacerDateTimeField(
    label: String,
    isoValue: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date & time"
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.padding(top = 4.dp))
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatIsoFor12hDisplay(isoValue, placeholder),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("📅", fontSize = 16.sp)
            }
        }
    }
}

/**
 * A two-step dialog: pick a date, then pick a time (12-hour).
 * Calls onConfirm with the resulting ISO 8601 OffsetDateTime string in the device timezone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SpacerDateTimePickerDialog(
    title: String,
    initialIso: String?,
    onDismiss: () -> Unit,
    onConfirm: (isoDateTime: String) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(initialIso) { parseInitial(initialIso, zone) }
    var stage by remember { mutableStateOf(0) }

    val initialUtcMidnightMillis = remember(initial) {
        initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMidnightMillis
    )
    val timePickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    var pickedDateUtcMillis by remember { mutableStateOf<Long?>(null) }
    var pickedHour by remember { mutableIntStateOf(initial.hour) }
    var pickedMinute by remember { mutableIntStateOf(initial.minute) }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        pickedDateUtcMillis = datePickerState.selectedDateMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (stage == 0) "$title · Pick date" else "$title · Pick time",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Box(
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (stage == 0) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TimePicker(state = timePickerState)
                            Text(
                                text = "Tap AM/PM to switch · 12-hour clock",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            SpacerButton(
                label = if (stage == 0) "Next: time" else "Save",
                onClick = {
                    if (stage == 0) {
                        if (pickedDateUtcMillis == null) {
                            pickedDateUtcMillis = datePickerState.selectedDateMillis
                        }
                        if (pickedDateUtcMillis != null) stage = 1
                    } else {
                        pickedHour = timePickerState.hour
                        pickedMinute = timePickerState.minute
                        val millis = pickedDateUtcMillis ?: datePickerState.selectedDateMillis
                        if (millis != null) {
                            val iso = combineToIso(millis, pickedHour, pickedMinute, zone)
                            onConfirm(iso)
                        }
                    }
                },
                kind = SpacerButtonKind.Primary,
                size = SpacerButtonSize.Md
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stage == 1) {
                    TextButton(onClick = { stage = 0 }) { Text("Back") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
private fun parseInitial(initialIso: String?, zone: ZoneId): LocalDateTime {
    val raw = initialIso?.trim().orEmpty()
    if (raw.isEmpty()) {
        return LocalDateTime.now(zone).withSecond(0).withNano(0)
    }
    return try {
        OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        LocalDateTime.now(zone).withSecond(0).withNano(0)
    } catch (_: Throwable) {
        LocalDateTime.now(zone).withSecond(0).withNano(0)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun combineToIso(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): String {
    val date = LocalDate.ofEpochDay(dateUtcMillis / 86_400_000L).let {
        // DatePicker returns UTC midnight millis; convert to the LocalDate that matches the user's intent
        val secondsSinceEpoch = dateUtcMillis / 1000
        val instant = java.time.Instant.ofEpochSecond(secondsSinceEpoch)
        instant.atOffset(ZoneOffset.UTC).toLocalDate()
    }
    val local = LocalDateTime.of(date, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
    return local.atZone(zone).toOffsetDateTime().toString()
}
