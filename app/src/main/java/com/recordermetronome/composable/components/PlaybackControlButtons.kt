package com.recordermetronome.composable.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Shared button layout for Play/Pause, Record/Secondary, and Stop buttons
 * Used by both RecorderScreen and PlaybackScreen to maintain consistency
 */
@Composable
fun PlaybackControlButtons(
    modifier: Modifier = Modifier,
    leftButton: @Composable () -> Unit,
    centerButton: @Composable () -> Unit,
    rightButton: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leftButton()
        centerButton()
        rightButton()
    }
}

