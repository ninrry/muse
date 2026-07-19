package luzzr.muse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * A single-track progress control. Keeping the gesture surface separate from
 * the painted track avoids the default Slider thumb being painted on top of a
 * second custom thumb.
 */
@Composable
fun MuseProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color = activeColor,
    trackHeight: Float = 4f,
    draggingThumbRadius: Float = 8f
) {
    var dragging by remember { mutableStateOf(false) }
    val clampedValue = value.coerceIn(0f, 1f)

    fun valueAt(x: Float, width: Float): Float = if (width <= 0f) {
        clampedValue
    } else {
        (x / width).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x, size.width.toFloat()))
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onValueChange(valueAt(offset.x, size.width.toFloat()))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange(valueAt(change.position.x, size.width.toFloat()))
                    },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val track = max(trackHeight, 1f)
        val startX = 0f
        val endX = size.width
        val activeEnd = startX + (endX - startX) * clampedValue
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(startX, centerY - track / 2f),
            size = androidx.compose.ui.geometry.Size(endX, track),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(track / 2f)
        )
        if (activeEnd > startX) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(startX, centerY - track / 2f),
                size = androidx.compose.ui.geometry.Size(activeEnd, track),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(track / 2f)
            )
        }
        if (dragging) {
            drawCircle(
                color = thumbColor,
                radius = draggingThumbRadius,
                center = Offset(activeEnd, centerY)
            )
        }
    }
}
