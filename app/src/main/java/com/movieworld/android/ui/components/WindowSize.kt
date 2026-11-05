package com.movieworld.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/** Current window size in dp, used for hero and backdrop heights and landscape layouts. */
@Composable
fun windowSize(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

val DpSize.isLandscape: Boolean get() = width > height
