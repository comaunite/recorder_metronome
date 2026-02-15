package com.recordermetronome.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.recordermetronome.WaveformData
import kotlin.math.abs

@Composable
fun WaveformVisualizer(
    waveformData: WaveformData,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        drawRect(color = backgroundColor)

        val amplitudes = waveformData.amplitudes
        println("VISUALIZER: Rendering ${amplitudes.size} amplitudes")
        if (amplitudes.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val maxAmplitude = waveformData.maxAmplitude.coerceAtLeast(1f)
        val barWidth = (width / amplitudes.size.coerceAtLeast(1)) * 0.6f // Make bars thinner (60% of available space)
        val barSpacing = (width / amplitudes.size.coerceAtLeast(1)) * 0.4f // 40% spacing

        // Draw center line
        drawLine(
            color = waveColor.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1f
        )

        // Draw amplitude bars
        amplitudes.forEachIndexed { index, amplitude ->
            val normalized = (abs(amplitude) / maxAmplitude).coerceIn(0f, 1f)
            val barHeight = (height / 2) * normalized
            val xPosition = index * (barWidth + barSpacing)
            drawRect(
                color = waveColor,
                topLeft = Offset(
                    xPosition,
                    centerY - barHeight / 2
                ),
                size = Size(
                    barWidth.coerceAtLeast(1f),
                    barHeight.coerceAtLeast(1f)
                )
            )
        }
    }
}
