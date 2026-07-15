package luzzr.muse.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Spec: Card 28dp, Button 999dp, Album Cover 24dp, BottomSheet 32dp
private val MuseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Named shape tokens that complement [MaterialTheme.shapes].
 * Use these for specific component radii that don't fit the MD3 slots.
 */
object MuseShapeTokens {
    /** List item / small interactive element ->12dp */
    val Item = RoundedCornerShape(12.dp)

    /** ElevatedCard / SettingItem / ArtistCard ->20dp */
    val Card = RoundedCornerShape(20.dp)

    /** Album cover / AlbumCard ->24dp */
    val Album = RoundedCornerShape(24.dp)

    /** ModalBottomSheet / large card ->28dp */
    val Sheet = RoundedCornerShape(28.dp)

    /** Pill / fully-rounded button ->999.dp */
    val Pill = RoundedCornerShape(999.dp)

    /** Stat card / large section card ->28dp */
    val SectionCard = RoundedCornerShape(28.dp)
}

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = OnLightPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = OnLightPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = OnLightSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = OnLightSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = OnLightTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = OnLightTertiaryContainer,
    error = LightError,
    onError = OnLightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnLightErrorContainer,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightSurface,
    onSurface = OnLightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = androidx.compose.ui.graphics.Color(0x52000000), // MD3 scrim: 32% black overlay
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceContainerLowest,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = OnDarkPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = OnDarkSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = OnDarkSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = OnDarkTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = OnDarkTertiaryContainer,
    error = DarkError,
    onError = OnDarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = OnDarkErrorContainer,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = androidx.compose.ui.graphics.Color(0x80000000), // Dark theme scrim: 50% black
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceDim = DarkSurfaceContainerLowest,
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

/**
 * 全局无波纹 Indication：彻底禁用所有默认灰色矩形点击波纹，
 * 保障圆角卡片、胶囊按钮等控件不会出现溢出边界的 ripple。
 */
private class NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node = NoIndicationNode()
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun equals(other: Any?): Boolean = other === this
}

private class NoIndicationNode : Modifier.Node()

@Composable
fun MuseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalIndication provides NoIndication()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MuseTypography,
            shapes = MuseShapes,
            content = content
        )
    }
}
