package com.example.spacer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SpacerDarkPurple,
    onPrimary = SpacerPurpleOnPrimary,
    secondary = SpacerDarkPurple,
    tertiary = SpacerDarkGold,
    background = SpacerDarkBackground,
    surface = SpacerDarkSurface,
    surfaceVariant = SpacerDarkSurfaceVariant,
    outline = SpacerDarkLine,
    outlineVariant = SpacerDarkLineStrong,
    onBackground = SpacerDarkOnBackground,
    onSurface = SpacerDarkOnBackground,
    onSurfaceVariant = SpacerDarkOnSurfaceVariant,
    primaryContainer = SpacerDarkPurpleInk,
    onPrimaryContainer = SpacerDarkPurple,
    tertiaryContainer = SpacerDarkGoldInk,
    onTertiaryContainer = SpacerDarkGold
)

private val LightColorScheme = lightColorScheme(
    primary = SpacerLightPurple,
    onPrimary = SpacerPurpleOnPrimary,
    secondary = SpacerLightPurple,
    tertiary = SpacerLightGold,
    background = SpacerLightBackground,
    surface = SpacerLightSurface,
    surfaceVariant = SpacerLightSurfaceVariant,
    outline = SpacerLightLine,
    outlineVariant = SpacerLightLineStrong,
    onBackground = SpacerLightOnBackground,
    onSurface = SpacerLightOnBackground,
    onSurfaceVariant = SpacerLightOnSurfaceVariant,
    primaryContainer = SpacerLightPurpleInk,
    onPrimaryContainer = SpacerLightPurple,
    tertiaryContainer = SpacerLightGoldInk,
    onTertiaryContainer = SpacerLightGold
)

@Composable
fun SpacerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
