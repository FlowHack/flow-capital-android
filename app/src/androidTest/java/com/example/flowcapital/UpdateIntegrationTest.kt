package com.example.flowcapital

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты для логики обновлений.
 * Проверяют взаимодействие между UpdateChecker и SettingsManager.
 */
@RunWith(AndroidJUnit4::class)
class UpdateIntegrationTest {

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun `check update on start is enabled by default`() = runBlocking {
        settingsManager.initializeDefaults()
        val checkOnStart = settingsManager.checkUpdateOnStartFlow.first()
        assertTrue(checkOnStart)
    }

    @Test
    fun `skip auto update can be toggled`() = runBlocking {
        settingsManager.initializeDefaults()
        
        settingsManager.setSkipAutoUpdate(true)
        val skipAuto = settingsManager.skipAutoUpdateFlow.first()
        assertTrue(skipAuto)

        settingsManager.setSkipAutoUpdate(false)
        val skipAutoAfter = settingsManager.skipAutoUpdateFlow.first()
        assertFalse(skipAutoAfter)
    }

    @Test
    fun `skipped version can be set and cleared`() = runBlocking {
        settingsManager.initializeDefaults()
        
        settingsManager.setSkippedVersion("1.3.1")
        val skipped = settingsManager.skippedVersionFlow.first()
        assertEquals("1.3.1", skipped)

        settingsManager.setSkippedVersion(null)
        val cleared = settingsManager.skippedVersionFlow.first()
        assertNull(cleared)
    }

    @Test
    fun `skipped version is stored correctly`() = runBlocking {
        settingsManager.initializeDefaults()
        settingsManager.setSkippedVersion("1.3.1")
        
        val skipped = settingsManager.skippedVersionFlow.first()
        assertEquals("1.3.1", skipped)
    }

    @Test
    fun `version check settings are independent`() = runBlocking {
        settingsManager.initializeDefaults()
        
        settingsManager.setCheckUpdateOnStart(false)
        val checkOnStart = settingsManager.checkUpdateOnStartFlow.first()
        assertFalse(checkOnStart)

        settingsManager.setSkipAutoUpdate(false)
        val skipAuto = settingsManager.skipAutoUpdateFlow.first()
        assertFalse(skipAuto)
    }
}