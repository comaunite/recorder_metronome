package com.recorder.composable.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.recorder.data.WaveformData
import kotlin.math.abs

@Composable
fun WaveformVisualizer(
    waveformData: WaveformData,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    enableScrubbing: Boolean = false,
    onScrubPosition: ((Int) -> Unit)? = null,
    onScrubStart: (() -> Unit)? = null,
    onScrubEnd: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val barWidthPx = remember(density) { with(density) { 1.dp.toPx() } }
    val barSpacingPx = remember(density) { with(density) { 1.dp.toPx() } }
    val barFullWidthPx = barWidthPx + barSpacingPx

    var initialTapX by remember { mutableFloatStateOf(0f) }
    var currentDragX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }

    // Velocity-based seeking: continuously update position based on drag offset
    LaunchedEffect(isDragging) {
        if (isDragging > 0) {
            lastUpdateTime = System.currentTimeMillis()
            while (isActive && isDragging > 0) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastUpdateTime) / 1000f // seconds
                lastUpdateTime = currentTime

                val totalBars = waveformData.amplitudes.size
                if (totalBars > 0 && barFullWidthPx > 0f && deltaTime > 0f) {
                    // Calculate offset from initial tap point in pixels
                    val dragOffset = currentDragX - initialTapX

                    // Convert to bars offset and use as velocity multiplier
                    // Positive = seeking forward, negative = seeking backward
                    val barsOffset = dragOffset / barFullWidthPx

                    // Velocity in bars per second (scale factor controls sensitivity)
                    val velocityScale = 20f // Adjust this to control seeking speed
                    val barsPerSecond = barsOffset * velocityScale

                    // Calculate how many bars to move this frame
                    val barsToMove = (barsPerSecond * deltaTime).toInt()

                    if (barsToMove != 0) {
                        val currentPos = waveformData.currentPosition
                        val targetIndex = (currentPos + barsToMove).coerceIn(0, totalBars - 1)
                        onScrubPosition?.invoke(targetIndex)
                    }
                }

                delay(16) // ~60fps updates
            }
        }
    }

    val scrubModifier = if (enableScrubbing && onScrubPosition != null) {
        Modifier.pointerInput(enableScrubbing) {
            detectDragGestures(
                onDragStart = { offset ->
                    onScrubStart?.invoke()
                    initialTapX = offset.x
                    currentDragX = offset.x
                    isDragging++ // Trigger the LaunchedEffect
                },
                onDrag = { change, _ ->
                    // Just update the current drag position
                    // The LaunchedEffect will handle the velocity-based seeking
                    currentDragX = change.position.x
                    change.consume()
                },
                onDragEnd = {
                    isDragging = 0 // Stop the velocity updates
                    onScrubEnd?.invoke()
                },
                onDragCancel = {
                    isDragging = 0 // Stop the velocity updates
                    onScrubEnd?.invoke()
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
