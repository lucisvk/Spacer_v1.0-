package com.example.spacer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import com.example.spacer.ui.theme.SpacerChip
import com.example.spacer.ui.theme.SpacerChipSize
import com.example.spacer.ui.theme.SpacerChipTone
import com.example.spacer.ui.theme.SpacerGold
import com.example.spacer.ui.theme.SpacerLiveGreen
import com.example.spacer.events.EventLiveGuestUi
import kotlin.math.roundToInt
import kotlin.math.abs

enum class GuestState { Arrived, EnRoute, Self, Private }

data class TrackedGuest(
    val name: String,
    val hue: Int,
    val state: GuestState,
    val etaMinutes: Int?,
    val distance: String,
    val x: Float?,
    val y: Float?
)

data class TrackingStats(
    val total: Int,
    val arrived: Int,
    val enroute: Int,
    val onWay: Int,
    val percent: Int
)

fun computeTrackingStats(guests: List<TrackedGuest>): TrackingStats {
    val total = guests.size
    val arrived = guests.count { it.state == GuestState.Arrived }
    val enroute = guests.count { it.state == GuestState.EnRoute || it.state == GuestState.Self }
    val onWay = arrived + enroute
    val percent = if (total > 0) ((onWay.toFloat() / total.toFloat()) * 100f).roundToInt() else 0
    return TrackingStats(total, arrived, enroute, onWay, percent)
}

fun trackedFromLive(
    guests: List<EventLiveGuestUi>,
    selfUserId: String?,
    selfHasArrived: Boolean
): List<TrackedGuest> {
    if (guests.isEmpty()) return DemoGuests
    return guests.mapIndexed { index, guest ->
        val isSelf = selfUserId != null && guest.userId == selfUserId
        val state = when {
            guest.isArrived || (isSelf && selfHasArrived) -> GuestState.Arrived
            !guest.sharingEnabled -> GuestState.Private
            isSelf -> GuestState.Self
            else -> GuestState.EnRoute
        }
        val hue = ((abs(guest.userId.hashCode()) % 36) * 10)
        val (x, y) = if (state == GuestState.Private) {
            null to null
        } else if (state == GuestState.Arrived) {
            (0.48f + ((index % 5) * 0.012f)) to (0.40f + ((index % 3) * 0.014f))
        } else {
            val seed = abs(guest.userId.hashCode())
            val xPos = 0.18f + ((seed % 60) / 100f)
            val yPos = 0.22f + (((seed / 60) % 50) / 100f)
            xPos to yPos
        }
        val eta = if (state == GuestState.Private) null
        else ((abs(guest.userId.hashCode()) % 11) + 2)
        val distance = when (state) {
            GuestState.Arrived -> "at venue"
            GuestState.Private -> "sharing off"
            else -> "${"%.1f".format((abs(guest.userId.hashCode()) % 30) / 10f + 0.2f)} mi away"
        }
        TrackedGuest(
            name = guest.displayName,
            hue = hue,
            state = state,
            etaMinutes = eta,
            distance = distance,
            x = x,
            y = y
        )
    }
}

val DemoGuests: List<TrackedGuest> = listOf(
    TrackedGuest("maya.k", 280, GuestState.Arrived, 0, "at venue", 0.51f, 0.41f),
    TrackedGuest("jules", 60, GuestState.Arrived, 0, "at venue", 0.49f, 0.43f),
    TrackedGuest("devon", 340, GuestState.EnRoute, 4, "0.6 mi NE", 0.74f, 0.30f),
    TrackedGuest("chromic", 20, GuestState.EnRoute, 9, "1.4 mi W", 0.18f, 0.50f),
    TrackedGuest("sky.p", 200, GuestState.EnRoute, 14, "2.1 mi S", 0.62f, 0.78f),
    TrackedGuest("amara", 140, GuestState.Private, null, "sharing off", null, null),
    TrackedGuest("you", 310, GuestState.Self, 7, "0.9 mi N", 0.32f, 0.18f)
)

private val VenueX = 0.50f
private val VenueY = 0.42f

