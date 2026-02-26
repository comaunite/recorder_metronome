package com.recorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.recorder.composable.FileExplorerScreen
import com.recorder.composable.PlaybackScreen
import com.recorder.composable.RecorderScreen
import com.recorder.data.RecordingFile
import com.recorder.ui.theme.RecorderTheme
import com.recorder.view_models.FileExplorerViewModel
import com.recorder.view_models.PlaybackViewModel
import com.recorder.view_models.RecorderViewModel

class MainActivity : ComponentActivity() {
    private val recorderViewModel by viewModels<RecorderViewModel>()
    private val fileExplorerViewModel by viewModels<FileExplorerViewModel>()
    private val playbackViewModel by viewModels<PlaybackViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RecorderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentScreen = remember { mutableStateOf<Screen>(Screen.FileExplorer) }
                    val selectedRecording = remember { mutableStateOf<RecordingFile?>(null) }
                    val preLoadedRecordings by fileExplorerViewModel.recordings.collectAsStateWithLifecycle()

                    when (currentScreen.value) {
                        Screen.FileExplorer -> {
                            FileExplorerScreen(
                                modifier = Modifier.padding(innerPadding),
                                recorderViewModel = recorderViewModel,
                                fileExplorerViewModel = fileExplorerViewModel,
                                onStartRecording = {
                                    currentScreen.value = Screen.Recorder
                                },
                                onPlayRecording = { recording ->
                                    selectedRecording.value = recording
                                    currentScreen.value = Screen.Playback
                                }
                            )
                        }

                        Screen.Recorder -> {
                            RecorderScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = recorderViewModel,
                                onNavigateBack = {
                                    currentScreen.value = Screen.FileExplorer
                                },
                                preLoadedRecordings = preLoadedRecordings
                            )
                        }

                        Screen.Playback -> {
                            selectedRecording.value?.let { recording ->
                                PlaybackScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    viewModel = playbackViewModel,
                                    fileExplorerViewModel = fileExplorerViewModel,
                                    recordingFile = recording,
                                    onNavigateBack = {
                                        currentScreen.value = Screen.FileExplorer
                                    },
                                    preLoadedRecordings = preLoadedRecordings
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    enum class Screen {
        Recorder,
        FileExplorer,
        Playback
    }
}