package com.movieworld.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val MovieWorldColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberDeep,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = Night,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextPrimary,
    tertiary = Success,
    onTertiary = Night,
    background = Night,
    onBackground = TextPrimary,
    surface = Night,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    inverseSurface = TextPrimary,
    inverseOnSurface = Night,
    outline = Outline,
    outlineVariant = Surface3,
    error = Danger,
    onError = OnDanger,
    errorContainer = Danger.copy(alpha = 0.16f),
    onErrorContainer = Danger,
    scrim = Night,
)

/** Dark-only theme. The activity draws edge-to-edge; system bars are transparent. */
@Composable
fun MovieWorldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MovieWorldColors, typography = MovieWorldTypography, shapes = MovieWorldShapes) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}
