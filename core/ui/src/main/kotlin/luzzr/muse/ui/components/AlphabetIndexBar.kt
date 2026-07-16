package luzzr.muse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * iOS 通讯录式侧边字母条：无矩形底、无阴影。
 * 反馈 = 字母缩放/变色 + 跟随手指的圆形气泡 + 轻触觉。
 */
@Composable
fun AlphabetIndexBar(
    modifier: Modifier = Modifier,
    letters: List<Char> = ALPHABET_INDEX,
    onLetterSelected: (Int) -> Unit = {},
    currentIndex: Int = -1,
    availableIndices: Set<Int> = letters.indices.toSet(),
    showCurrentIndicator: Boolean = true
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isInteracting by remember { mutableStateOf(false) }
    // 气泡相对 bar 顶部的 Y（px），跟随手指
    var bubbleY by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    fun selectAt(y: Float, height: Float) {
        if (letters.isEmpty() || height <= 0f) return
        val itemHeight = height / letters.size
        val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
        bubbleY = (index + 0.5f) * itemHeight
        if (index != selectedIndex) {
            selectedIndex = index
            if (index in availableIndices) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onLetterSelected(index)
            }
        }
    }

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            // 刻意无 background / 无 elevation，避免任何矩形阴影
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        selectAt(offset.y, size.height.toFloat())
                    },
                    onDragEnd = {
                        isInteracting = false
                        selectedIndex = -1
                    },
                    onDragCancel = {
                        isInteracting = false
                        selectedIndex = -1
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        selectAt(change.position.y, size.height.toFloat())
                    }
                )
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        isInteracting = true
                        try {
                            selectAt(offset.y, size.height.toFloat())
                            awaitRelease()
                        } finally {
                            isInteracting = false
                            selectedIndex = -1
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            letters.forEachIndexed { index, letter ->
                val isAvailable = index in availableIndices
                val isActive = selectedIndex == index ||
                    (!isInteracting && selectedIndex < 0 && currentIndex == index)

                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                    },
                    animationSpec = tween(80),
                    label = "letter_color"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.25f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "letter_scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }
            }
        }

        // 仅交互时显示气泡，跟随当前字母；无 idle 常驻方块
        val showBubble = showCurrentIndicator && isInteracting && selectedIndex in letters.indices
        if (showBubble) {
            val letter = letters[selectedIndex]
            val bubbleSizePx = with(density) { 36.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = -with(density) { 42.dp.roundToPx() },
                            y = (bubbleY - bubbleSizePx / 2f).roundToInt()
                        )
                    }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .graphicsLayer {
                        // 轻微弹出，无阴影 elevation
                        scaleX = 1f
                        scaleY = 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

val ALPHABET_INDEX = listOf(
    '#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
)

fun getAlphabetIndex(title: String): Int {
    if (title.isBlank()) return 0
    val firstChar = title.first().uppercaseChar()
    val index = ALPHABET_INDEX.indexOf(firstChar)
    return if (index >= 0) index else 0
}

fun getLetterForIndex(index: Int): Char {
    return ALPHABET_INDEX.getOrElse(index) { '#' }
}
