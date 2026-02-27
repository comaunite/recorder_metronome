package com.recorder.composable.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.recorder.data.WaveformData
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun WaveformVisualizer(
    waveformData: WaveformData,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    enableScrubbing: Boolean = false,
    onScrubPosition: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    val barWidthPx = remember(density) { with(density) { 1.dp.toPx() } }
    val barSpacingPx = remember(density) { with(density) { 1.dp.toPx() } }
    val barFullWidthPx = barWidthPx + barSpacingPx

    val scrubModifier = if (enableScrubbing && onScrubPosition != null) {
        Modifier.pointerInput(enableScrubbing) {
            var dragStartIndex = 0
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val totalBars = waveformData.amplitudes.size
                    if (totalBars > 0 && barFullWidthPx > 0f) {
                        val centerX = size.width / 2f
                        val barsToLeft = (centerX / barFullWidthPx).toInt()
                        val baseIndex = (waveformData.currentPosition - barsToLeft)
                            .coerceIn(0, totalBars - 1)

                        val indexFromLeft = floor(offset.x / barFullWidthPx).toInt()
                        dragStartIndex = (baseIndex + indexFromLeft).coerceIn(0, totalBars - 1)
                        onScrubPosition(dragStartIndex)
                    }
                },
                onDrag = { change, _ ->
                    val totalBars = waveformData.amplitudes.size
                    if (totalBars > 0 && barFullWidthPx > 0f) {
                        val centerX = size.width / 2f
                        val barsToLeft = (centerX / barFullWidthPx).toInt()
                        val baseIndex = (dragStartIndex - barsToLeft)
                            .coerceIn(0, totalBars - 1)

                        val indexFromLeft = floor(change.position.x / barFullWidthPx).toInt()
                        val targetIndex = (baseIndex + indexFromLeft).coerceIn(0, totalBars - 1)
                        onScrubPosition(targetIndex)
                        change.consume()
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .then(scrubModifier)
            .fillMaxWidth()
            .height(150.dp)
    ) {
        drawRect(color = backgroundColor)

        val amplitudes = waveformData.amplitudes
        val currentPosition = waveformData.currentPosition

        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        // Draw the center line
        drawLine(
            color = waveColor.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1f
        )

        if (amplitudes.isNotEmpty()) {
            val maxAmplitude = waveformData.maxAmplitude.coerceAtLeast(1f)
            val barFullWidth = barWidthPx + barSpacingPx

            // Calculate which bars to draw based on current position
            // The current position should be at centerX
            val barsToLeft = (centerX / barFullWidth).toInt()
            val barsToRight = ((width - centerX) / barFullWidth).toInt()

            // Calculate the start and end indices for the bars to be drawn
            val startIndex = (currentPosition - barsToLeft).coerceAtLeast(0)
            val endIndex = (currentPosition + barsToRight).coerceAtMost(amplitudes.size - 1)

            // Draw amplitude bars only if we have a valid range
            if (startIndex <= endIndex) {
                for (i in startIndex..endIndex) {
                    val amplitude = amplitudes[i]
                    val normalized = (abs(amplitude) / maxAmplitude).coerceIn(0f, 1f)
                    val barHeight = (height / 2) * normalized

                    // Calculate x position relative to current position
                    val offsetFromCurrent = i - currentPosition
                    val xPosition = centerX + (offsetFromCurrent * barFullWidth)

                    drawRect(
                        color = waveColor,
                        topLeft = Offset(
                            xPosition,
                            centerY - barHeight / 2
                        ),
                        size = Size(
                            barWidthPx.coerceAtLeast(1f),
                            barHeight.coerceAtLeast(1f)
                        )
                    )
                }
            }
        }

        // Draw red playback line on top
        drawLine(
            color = Color.Red,
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 3f
        )
    }
}
