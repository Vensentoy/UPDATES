package com.revyu.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Slightly squared-off corners — closer to a cut sheet of paper than a soft app-icon
// rounded rect. Keeps the "printed reviewer" feel consistent in cards and sheets.
val RevyuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

// Radius used specifically by the Margin Rule tab dot / pill components.
val MarginRuleWidth = 3.dp
val MarginTabSize = 10.dp
