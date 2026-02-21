package com.recordermetronome.composable.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PauseIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.25f
        val barHeight = size.height
        val spacing = size.width * 0.2f

        // Left bar
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - 2 * barWidth - spacing) / 2f, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        // Right bar
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - 2 * barWidth - spacing) / 2f + barWidth + spacing, 0f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
fun StopButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        enabled = enabled
    ) {
        val tint = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

        Canvas(modifier = Modifier.size(24.dp)) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

@Composable
fun RecordButton(enabled: Boolean, onClick: () -> Unit) {
    val containerColor = if (enabled) {
        Color.Red
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        val tint = if (enabled) {
            Color.White
        }  else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

        Canvas(modifier = Modifier.size(40.dp)) {
            drawCircle(
                color = tint,
                radius = size.minDimension / 2f
            )
        }
    }
}

@Composable
fun PauseRecordButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        PauseIcon(
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

}

@Composable
fun PlayButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        enabled = enabled
    ) {
        val tint = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(32.dp),
            tint = tint
        )
    }
}

@Composable
fun PausePlaybackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp)
    ) {
        PauseIcon(
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}