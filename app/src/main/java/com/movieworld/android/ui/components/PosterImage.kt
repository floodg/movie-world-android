package com.movieworld.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.movieworld.android.ui.theme.Surface1
import com.movieworld.android.ui.theme.Surface3

/**
 * Remote artwork with crossfade, memory and disk caching, and a deliberate dark placeholder so
 * the catalogue still looks intentional offline. Coil sizes the decode to the composable bounds,
 * so a poster row never decodes a full backdrop.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderLabel: String? = null,
    iconSize: Int = 28,
) {
    val context = LocalContext.current
    if (url?.startsWith("https://") != true) {
        PlaceholderTile(placeholderLabel, modifier, iconSize)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(250).build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { ShimmerBox(Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape) },
        error = { PlaceholderTile(placeholderLabel, Modifier.fillMaxSize(), iconSize) },
    )
}

@Composable
fun PlaceholderTile(label: String?, modifier: Modifier = Modifier, iconSize: Int = 28) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(Surface3, Surface1))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Icon(AppIcons.Film, contentDescription = null, Modifier.size(iconSize.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            if (!label.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
