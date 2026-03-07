package com.recorder.view_models

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileExplorerViewModelTest {

    @Test
    fun fileExplorerViewModel_initialState_recordingsIsEmpty() {
        val viewModel = FileExplorerViewModel()
        assertTrue(viewModel.recordings.value.isEmpty())
    }

    @Test
    fun fileExplorerViewModel_created_successfully() {
        val viewModel = FileExplorerViewModel()
        assertNotNull(viewModel)
        assertNotNull(viewModel.recordings)
    }

    @Test
    fun fileExplorerViewModel_recordingsStateFlow_isNotNull() {
        val viewModel = FileExplorerViewModel()
        assertNotNull(viewModel.recordings)
        assertEquals(0, viewModel.recordings.value.size)
    }

    @Test
    fun fileExplorerViewModel_multipleInstances_haveIndependentStates() {
        val viewModel1 = FileExplorerViewModel()
        val viewModel2 = FileExplorerViewModel()

        assertNotSame(viewModel1, viewModel2)
        assertNotNull(viewModel1.recordings)
        assertNotNull(viewModel2.recordings)
    }
}

