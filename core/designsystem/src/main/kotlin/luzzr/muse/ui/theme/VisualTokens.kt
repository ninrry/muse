package luzzr.muse.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Muse's editorial surface language. These values deliberately sit above the
 * Material color scheme so feature screens do not invent one-off opacity,
 * border and elevation values.
 */
@Immutable
data class MuseVisualStyle(
    val pageGlow: Color,
    val pageGlowSecondary: Color,
    val paperGrain: Color,
    val cardBorder: Color,
    val floatingSurface: Color,
    val mutedInk: Color,
    val heroScrim: Color,
    val cardElevation: Dp,
    val floatingElevation: Dp
)

internal val LightMuseVisualStyle = MuseVisualStyle(
    pageGlow = LightPrimaryContainer.copy(alpha = 0.58f),
    pageGlowSecondary = LightTertiaryContainer.copy(alpha = 0.34f),
    paperGrain = Color(0x1835281F),
    cardBorder = LightOutlineVariant.copy(alpha = 0.72f),
    floatingSurface = LightSurface.copy(alpha = 0.96f),
    mutedInk = OnLightSurfaceVariant.copy(alpha = 0.72f),
    heroScrim = Color(0x8A24180F),
    cardElevation = 1.dp,
    floatingElevation = 10.dp
)

internal val DarkMuseVisualStyle = MuseVisualStyle(
    pageGlow = DarkPrimaryContainer.copy(alpha = 0.48f),
    pageGlowSecondary = DarkTertiaryContainer.copy(alpha = 0.32f),
    paperGrain = Color(0x24F3ECE4),
    cardBorder = DarkOutlineVariant.copy(alpha = 0.78f),
    floatingSurface = DarkSurfaceContainerHigh.copy(alpha = 0.96f),
    mutedInk = OnDarkSurfaceVariant.copy(alpha = 0.78f),
    heroScrim = Color(0xB8141210),
    cardElevation = 0.dp,
    floatingElevation = 12.dp
)

val LocalMuseVisualStyle = staticCompositionLocalOf { LightMuseVisualStyle }
