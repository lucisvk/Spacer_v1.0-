package com.example.spacer.location

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.spacer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GENERIC_PLACE_TYPES = setOf(
    "point_of_interest",
    "establishment",
    "premise",
    "political"
)

@OptIn(FlowPreview::class)
@Composable
fun CreateEventPlaceScreen(
    modifier: Modifier = Modifier,
    selectedPlace: PlaceUi?,
    onSelectedPlaceChange: (PlaceUi?) -> Unit,
    eventTitle: String,
    onEventTitleChange: (String) -> Unit,
    onOpenPlaceDetail: (PlaceUi) -> Unit,
    onContinue: () -> Unit,
    /** After user taps "Use for event" on a place card — go to step 2 (details). */
    onUsePlaceForEvent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val placesRepository = remember { PlacesRepository() }
    var searchQuery by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var nearbyPlaces by remember { mutableStateOf<List<PlaceUi>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<PlaceUi>>(emptyList()) }
    var loadingNearby by remember { mutableStateOf(false) }
    var loadingSearch by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }

    val hasKey = BuildConfig.PLACES_API_KEY.isNotBlank()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            scope.launch {
                userLocation = withContext(Dispatchers.IO) { getLastKnownLatLng(context) }
            }
        }
    }

    // Do not auto-launch permission from LaunchedEffect — it can crash during NavHost composition.
    LaunchedEffect(Unit) {
        try {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                userLocation = withContext(Dispatchers.IO) { getLastKnownLatLng(context) }
            }
        } catch (e: Exception) {
            listError = e.message
        }
    }

    LaunchedEffect(userLocation, hasKey) {
        val loc = userLocation ?: return@LaunchedEffect
        if (!hasKey) return@LaunchedEffect
        try {
            loadingNearby = true
            listError = null
            val result = withContext(Dispatchers.IO) {
                placesRepository.searchNearby(loc.latitude, loc.longitude)
            }
            result.onSuccess { nearbyPlaces = it }
                .onFailure { e ->
                    listError = e.message
                    Toast.makeText(context, "Nearby failed", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            listError = e.message
            Toast.makeText(context, "Nearby error", Toast.LENGTH_LONG).show()
        } finally {
            loadingNearby = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(400L)
            .distinctUntilChanged()
            .collectLatest { q ->
                try {
                    if (!hasKey || q.trim().length < 2) {
                        searchResults = emptyList()
                        return@collectLatest
                    }
                    val loc = userLocation ?: run {
                        val fine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!fine && !coarse) {
                            searchResults = emptyList()
                            return@collectLatest
                        }
                        withContext(Dispatchers.IO) { getLastKnownLatLng(context) }
                            .also { userLocation = it }
                    }
                    if (loc == null) {
                        searchResults = emptyList()
                        return@collectLatest
                    }
                    loadingSearch = true
                    val result = withContext(Dispatchers.IO) {
                        placesRepository.searchText(q, loc.latitude, loc.longitude)
                    }
                    result.onSuccess { searchResults = it }
                        .onFailure { e ->
                            Toast.makeText(context, "Search failed", Toast.LENGTH_LONG).show()
                        }
                } catch (e: Exception) {
                    Toast.makeText(context, "Search error", Toast.LENGTH_LONG).show()
                } finally {
                    loadingSearch = false
                }
            }
    }

    val showingSearch = searchQuery.trim().length >= 2
    val baseList = if (showingSearch) searchResults else nearbyPlaces
    val filtered = remember(baseList, categoryFilter) {
        if (categoryFilter == null) baseList
        else baseList.filter { it.types.contains(categoryFilter) }
    }

    val filterOptions: List<Pair<String?, String>> = remember(nearbyPlaces, searchResults) {
        val merged = (nearbyPlaces.asSequence() + searchResults.asSequence())
            .distinctBy { it.id }
            .toList()
        val counts = merged
            .asSequence()
            .flatMap { it.types.asSequence() }
            .filter { it.isNotBlank() && it !in GENERIC_PLACE_TYPES }
            .groupingBy { it }
            .eachCount()
        val topTypes = counts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(14)
        listOf(null to "All") + topTypes.map { type -> type to formatPlaceType(type) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            item {
                Text(
                    text = "STEP 01 / 02",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose a venue",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = "Name your event and pick a place. We'll pull rich details from Google.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )
                StepProgress(current = 1, total = 2)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val fine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Location is off",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Allow location to load venues near you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )
                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            ) {
                                Text("Allow location access")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (!hasKey) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "Add PLACES_API_KEY to local.properties and enable Places API (New) in Google Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB4B4),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = onEventTitleChange,
                    label = { Text("Event title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search places") },
                    placeholder = { Text("Try “rooftop bar”, “park”, “theater”…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val fine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!fine && !coarse) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                    return@launch
                                }
                                val loc = withContext(Dispatchers.IO) { getLastKnownLatLng(context) }
                                userLocation = loc
                                if (loc == null) {
                                    Toast.makeText(
                                        context,
                                        "Location unavailable — enable GPS and try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = hasKey && !loadingNearby
                    ) {
                        Text("Refresh nearby")
                    }
                    if (loadingNearby || loadingSearch) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(
                    text = if (showingSearch) "Search results" else "Nearby picks",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.forEach { (key, label) ->
                        val selected = categoryFilter == key
                        AssistChip(
                            onClick = { categoryFilter = key },
                            label = { Text(label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            selectedPlace?.let { sel ->
                item {
                    Text(
                        "Selected venue",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PlaceCard(
                        place = sel,
                        selected = true,
                        placesRepository = placesRepository,
                        onOpenDetail = { onOpenPlaceDetail(sel) },
                        onSelectForEvent = {
                            onSelectedPlaceChange(sel)
                            onUsePlaceForEvent()
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            listError?.let { err ->
                item {
                    Text(err, color = Color(0xFFFFB4B4), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            itemsIndexed(
                items = filtered,
                key = { index, place -> "${place.id}_$index" }
            ) { _, place ->
                PlaceCard(
                    place = place,
                    selected = selectedPlace?.id == place.id,
                    placesRepository = placesRepository,
                    onOpenDetail = { onOpenPlaceDetail(place) },
                    onSelectForEvent = {
                        onSelectedPlaceChange(place)
                        onUsePlaceForEvent()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                val canContinue = selectedPlace != null && eventTitle.isNotBlank()
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onContinue,
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to details")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
)

@Composable
private fun PlaceCard(
    place: PlaceUi,
    selected: Boolean,
    placesRepository: PlacesRepository,
    onOpenDetail: () -> Unit,
    onSelectForEvent: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val thumbUrl = place.primaryPhotoName?.let { placesRepository.photoMediaUrl(it, 400) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetail),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.size(88.dp)
                ) {
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📍",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    place.rating?.let { r ->
                        val c = place.userRatingCount ?: 0
                        Text(
                            "★ ${"%.1f".format(r)} · $c reviews",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (place.address.isNotBlank()) {
                        Text(
                            place.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                StatusPill(place)
            }

            if (place.categoryTags().isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    place.categoryTags().forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (place.weekdayHours.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Hours",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                place.weekdayHours.take(3).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (place.weekdayHours.size > 3) {
                    Text(
                        "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onSelectForEvent) {
                    Text(if (selected) "Selected" else "Use for event")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(place: PlaceUi) {
    val (label, bg, fg) = when (place.businessStatus) {
        "CLOSED_PERMANENTLY" -> Triple("Closed", Color(0xFF5A1E2A), Color(0xFFFFC9C9))
        else -> when (place.openNow) {
            true -> Triple("Open now", Color(0xFF1A4D35), Color(0xFF8FFFC4))
            false -> Triple("Closed", Color(0xFF4D2A1A), Color(0xFFFFC9A8))
            null -> Triple("Hours vary", Color(0xFF2E275F), Color(0xFFB9B1FF))
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun StepProgress(current: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = if (i < current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
        Text(
            "%02d / %02d".format(current, total),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        )
    }
}
