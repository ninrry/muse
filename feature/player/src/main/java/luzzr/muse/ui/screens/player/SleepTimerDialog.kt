package luzzr.muse.ui.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.feature.player.R
import luzzr.muse.media.SleepTimerMode
import luzzr.muse.ui.components.MuseAlertDialog
import luzzr.muse.ui.components.MusePrimaryActionTile
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

/** 滚轮可选分钟：5 分钟步进，1h 内细一些 */
private val TIMER_MINUTES: List<Int> = buildList {
    addAll(listOf(1, 2, 3, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60))
    addAll((75..180 step 15))
}

/**
 * 定时关闭：滚轮选择时长（拖动滚动），极简确认。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SleepTimerDialog(
    currentMode: SleepTimerMode?,
    remainingMs: Long? = null,
    onSelect: (SleepTimerMode, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMin = remember(remainingMs, currentMode) {
        when {
            remainingMs != null && remainingMs > 0 -> {
                val m = ((remainingMs + 30_000L) / 60_000L).toInt().coerceIn(1, 180)
                TIMER_MINUTES.minByOrNull { kotlin.math.abs(it - m) } ?: 30
            }
            currentMode == SleepTimerMode.MIN_15 -> 15
            currentMode == SleepTimerMode.MIN_30 -> 30
            currentMode == SleepTimerMode.MIN_45 -> 45
            currentMode == SleepTimerMode.MIN_60 -> 60
            currentMode == SleepTimerMode.MIN_90 -> 90
            else -> 30
        }
    }

    val itemHeight = 44.dp
    val visibleCount = 5
    val wheelHeight = itemHeight * visibleCount
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val initialIndex = TIMER_MINUTES.indexOf(initialMin).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex
    )
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)

    // 当前居中项
    val selectedIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val base = listState.firstVisibleItemIndex
            val adj = if (offset > itemHeightPx / 2f) 1 else 0
            (base + adj).coerceIn(0, TIMER_MINUTES.lastIndex)
        }
    }
    val selectedMinutes = TIMER_MINUTES.getOrElse(selectedIndex) { 30 }

    // 打开时滚到初始项（居中：先滚到 index，再靠 padding 居中）
    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
    }

    MuseAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.sleep_timer_title),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 滚轮（时长只在轮上显示，不重复大标题）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(wheelHeight)
                        .clip(MuseShapeTokens.Card)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    // 居中高亮条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                    )

                    val pad = itemHeight * ((visibleCount - 1) / 2)
                    LazyColumn(
                        state = listState,
                        flingBehavior = snapFling,
                        contentPadding = PaddingValues(vertical = pad),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(TIMER_MINUTES.size) { index ->
                            val min = TIMER_MINUTES[index]
                            val isSelected = index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatMinutesLabel(min),
                                    style = if (isSelected) {
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 上下渐隐
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.surfaceContainerHigh,
                                    0.22f to Color.Transparent,
                                    0.78f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                    )
                }

                Spacer(Modifier.height(AppSpacing.md))

                // 次要：关闭 / 播完本首；主操作：开始（与多选坞同型瓦片）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    TextButton(
                        onClick = { onSelect(SleepTimerMode.OFF, null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sleep_timer_off))
                    }
                    TextButton(
                        onClick = { onSelect(SleepTimerMode.END_OF_TRACK, null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sleep_timer_end), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(AppSpacing.xs))
                MusePrimaryActionTile(
                    label = stringResource(R.string.sleep_timer_start),
                    icon = Icons.Default.Schedule,
                    onClick = { onSelect(SleepTimerMode.CUSTOM, selectedMinutes) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (currentMode != null && currentMode != SleepTimerMode.OFF && remainingMs != null) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(
                            R.string.sleep_timer_remaining,
                            formatRemaining(remainingMs)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
private fun formatMinutesLabel(min: Int): String {
    return if (min >= 60 && min % 60 == 0) {
        stringResource(R.string.sleep_timer_display_h, min / 60)
    } else if (min >= 60) {
        stringResource(R.string.sleep_timer_display_hm_short, min / 60, min % 60)
    } else {
        stringResource(R.string.sleep_timer_display_m_short, min)
    }
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
