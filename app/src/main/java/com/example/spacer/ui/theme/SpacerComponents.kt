package com.example.spacer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SpacerRadii {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val pill: Dp = 999.dp
}

object SpacerSpacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 20.dp
    val s6: Dp = 24.dp
    val s7: Dp = 32.dp
    val s8: Dp = 40.dp
}

@Composable
fun SpacerScreenHeader(
    kicker: String? = null,
    title: String? = null,
    italic: String? = null,
    sub: String? = null,
    paddingTop: Dp = 56.dp,
    paddingBottom: Dp = 18.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = paddingTop, bottom = paddingBottom)
    ) {
        if (!kicker.isNullOrBlank()) {
            Text(
                text = kicker.uppercase(),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp
            )
            Spacer(Modifier.height(16.dp))
        }
        if (!title.isNullOrBlank() || !italic.isNullOrBlank()) {
            Text(
                text = buildAnnotatedString {
                    if (!title.isNullOrBlank()) {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        ) { append(title) }
                    }
                    if (!italic.isNullOrBlank()) {
                        if (!title.isNullOrBlank()) append(' ')
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.tertiary,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light
                            )
                        ) { append(italic) }
                    }
                },
                fontSize = 36.sp,
                lineHeight = 39.sp,
                letterSpacing = (-0.5).sp
            )
        }
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = sub,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 14.5.sp
            )
        }
    }
}

@Composable
fun SpacerCompactHeader(
    title: String,
    sub: String? = null,
    onBack: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (onBack != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp
            )
            if (!sub.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.5.sp
                )
            }
        }
        if (right != null) right()
    }
}

@Composable
fun SpacerSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
            modifier = Modifier.weight(1f)
        )
        if (action != null) action()
    }
}

@Composable
fun SpacerCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    padding: Dp = 18.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val container = if (raised) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val base = Modifier
        .then(modifier)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Surface(
        modifier = base,
        shape = shape,
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

enum class SpacerChipTone { Default, Purple, PurpleSolid, Gold, Success, Danger, Ghost }
enum class SpacerChipSize { Sm, Md, Lg }

@Composable
fun SpacerChip(
    text: String,
    tone: SpacerChipTone = SpacerChipTone.Default,
    size: SpacerChipSize = SpacerChipSize.Md,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val purple = MaterialTheme.colorScheme.primary
    val purpleInk = MaterialTheme.colorScheme.primaryContainer
    val gold = MaterialTheme.colorScheme.tertiary
    val goldInk = MaterialTheme.colorScheme.tertiaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = onSurface.copy(alpha = 0.55f)
    val line = MaterialTheme.colorScheme.outline
    val lineStrong = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val (bg, fg, border) = when (tone) {
        SpacerChipTone.Default -> Triple(Color.Transparent, muted, lineStrong)
        SpacerChipTone.Purple -> Triple(purpleInk, purple, line)
        SpacerChipTone.PurpleSolid -> Triple(purple, MaterialTheme.colorScheme.onPrimary, purple)
        SpacerChipTone.Gold -> Triple(goldInk, gold, Color.Transparent)
        SpacerChipTone.Success -> Triple(SpacerLiveGreen.copy(alpha = 0.14f), SpacerLiveGreen, Color.Transparent)
        SpacerChipTone.Danger -> Triple(SpacerDanger.copy(alpha = 0.14f), SpacerDanger, Color.Transparent)
        SpacerChipTone.Ghost -> Triple(surfaceVariant, onSurface, line)
    }

    val (px, py, fs) = when (size) {
        SpacerChipSize.Sm -> Triple(10.dp, 4.dp, 11.sp)
        SpacerChipSize.Md -> Triple(12.dp, 6.dp, 12.sp)
        SpacerChipSize.Lg -> Triple(16.dp, 9.dp, 13.sp)
    }
    val letterSpacing: TextUnit = if (tone == SpacerChipTone.Gold) 1.4.sp else 0.sp

    val base = Modifier
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Surface(
        modifier = base,
        shape = CircleShape,
        color = bg,
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = px, vertical = py),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leading != null) leading()
            Text(
                text = if (tone == SpacerChipTone.Gold) text.uppercase() else text,
                color = fg,
                fontSize = fs,
                fontWeight = FontWeight.Medium,
                letterSpacing = letterSpacing
            )
        }
    }
}

enum class SpacerButtonKind { Primary, Gold, Secondary, Ghost, Danger }
enum class SpacerButtonSize { Sm, Md, Lg }

@Composable
fun SpacerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: SpacerButtonKind = SpacerButtonKind.Primary,
    size: SpacerButtonSize = SpacerButtonSize.Md,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val purple = MaterialTheme.colorScheme.primary
    val purpleInk = MaterialTheme.colorScheme.primaryContainer
    val gold = MaterialTheme.colorScheme.tertiary
    val onPurple = MaterialTheme.colorScheme.onPrimary
    val onGold = Color(0xFF1A1408)
    val line = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    data class Kind(val bg: Color, val fg: Color, val border: Color)
    val k = when (kind) {
        SpacerButtonKind.Primary -> Kind(purple, onPurple, purple)
        SpacerButtonKind.Gold -> Kind(gold, onGold, gold)
        SpacerButtonKind.Secondary -> Kind(Color.Transparent, onSurface, line)
        SpacerButtonKind.Ghost -> Kind(purpleInk, purple, Color.Transparent)
        SpacerButtonKind.Danger -> Kind(Color.Transparent, SpacerDanger, SpacerDanger.copy(alpha = 0.4f))
    }
    val (h, px, fs) = when (size) {
        SpacerButtonSize.Sm -> Triple(36.dp, 14.dp, 13.sp)
        SpacerButtonSize.Md -> Triple(46.dp, 18.dp, 14.5.sp)
        SpacerButtonSize.Lg -> Triple(54.dp, 22.dp, 15.5.sp)
    }
    val alpha = if (enabled) 1f else 0.5f

    Surface(
        modifier = modifier.height(h),
        shape = CircleShape,
        color = k.bg.copy(alpha = if (k.bg == Color.Transparent) 0f else alpha),
        border = if (k.border == Color.Transparent) null else BorderStroke(1.dp, k.border.copy(alpha = alpha))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = label,
                color = k.fg.copy(alpha = alpha),
                fontSize = fs,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp
            )
            if (trailing != null) {
                Spacer(Modifier.size(8.dp))
                trailing()
            }
        }
    }
}

@Composable
fun SpacerSegRadio(
    options: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onSelected(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpacerFloatingMenu(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpacerTipStrip(
    label: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.3.sp,
                modifier = Modifier.widthIn(min = 28.dp)
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SpacerKeyVal(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueSize: TextUnit = 14.sp
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = valueSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SpacerInfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
