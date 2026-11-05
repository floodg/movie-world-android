package com.movieworld.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.movieworld.android.ui.theme.Amber
import com.movieworld.android.ui.theme.PillShape

/** Compact IMDb rating pill: star glyph plus the score. */
@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier, onDark: Boolean = true) {
    val container = if (onDark) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "IMDb rating $rating" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(12.dp), tint = Amber)
        Spacer(Modifier.width(3.dp))
        Text(rating, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Selectable genre chip used in search and detail. */
@Composable
fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = PillShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = null,
    )
}
