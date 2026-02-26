package com.recorder.composable.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun PauseButtonSmall(onClick: () -> Unit) {
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


@Composable
fun PauseButtonBig(onClick: () -> Unit) {
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
fun PlayButtonSmall(enabled: Boolean, onClick: () -> Unit) {
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
fun PlayButtonBig(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White,
            contentColor = Color.DarkGray,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        val tint = if (enabled) {
            Color.Black
        } else {
            Color.Black.copy(alpha = 0.38f)
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(70.dp),
            tint = tint
        )
    }
}

@Composable
fun RepeatToggleButtonSmall(isEnabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp)
    ) {
        val icon = if (isEnabled) Icons.Filled.RepeatOn else Icons.Filled.Repeat
        val contentDescription = if (isEnabled) "Repeat on" else "Repeat off"
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PlaybackSpeedButton(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = "${currentSpeed}x",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${speed}x") },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}
