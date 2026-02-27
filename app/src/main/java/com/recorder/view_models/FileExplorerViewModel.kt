package com.recorder.view_models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recorder.data.RecorderFile
import com.recorder.util.RecorderFileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileExplorerViewModel : ViewModel() {
    private val _recordings = MutableStateFlow<List<RecorderFile>>(emptyList())
    val recordings = _recordings.asStateFlow()

    fun loadRecordings(context: Context) {
        viewModelScope.launch {
            val recorderFiles = RecorderFileUtil.getRecorderFiles(context)
            _recordings.value = recorderFiles
        }
    }
}

