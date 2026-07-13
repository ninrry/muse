package luzzr.muse.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.home.R
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
fun HomeScreen(
    songs: List<Song>,
    isScanning: Boolean,
    scanProgress: Int,
    currentSong: Song?,
    greeting: GreetingPeriod,
    innerPadding: PaddingValues = PaddingValues(),
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onScan: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlaySong: (Int) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (songs.isEmpty()) {
            EmptyState(
                isScanning = isScanning,
                scanProgress = scanProgress,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onScan = onScan
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Ultra-compact header
                val greetingText = stringResource(
                    when (greeting) {
                        GreetingPeriod.MORNING -> R.string.greeting_morning
                        GreetingPeriod.AFTERNOON -> R.string.greeting_afternoon
                        GreetingPeriod.EVENING -> R.string.greeting_evening
                    }
                )
                CompactHeader(
                    greeting = greetingText,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle
                )

                // Recent songs section title
                val recentSongs = songs.take(20)
                if (recentSongs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(
                        items = recentSongs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        SongListItem(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            onClick = { onPlaySong(index) },
                            artworkSize = MuseDimens.ArtworkSizeSmall
                        )
                    }
                    // Bottom spacing for MiniPlayer clearance
                    item { Spacer(Modifier.height(MuseDimens.MiniPlayerClearance)) }
                }
            }
        }
    }
}

@Composable
private fun CompactHeader(greeting: String, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxxs)
    ) {
        // Row 1: "Muse 下午好" as strong header — full width, bold
        Text(
            text = stringResource(R.string.home_greeting, greeting),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = AppSpacing.xxs)
        )

        Spacer(Modifier.height(AppSpacing.xxxs))

        // Row 2: Quick actions with visual differentiation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MuseDimens.CornerRadiusSmall)
        ) {
            FilledTonalButton(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f).height(MuseDimens.ButtonHeightMedium),
                shape = RoundedCornerShape(MuseDimens.CornerRadiusLarge)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(MuseDimens.IconSizeSmall))
                Spacer(Modifier.width(MuseDimens.SpacingSmall))
                Text(
                    stringResource(R.string.home_play_all),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            FilledTonalButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f).height(MuseDimens.ButtonHeightMedium),
                shape = RoundedCornerShape(MuseDimens.CornerRadiusLarge)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(MuseDimens.IconSizeSmall))
                Spacer(Modifier.width(MuseDimens.SpacingSmall))
                Text(
                    stringResource(R.string.home_shuffle),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    isScanning: Boolean,
    scanProgress: Int,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.xlg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Breathing animation on the empty state icon
            // MD3 micro-interaction guidelines: ~300ms for subtle effects
            val infiniteTransition = rememberInfiniteTransition(label = "empty_breathe")
            val breatheScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathe_scale"
            )
            val breatheAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathe_alpha"
            )
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .size(AppSpacing.xxxlg)
                    .graphicsLayer {
                        scaleX = breatheScale
                        scaleY = breatheScale
                        alpha = breatheAlpha
                    },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                stringResource(R.string.home_empty),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                if (hasPermission) stringResource(R.string.home_scan_hint) else stringResource(R.string.home_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpacing.xlg))

            if (!hasPermission) {
                FilledTonalButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .height(MuseDimens.ButtonHeightLarge)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(MuseDimens.IconSizeNormal)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(stringResource(R.string.home_grant_permission), style = MaterialTheme.typography.labelLarge)
                }
            } else if (isScanning) {
                LinearProgressIndicator(
                    progress = { scanProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.bodyMedium)
            } else {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier
                        .height(MuseDimens.ButtonHeightLarge)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
                ) {
                    Icon(
                        Icons.Default.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(MuseDimens.IconSizeNormal)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(stringResource(R.string.home_start_scan), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
