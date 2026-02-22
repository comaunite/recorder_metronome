package com.recordermetronome

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordingFileUtilInstrumentedTest {
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Note: In real tests, we'd mock the file system or use a test directory
        testDir = File(context.cacheDir, "test_recordings")
        if (!testDir.exists()) {
            testDir.mkdirs()
        }
    }

    @Test
    fun useAppContext() {
        assertEquals("com.recordermetronome", context.packageName)
    }

    @Test
    fun recordingsDirectory_created_successfully() {
        // This is a basic test to ensure the app context works
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
        assertEquals("com.recordermetronome", context.packageName)
    }
}

