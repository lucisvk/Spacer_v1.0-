package com.example.spacer.location

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spacer.ui.theme.SpacerPurpleBackground
import com.example.spacer.ui.theme.SpacerPurpleOutline
import com.example.spacer.ui.theme.SpacerPurplePrimary
import com.example.spacer.ui.theme.SpacerPurpleSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceDetailScreen(
    placeId: String,
    onBack: () -> Unit,
    onUseForEvent: (PlaceDetailUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { PlacesRepository() }
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<PlaceDetailUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(placeId) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) { repository.fetchPlaceDetails(placeId) }
        result
            .onSuccess { detail = it }
            .onFailure { e ->
                error = e.message
                Toast.makeText(context, "Could not load place", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    Surface(modifier = modifier.fillMaxSize(), color = SpacerPurpleBackground) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpacerPurplePrimary)
                }
            }
            error != null && detail == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error!!, color = Color(0xFFFFB4B4))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
            detail != null -> {
                val d = requireNotNull(detail)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Text("←", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
                        }
                        Text(
                            "VENUE",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.2.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PhotoPager(
                                photoNames = d.photoResourceNames,
                                repository = repository
                            )
                        }
                        item {
                            Column(Modifier.padding(horizontal = 18.dp)) {
                                Text(
                                    d.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.4).sp
                                )
                                d.rating?.let { r ->
                                    val count = d.userRatingCount ?: 0
                                    Text(
                                        "★ ${"%.1f".format(r)} · $count reviews",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                if (d.address.isNotBlank()) {
                                    Text(
                                        d.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                if (d.categoryTags(8).isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        d.categoryTags(8).forEach { tag ->
                                            com.example.spacer.ui.theme.SpacerChip(
                                                text = tag,
                                                tone = com.example.spacer.ui.theme.SpacerChipTone.Ghost,
                                                size = com.example.spacer.ui.theme.SpacerChipSize.Sm
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (d.weekdayHours.isNotEmpty()) {
                            item {
                                Column(Modifier.padding(horizontal = 18.dp)) {
                                    Text(
                                        "HOURS",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 2.2.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    d.weekdayHours.forEach { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "REVIEWS",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.2.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )
                        }
                        if (d.reviews.isEmpty()) {
                            item {
                                Text(
                                    "No written reviews yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(horizontal = 18.dp)
                                )
                            }
                        } else {
                            items(
                                items = d.reviews,
                                key = { r -> "${r.author}_${r.relativeTime}_${r.text.hashCode()}" }
                            ) { review ->
                                ReviewCard(review)
                            }
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }

                    Surface(
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            com.example.spacer.ui.theme.SpacerButton(
                                label = "Back",
                                onClick = onBack,
                                kind = com.example.spacer.ui.theme.SpacerButtonKind.Secondary,
                                size = com.example.spacer.ui.theme.SpacerButtonSize.Lg,
                                modifier = Modifier.weight(1f)
                            )
                            com.example.spacer.ui.theme.SpacerButton(
                                label = "Use for event",
                                onClick = { onUseForEvent(d) },
                                kind = com.example.spacer.ui.theme.SpacerButtonKind.Primary,
                                size = com.example.spacer.ui.theme.SpacerButtonSize.Lg,
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPager(
    photoNames: List<String>,
    repository: PlacesRepository
) {
    if (photoNames.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("No photos", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { photoNames.size })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { page ->
            val url = repository.photoMediaUrl(photoNames[page], 1200)
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewUi) {
    Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        com.example.spacer.ui.theme.SpacerCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 16.dp
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        review.author,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "★ ${"%.1f".format(review.rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (review.relativeTime.isNotBlank()) {
                    Text(
                        review.relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    review.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