@Composable
fun SpacerMap(
    modifier: Modifier = Modifier,
    guests: List<TrackedGuest> = DemoGuests,
    showSelf: Boolean = true,
    showRoute: Boolean = true,
    showVenueLabel: Boolean = false,
    venueLabel: String = "Venue"
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val baseBg = if (isLight) Color(0xFFEEE7DA) else Color(0xFF0A0818)
    val blockA = if (isLight) Color(0xFFE2D9C5) else Color(0xFF13102A)
    val blockB = if (isLight) Color(0xFFD9CFB7) else Color(0xFF1A1635)
    val street = if (isLight) Color(0xFFF4EFE3) else Color(0xFF1F1B3D)
    val streetMinor = if (isLight) Color(0xFFEBE3D0) else Color(0xFF15122A)
    val water = if (isLight) Color(0xFFC9D9E0) else Color(0xFF0E1A2E)
    val routeColor = SpacerGold

    BoxWithConstraints(
        modifier = modifier.background(baseBg)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val waterPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                quadraticBezierTo(0.15f * w, 0.13f * h, 0.225f * w, 0.27f * h)
                quadraticBezierTo(0.30f * w, 0.41f * h, 0.45f * w, 0.47f * h)
                lineTo(0.55f * w, 0.60f * h)
                lineTo(0f, 0.60f * h)
                close()
            }
            drawPath(waterPath, water, alpha = if (isLight) 0.5f else 0.6f)

            val blocks = listOf(
                floatArrayOf(0.55f, 0.17f, 0.20f, 0.20f),
                floatArrayOf(0.78f, 0.10f, 0.18f, 0.27f),
                floatArrayOf(0.55f, 0.40f, 0.20f, 0.17f),
                floatArrayOf(0.78f, 0.40f, 0.18f, 0.17f),
                floatArrayOf(0.55f, 0.60f, 0.13f, 0.30f),
                floatArrayOf(0.70f, 0.60f, 0.23f, 0.17f),
                floatArrayOf(0.70f, 0.80f, 0.23f, 0.20f),
                floatArrayOf(0.30f, 0.67f, 0.20f, 0.17f),
                floatArrayOf(0.30f, 0.87f, 0.20f, 0.13f),
                floatArrayOf(0.025f, 0.67f, 0.25f, 0.33f),
                floatArrayOf(0.15f, 0.27f, 0.13f, 0.13f),
                floatArrayOf(0.30f, 0.20f, 0.15f, 0.17f),
                floatArrayOf(0.45f, 0.20f, 0.075f, 0.17f)
            )
            blocks.forEachIndexed { i, b ->
                val fill = if (i % 2 == 0) blockA else blockB
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(b[0] * w, b[1] * h),
                    size = Size(b[2] * w, b[3] * h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }

            // Major streets
            drawLine(street, Offset(0f, 0.567f * h), Offset(w, 0.567f * h), strokeWidth = 10f)
            drawLine(street, Offset(0.525f * w, 0f), Offset(0.525f * w, h), strokeWidth = 8f)
            drawLine(street, Offset(0f, 0.383f * h), Offset(w, 0.383f * h), strokeWidth = 6f)
            drawLine(street, Offset(0.7625f * w, 0f), Offset(0.7625f * w, h), strokeWidth = 5f)

            drawLine(streetMinor, Offset(0f, 0.20f * h), Offset(w, 0.20f * h), strokeWidth = 2.5f)
            drawLine(streetMinor, Offset(0f, 0.783f * h), Offset(w, 0.783f * h), strokeWidth = 2.5f)
            drawLine(streetMinor, Offset(0.2875f * w, 0f), Offset(0.2875f * w, h), strokeWidth = 2.5f)
            drawLine(streetMinor, Offset(0.675f * w, 0f), Offset(0.675f * w, h), strokeWidth = 2.5f)
            drawLine(streetMinor, Offset(0.925f * w, 0f), Offset(0.925f * w, h), strokeWidth = 2.5f)

            // Route line: self → venue, dashed gold
            if (showRoute && showSelf) {
                val self = Offset(0.32f * w, 0.18f * h)
                val venue = Offset(VenueX * w, VenueY * h)
                val mid = Offset(venue.x, self.y)
                val routePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(self.x, self.y)
                    lineTo(mid.x, mid.y)
                    lineTo(venue.x, venue.y)
                }
                drawPath(
                    path = routePath,
                    color = routeColor,
                    alpha = 0.85f,
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    )
                )
            }

            // Venue glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(routeColor.copy(alpha = 0.4f), routeColor.copy(alpha = 0f)),
                    center = Offset(VenueX * w, VenueY * h),
                    radius = 60f
                ),
                radius = 60f,
                center = Offset(VenueX * w, VenueY * h)
            )
        }

        // Venue pin
        VenuePin(
            xFraction = VenueX,
            yFraction = VenueY,
            label = if (showVenueLabel) venueLabel else null,
            widthPx = widthPx,
            heightPx = heightPx
        )

        // Guest pins
        guests.forEach { guest ->
            if (guest.state == GuestState.Self && !showSelf) return@forEach
            val x = guest.x ?: return@forEach
            val y = guest.y ?: return@forEach
            GuestPin(
                guest = guest,
                xFraction = x,
                yFraction = y,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }
    }
}

