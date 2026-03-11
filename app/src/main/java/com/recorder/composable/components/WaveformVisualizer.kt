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
import androidx.compose.runtime.mutableStateOf
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
import kotlin.math.sign
import kotlin.math.sqrt

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

    // Scrubbing parameters
    val distanceToReachMaxSpeed = 80
    val maxBarsPerSecond = 400f
    // Scrubbing state
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    // Fractional accumulator so slow speeds don't get lost between frames
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    // Local tracked position — updated every tick so the loop always advances
    // from where we last seeked, not from the stale captured waveformData.currentPosition
    var localSeekPosition by remember { mutableIntStateOf(0) }

    // Keep localSeekPosition in sync with external position changes
    // (e.g. normal playback advancing, or initial load) but NOT while we're dragging
    if (!isDragging) {
        localSeekPosition = waveformData.currentPosition
    }

    // Velocity-based seek loop: runs as long as the finger is held down
    LaunchedEffect(isDragging) {
        if (isDragging) {
            // Snapshot the current position when the drag starts
            localSeekPosition = waveformData.currentPosition
            seekAccumulator = 0f
            while (isActive && isDragging) {
                val totalBars = waveformData.amplitudes.size
                if (totalBars > 0 && barFullWidthPx > 0f) {
                    val deadZonePx = with(density) { 8.dp.toPx() }
                    val maxSpeedPx = with(density) { distanceToReachMaxSpeed.dp.toPx() }

                    val effectiveOffset = when {
                        abs(dragOffsetPx) < deadZonePx -> 0f
                        else -> (dragOffsetPx - sign(dragOffsetPx) * deadZonePx) / maxSpeedPx
                    }.coerceIn(-1f, 1f)

                    val barsPerSecond = effectiveOffset * abs(effectiveOffset) * maxBarsPerSecond

                    // Accumulate fractional bars over the 16 ms frame
                    seekAccumulator += barsPerSecond * (16 / 1000f)
                    val barsToMove = seekAccumulator.toInt()
                    if (barsToMove != 0) {
                        seekAccumulator -= barsToMove
                        val targetIndex = (localSeekPosition + barsToMove).coerceIn(0, totalBars - 1)
                        localSeekPosition = targetIndex
                        onScrubPosition?.invoke(targetIndex)
                    }
                }
                delay(16L) // ~60 fps
            }
        }
    }

    val scrubModifier = if (enableScrubbing && onScrubPosition != null) {
        Modifier.pointerInput(enableScrubbing) {
            detectDragGestures(
                onDragStart = { _ ->
                    dragOffsetPx = 0f
                    seekAccumulator = 0f
                    isDragging = true
                    onScrubStart?.invoke()
                },
                onDrag = { change, dragAmount ->
                    dragOffsetPx += dragAmount.x
                    change.consume()
                },
                onDragEnd = {
                    isDragging = false
                    dragOffsetPx = 0f
                    onScrubEnd?.invoke()
                },
                onDragCancel = {
                    isDragging = false
                    dragOffsetPx = 0f
                    onScrubEnd?.invoke()
                }
            )
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier.then(scrubModifier)
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

            val barsToLeft = (centerX / barFullWidth).toInt()
            val barsToRight = ((width - centerX) / barFullWidth).toInt()

            val startIndex = (currentPosition - barsToLeft).coerceAtLeast(0)
            val endIndex = (currentPosition + barsToRight).coerceAtMost(amplitudes.size - 1)

            if (startIndex <= endIndex) {
                for (i in startIndex..endIndex) {
                    val amplitude = amplitudes[i]
                    val normalized = (abs(amplitude) / maxAmplitude).coerceIn(0f, 1f)
                    // Square-root curve: boosts quiet parts significantly without clipping loud ones.
                    // e.g. 4% amplitude → 20% bar height instead of 4%.
                    val scaledNormalized = sqrt(normalized)
                    val barHeight = (height / 2) * scaledNormalized

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
