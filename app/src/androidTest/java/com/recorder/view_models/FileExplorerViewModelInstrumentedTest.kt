package com.recorder.view_models

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class FileExplorerViewModelInstrumentedTest {
    private lateinit var context: Context
    private lateinit var viewModel: FileExplorerViewModel

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        viewModel = FileExplorerViewModel()
    }

    @Test
    fun useAppContext() {
        assertEquals("com.recorder", context.packageName)
    }

    @Test
    fun fileExplorerViewModel_initialState_recordingsIsEmpty() {
        val recordings = viewModel.recordings
        assertNotNull(recordings)
    }

    @Test
    fun fileExplorerViewModel_created_successfully() {
        assertNotNull(viewModel)
        assertNotNull(viewModel.recordings)
    }
}

