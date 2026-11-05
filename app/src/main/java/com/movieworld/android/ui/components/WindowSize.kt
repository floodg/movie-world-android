package com.movieworld.android.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/** Current window size in dp, used for hero and backdrop heights and landscape layouts. */
@Composable
fun windowSize(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

val DpSize.isLandscape: Boolean get() = width > height

/**
 * Bottom space a scrolling screen must leave free: the scaffold's bottom bar when one is shown,
 * otherwise the system navigation bar.
 */
@Composable
fun PaddingValues.bottomClearance(): Dp =
    maxOf(calculateBottomPadding(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
