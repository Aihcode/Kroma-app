package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Organic & Asymmetric Material 3 Expressive Shape System
val KromaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp, bottomEnd = 24.dp, bottomStart = 12.dp),
    large = RoundedCornerShape(topStart = 32.dp, topEnd = 16.dp, bottomEnd = 32.dp, bottomStart = 16.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
val OrganicHeroShape = RoundedCornerShape(topStart = 36.dp, topEnd = 16.dp, bottomEnd = 36.dp, bottomStart = 16.dp)
val OrganicBubbleShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
