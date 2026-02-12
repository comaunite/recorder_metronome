import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.recordermetronome.RecorderViewModel
import com.recordermetronome.ui.theme.RecorderMetronomeTheme
import kotlin.getValue

class MainActivity : ComponentActivity() {

    // Logic for permissions must stay here or in a dedicated bridge
    // because it requires an Activity context to launch.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // If granted, we can trigger the start via the ViewModel
                viewModel.onStartRecording()
            }
        }

    private val viewModel by viewModels<RecorderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecorderMetronomeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecorderScreen(
                        isRecording = viewModel.isRecording,
                        onStartRequest = { handleStartRecording() },
                        onStopRequest = { viewModel.onStopRecording() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun handleStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            viewModel.onStartRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}