package com.movieworld.android.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.movieworld.android.ui.components.PrimaryButton
import com.movieworld.android.ui.components.SecondaryButton
import com.movieworld.android.ui.theme.Dimens
import com.movieworld.android.ui.theme.SheetShape
import kotlin.math.roundToInt

/** Year range filter. Touching neither end of the slider means "any year". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    bounds: IntRange,
    from: Int?,
    to: Int?,
    onApply: (Int?, Int?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var range by remember(bounds, from, to) {
        mutableStateOf((from ?: bounds.first).toFloat()..(to ?: bounds.last).toFloat())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.xl).navigationBarsPadding().padding(bottom = Dimens.xl)) {
            Text("Filters", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Dimens.xl))
            val start = range.start.roundToInt()
            val end = range.endInclusive.roundToInt()
            Row(Modifier.fillMaxWidth()) {
                Text("Release year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$start – $end", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = bounds.first.toFloat()..bounds.last.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Release year from $start to $end" },
            )
            Spacer(Modifier.height(Dimens.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                SecondaryButton("Clear", onClick = onClear, modifier = Modifier.weight(1f))
                PrimaryButton(
                    "Apply",
                    onClick = {
                        onApply(start.takeIf { it > bounds.first }, end.takeIf { it < bounds.last })
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
