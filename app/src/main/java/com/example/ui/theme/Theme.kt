package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class KromaAccents(
    val violet: Color = ElectricViolet,
    val cobalt: Color = CobaltBlue,
    val coral: Color = EnergeticCoral,
    val emerald: Color = MintEmerald,
    val amber: Color = AmberGold,
)

val LocalKromaAccents = staticCompositionLocalOf { KromaAccents() }

private val KromaDarkColorScheme = darkColorScheme(
    primary = ElectricVioletLight,
    onPrimary = Color(0xFF1D0061),
    primaryContainer = ElectricVioletContainer,
    onPrimaryContainer = OnElectricVioletContainer,
    secondary = CobaltBlueLight,
    onSecondary = Color(0xFF00255E),
    secondaryContainer = CobaltContainer,
    onSecondaryContainer = OnCobaltContainer,
    tertiary = EnergeticCoralLight,
    onTertiary = Color(0xFF56000A),
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = OnCoralContainer,
    background = ObsidianBackground,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = EnergeticCoralLight,
)

private val KromaLightColorScheme = lightColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF230070),
    secondary = CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E6FF),
    onSecondaryContainer = Color(0xFF001C4A),
    tertiary = EnergeticCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD8),
    onTertiaryContainer = Color(0xFF410006),
    background = LightStudioBackground,
    onBackground = LightInk,
    surface = LightStudioSurface,
    onSurface = LightInk,
    surfaceVariant = LightStudioContainer,
    onSurfaceVariant = LightInkMuted,
    surfaceContainerLow = LightStudioSurface,
    surfaceContainer = LightStudioContainer,
    surfaceContainerHigh = LightStudioContainerHigh,
)

@Composable
fun KromaStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KromaDarkColorScheme
        else -> KromaLightColorScheme
    }

    val accents = KromaAccents(
        violet = if (darkTheme) ElectricVioletLight else ElectricViolet,
        cobalt = if (darkTheme) CobaltBlueLight else CobaltBlue,
        coral = if (darkTheme) EnergeticCoralLight else EnergeticCoral,
        emerald = MintEmerald,
        amber = AmberGold,
    )

    CompositionLocalProvider(LocalKromaAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KromaTypography,
            shapes = KromaShapes,
            content = content,
        )
    }
}
