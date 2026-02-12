package com.recordermetronome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.recordermetronome.composable.RecorderScreen
import com.recordermetronome.ui.theme.RecorderMetronomeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RecorderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RecorderMetronomeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecorderScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}