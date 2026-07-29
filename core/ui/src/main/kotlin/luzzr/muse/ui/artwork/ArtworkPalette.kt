package luzzr.muse.ui.artwork

import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val ambientStart: Color,
    val ambientMiddle: Color,
    val ambientEnd: Color,
    val surface: Color,
    val onSurface: Color
)

private data class ExtractedArtworkColors(
    val dominant: Int,
    val muted: Int,
    val vibrant: Int
)

private object ArtworkColorCache : LruCache<String, ExtractedArtworkColors>(48)

@Composable
fun rememberArtworkPalette(artworkUri: String?): ArtworkPalette {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val fallback = remember(scheme) { scheme.toArtworkPalette() }
    val extracted by produceState<ExtractedArtworkColors?>(
        initialValue = artworkUri?.let(ArtworkColorCache::get),
        artworkUri,
        scheme
    ) {
        if (artworkUri.isNullOrBlank()) {
            value = null
            return@produceState
        }
        ArtworkColorCache.get(artworkUri)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .size(256)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                    ?: return@runCatching null
                val bitmap = result.drawable.toBitmap(width = 256, height = 256)
                val palette = Palette.from(bitmap)
                    .maximumColorCount(20)
                    .resizeBitmapArea(256 * 256)
                    .generate()
                ExtractedArtworkColors(
                    dominant = palette.dominantSwatch?.rgb ?: scheme.primary.toArgb(),
                    muted = palette.mutedSwatch?.rgb
                        ?: palette.darkMutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: scheme.secondary.toArgb(),
                    vibrant = palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: scheme.tertiary.toArgb()
                )
            }.getOrNull()
        }?.also { ArtworkColorCache.put(artworkUri, it) }
    }

    return remember(extracted, fallback, scheme) {
        extracted?.toArtworkPalette(scheme) ?: fallback
    }
}

private fun ExtractedArtworkColors.toArtworkPalette(scheme: ColorScheme): ArtworkPalette {
    val dark = scheme.background.luminance() < 0.45f
    val dominantColor = Color(dominant).editorialized(dark)
    val mutedColor = Color(muted).editorialized(dark)
    val vibrantColor = Color(vibrant).editorialized(dark)
    val primary = lerp(scheme.primary, vibrantColor, if (dark) 0.58f else 0.48f)
    val secondary = lerp(scheme.secondary, mutedColor, 0.58f)
    val tertiary = lerp(scheme.tertiary, dominantColor, 0.48f)
    val surface = lerp(scheme.surfaceContainerHigh, mutedColor, if (dark) 0.18f else 0.12f)
    return ArtworkPalette(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        onPrimary = readableInk(primary),
        ambientStart = dominantColor.copy(alpha = if (dark) 0.72f else 0.46f),
        ambientMiddle = vibrantColor.copy(alpha = if (dark) 0.46f else 0.30f),
        ambientEnd = scheme.background,
        surface = surface,
        onSurface = readableInk(surface)
    )
}

private fun ColorScheme.toArtworkPalette(): ArtworkPalette = ArtworkPalette(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    onPrimary = onPrimary,
    ambientStart = primaryContainer.copy(alpha = 0.64f),
    ambientMiddle = tertiaryContainer.copy(alpha = 0.38f),
    ambientEnd = background,
    surface = surfaceContainerHigh,
    onSurface = onSurface
)

private fun Color.editorialized(dark: Boolean): Color {
    val neutral = if (dark) Color(0xFF2B2520) else Color(0xFFE8DDCC)
    return lerp(neutral, this, if (dark) 0.72f else 0.64f)
}

private fun readableInk(background: Color): Color {
    val cream = Color(0xFFFFF8ED)
    val espresso = Color(0xFF211914)
    return if (contrastRatio(background, cream) >= contrastRatio(background, espresso)) cream else espresso
}

private fun contrastRatio(first: Color, second: Color): Float {
    val high = maxOf(first.luminance(), second.luminance())
    val low = minOf(first.luminance(), second.luminance())
    return (high + 0.05f) / (low + 0.05f)
}
