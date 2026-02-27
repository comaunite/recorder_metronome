package com.recorder.composable.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.recorder.util.FormattingHelper

@Composable
fun TimestampDisplay(
    modifier: Modifier = Modifier,
    timestampMs: Long,
    durationMs: Long? = null
) {
    val formattedTimestamp = remember(timestampMs) {
        FormattingHelper.formatDurationWithMs(timestampMs)
    }
    val formattedDuration = remember(durationMs) {
        durationMs?.let { FormattingHelper.formatDuration(it) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.displayLarge
            )
        }

        if (formattedDuration != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
