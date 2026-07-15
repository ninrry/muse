package luzzr.muse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alphabet index bar for quick navigation in lists.
 * Similar to contact list or music player letter navigation.
 *
 * @param letters The list of letters to display (can be dynamic based on content)
 * @param onLetterSelected Callback when a letter is selected (provides index)
 * @param currentIndex The currently visible letter index (for visual highlight)
 * @param availableIndices Set of indices that have content (will be highlighted, others dimmed)
 * @param showCurrentIndicator Whether to show a bubble indicator for current letter
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
    var selectedIndex by remember { mutableStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    var indicatorLetter by remember { mutableStateOf<Char?>(null) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDragging || selectedIndex >= 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            else -> Color.Transparent
        },
        label = "alphabet_bg"
    )

    Box(
        modifier = modifier
            .width(32.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        selectedIndex = -1
                        indicatorLetter = null
                    },
                    onDragCancel = {
                        isDragging = false
                        selectedIndex = -1
                        indicatorLetter = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val y = change.position.y
                        val itemHeight = size.height.toFloat() / letters.size
                        val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                        if (index != selectedIndex) {
                            selectedIndex = index
                            indicatorLetter = letters.getOrNull(index)
                            // Only trigger if this letter has content
                            if (index in availableIndices) {
                                onLetterSelected(index)
                            }
                        }
                    }
                )
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        try {
                            val y = offset.y
                            val itemHeight = size.height.toFloat() / letters.size
                            val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                            selectedIndex = index
                            indicatorLetter = letters.getOrNull(index)
                            // Only trigger if this letter has content
                            if (index in availableIndices) {
                                onLetterSelected(index)
                            }
                            awaitRelease()
                        } finally {
                            isDragging = false
                            selectedIndex = -1
                            indicatorLetter = null
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            letters.forEachIndexed { index, letter ->
                val isAvailable = index in availableIndices
                val isActive = selectedIndex == index || (selectedIndex < 0 && currentIndex == index)
                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    },
                    label = "letter_color"
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
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Current letter indicator bubble: follows the finger while interacting,
        // and persistently shows the letter at the current scroll position when idle.
        val persistentLetter = indicatorLetter
            ?: currentIndex.takeIf { it in letters.indices }?.let { letters[it] }
        if (showCurrentIndicator && persistentLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-28).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = persistentLetter.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Default alphabet index including # for numbers/symbols
 */
val ALPHABET_INDEX = listOf(
    '#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
)

/**
 * Get the index in the alphabet list for a given character.
 * Returns the index in ALPHABET_INDEX for the first letter of the given string,
 * or 0 (#) if not found.
 */
fun getAlphabetIndex(title: String): Int {
    if (title.isBlank()) return 0
    val firstChar = title.first().uppercaseChar()
    val index = ALPHABET_INDEX.indexOf(firstChar)
    return if (index >= 0) index else 0
}

/**
 * Get the letter for a given index in the alphabet list.
 */
fun getLetterForIndex(index: Int): Char {
    return ALPHABET_INDEX.getOrElse(index) { '#' }
}