package com.movieworld.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PosterShape = RoundedCornerShape(Dimens.posterCorner)
val SheetShape = RoundedCornerShape(topStart = Dimens.sheetCorner, topEnd = Dimens.sheetCorner)
val CardShape = RoundedCornerShape(Dimens.sheetCorner)
val PillShape = RoundedCornerShape(50)

val MovieWorldShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = PosterShape,
    medium = RoundedCornerShape(12.dp),
    large = CardShape,
    extraLarge = RoundedCornerShape(24.dp),
)