@Composable
private fun VenuePin(
    xFraction: Float,
    yFraction: Float,
    label: String?,
    widthPx: Float,
    heightPx: Float
) {
    val pinSize = 32.dp
    val xDp = with(androidx.compose.ui.platform.LocalDensity.current) { (xFraction * widthPx).toDp() }
    val yDp = with(androidx.compose.ui.platform.LocalDensity.current) { (yFraction * heightPx).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(((xFraction * widthPx) - 16f * 2).roundToInt(), ((yFraction * heightPx) - 32f * 2).roundToInt()) }
    ) {
        Box(
            modifier = Modifier
                .size(pinSize)
                .background(
                    color = SpacerGold,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topStartPercent = 50,
                        topEndPercent = 50,
                        bottomEndPercent = 50,
                        bottomStartPercent = 5
                    )
                )
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.surface,
                    androidx.compose.foundation.shape.RoundedCornerShape(
                        topStartPercent = 50,
                        topEndPercent = 50,
                        bottomEndPercent = 50,
                        bottomStartPercent = 5
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color(0xFF1A1408),
                modifier = Modifier.size(18.dp)
            )
        }
        if (!label.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 38.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    label,
                    color = SpacerGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun GuestPin(
    guest: TrackedGuest,
    xFraction: Float,
    yFraction: Float,
    widthPx: Float,
    heightPx: Float
) {
    val ringColor = when (guest.state) {
        GuestState.Arrived -> SpacerLiveGreen
        GuestState.Self -> SpacerGold
        GuestState.EnRoute -> MaterialTheme.colorScheme.primary
        GuestState.Private -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val avatarColor = Color.hsv(guest.hue.toFloat(), 0.45f, 0.78f)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    ((xFraction * widthPx) - 18f).roundToInt(),
                    ((yFraction * heightPx) - 18f).roundToInt()
                )
            }
            .size(36.dp)
            .background(ringColor, CircleShape)
            .border(2.dp, ringColor, CircleShape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(avatarColor, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                guest.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LiveTrackHeroCard(
    eventTitle: String,
    venueLine: String,
    startTimeLabel: String,
    arrived: Boolean,
    yourEtaMinutes: Int,
    onExpand: () -> Unit,
    onArrive: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseGuests = remember { DemoGuests }
    val guests = if (arrived) {
        baseGuests.map {
            if (it.state == GuestState.Self) it.copy(state = GuestState.Arrived, x = 0.50f, y = 0.42f, etaMinutes = 0)
            else it
        }
    } else baseGuests
    val stats = computeTrackingStats(guests)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (arrived) SpacerLiveGreen.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (arrived) SpacerLiveGreen else SpacerGold, CircleShape)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = if (arrived) "YOU'RE HERE" else "LIVE · ARRIVING NOW",
                        color = if (arrived) SpacerLiveGreen else SpacerGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp
                    )
                }
                Text(
                    text = startTimeLabel,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(onClick = onExpand)
            ) {
                SpacerMap(
                    modifier = Modifier.fillMaxSize(),
                    guests = guests,
                    showRoute = !arrived
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                    shape = CircleShape,
                    color = Color(0xAA0A0818),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⤢", color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Expand",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 14.dp, top = 40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${stats.percent}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.8).sp
                                )
                                Text(
                                    "%",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 22.sp,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                                )
                            }
                            Text(
                                "${stats.arrived} arrived · ${stats.enroute} en route",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        AvatarStack(
                            guests = guests.filter { it.state != GuestState.Self && it.x != null }.take(4)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text(
                            eventTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            venueLine,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (!arrived) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "YOUR ETA",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "$yourEtaMinutes min",
                                color = SpacerGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.size(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (arrived) {
                        SpacerButton(
                            label = "Open chat",
                            onClick = onChat,
                            kind = SpacerButtonKind.Primary,
                            size = SpacerButtonSize.Md,
                            modifier = Modifier.weight(1f)
                        )
                        SpacerButton(
                            label = "See map",
                            onClick = onExpand,
                            kind = SpacerButtonKind.Secondary,
                            size = SpacerButtonSize.Md
                        )
                    } else {
                        SpacerButton(
                            label = "I'm here",
                            onClick = onArrive,
                            kind = SpacerButtonKind.Gold,
                            size = SpacerButtonSize.Md,
                            modifier = Modifier.weight(1f)
                        )
                        SpacerButton(
                            label = "Chat",
                            onClick = onChat,
                            kind = SpacerButtonKind.Secondary,
                            size = SpacerButtonSize.Md
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarStack(guests: List<TrackedGuest>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        guests.take(3).forEachIndexed { index, guest ->
            val color = Color.hsv(guest.hue.toFloat(), 0.45f, 0.78f)
            Box(
                modifier = Modifier
                    .padding(start = if (index == 0) 0.dp else 0.dp)
                    .offset(x = (index * (-10)).dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    guest.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (guests.size > 3) {
            Box(
                modifier = Modifier
                    .offset(x = (3 * -10).dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+${guests.size - 3}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LiveMapScreen(
    eventTitle: String = "vc meeting",
    arrived: Boolean = false,
    onBack: () -> Unit = {},
    onArrive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localArrived by remember { mutableStateOf(arrived) }
    val baseGuests = remember { DemoGuests }
    val guests = if (localArrived) {
        baseGuests.map {
            if (it.state == GuestState.Self) it.copy(state = GuestState.Arrived, x = 0.50f, y = 0.42f, etaMinutes = 0)
            else it
        }
    } else baseGuests
    val stats = computeTrackingStats(guests)

    val sortedGuests = guests.sortedWith(compareBy(
        { when (it.state) { GuestState.Arrived -> 0; GuestState.Self -> 1; GuestState.EnRoute -> 2; GuestState.Private -> 3 } },
        { it.etaMinutes ?: 999 }
    ))

    Box(modifier = modifier.fillMaxSize()) {
        SpacerMap(
            modifier = Modifier.fillMaxSize(),
            guests = guests,
            showRoute = !localArrived,
            showVenueLabel = true,
            venueLabel = eventTitle
        )

        // Top chrome
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xCC0A0818),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = CircleShape,
                color = Color(0xCC0A0818),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .size(8.dp)
                        .background(SpacerGold, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            eventTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${stats.percent}% ON THE WAY · ${stats.arrived} HERE",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        // Floating I'm here CTA
        if (!localArrived) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 360.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SpacerGold,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                localArrived = true
                                onArrive()
                            }
                            .padding(horizontal = 26.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF1A1408),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "I'm here · 0.1 mi away",
                            color = Color(0xFF1A1408),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(340.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 24.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${stats.arrived}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.6).sp
                            )
                            Text(
                                "of ${stats.total} here",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                            )
                        }
                        Text(
                            "${stats.enroute} on the way · ${guests.count { it.state == GuestState.Private }} private",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    SpacerChip(
                        text = "10:00 PM",
                        tone = SpacerChipTone.Gold,
                        size = SpacerChipSize.Md
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    sortedGuests.forEach { guest ->
                        GuestRowLive(guest)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMapScreenForLiveData(
    eventTitle: String,
    guests: List<EventLiveGuestUi>,
    currentUserId: String?,
    arrived: Boolean,
    onBack: () -> Unit,
    onArrive: () -> Unit
) {
    val mapped = remember(guests, currentUserId, arrived) {
        trackedFromLive(guests, currentUserId, arrived)
    }
    val stats = computeTrackingStats(mapped)
    val sortedGuests = mapped.sortedWith(compareBy(
        { when (it.state) { GuestState.Arrived -> 0; GuestState.Self -> 1; GuestState.EnRoute -> 2; GuestState.Private -> 3 } },
        { it.etaMinutes ?: 999 }
    ))

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        SpacerMap(
            modifier = Modifier.fillMaxSize(),
            guests = mapped,
            showRoute = !arrived,
            showVenueLabel = true,
            venueLabel = eventTitle
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xCC0A0818),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = CircleShape,
                color = Color(0xCC0A0818),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .size(8.dp)
                        .background(SpacerGold, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            eventTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${stats.percent}% ON THE WAY · ${stats.arrived} HERE",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        if (!arrived) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 360.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SpacerGold,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onArrive)
                            .padding(horizontal = 26.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF1A1408),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "I'm here · 0.1 mi away",
                            color = Color(0xFF1A1408),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(340.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 24.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${stats.arrived}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.6).sp
                            )
                            Text(
                                "of ${stats.total} here",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                            )
                        }
                        Text(
                            "${stats.enroute} on the way · ${mapped.count { it.state == GuestState.Private }} private",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    sortedGuests.forEach { guest ->
                        GuestRowLive(guest)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestRowLive(guest: TrackedGuest) {
    val avatarColor = Color.hsv(guest.hue.toFloat(), 0.45f, 0.78f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(avatarColor, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                guest.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                if (guest.state == GuestState.Self) "You" else guest.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                guest.distance,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }
        when (guest.state) {
            GuestState.Arrived -> SpacerChip(text = "Arrived", tone = SpacerChipTone.Success, size = SpacerChipSize.Sm)
            GuestState.Self -> SpacerChip(text = "${guest.etaMinutes} min · You", tone = SpacerChipTone.Gold, size = SpacerChipSize.Sm)
            GuestState.Private -> SpacerChip(text = "Sharing off", tone = SpacerChipTone.Default, size = SpacerChipSize.Sm)
            GuestState.EnRoute -> SpacerChip(text = "${guest.etaMinutes} min away", tone = SpacerChipTone.Purple, size = SpacerChipSize.Sm)
        }
    }
}
