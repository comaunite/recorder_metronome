import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecorderScreen(
    isRecording: Boolean,
    onStartRequest: () -> Unit,
    onStopRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isRecording) {
            Text(text = "Recording in progress...")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = if (isRecording) onStopRequest else onStartRequest) {
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }
    }
}