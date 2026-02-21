package com.recordermetronome

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
import androidx.compose.ui.Modifier
import com.recordermetronome.composable.FileExplorerScreen
import com.recordermetronome.composable.RecorderScreen
import com.recordermetronome.ui.theme.RecorderMetronomeTheme
import com.recordermetronome.view_models.FileExplorerViewModel
import com.recordermetronome.view_models.RecorderViewModel

class MainActivity : ComponentActivity() {
    private val recorderViewModel by viewModels<RecorderViewModel>()
    private val fileExplorerViewModel by viewModels<FileExplorerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RecorderMetronomeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentScreen = remember { mutableStateOf<Screen>(Screen.Recorder) }

                    when (currentScreen.value) {
                        Screen.Recorder -> {
                            RecorderScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = recorderViewModel,
                                onNavigateToFileExplorer = {
                                    currentScreen.value = Screen.FileExplorer
                                }
                            )
                        }

                        Screen.FileExplorer -> {
                            FileExplorerScreen(
                                modifier = Modifier.padding(innerPadding),
                                recorderViewModel = recorderViewModel,
                                fileExplorerViewModel = fileExplorerViewModel,
                                onNavigateBack = {
                                    currentScreen.value = Screen.Recorder
                                },
                                onStartRecording = {
                                    currentScreen.value = Screen.Recorder
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    enum class Screen {
        Recorder,
        FileExplorer
    }
}