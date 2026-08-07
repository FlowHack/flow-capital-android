package com.flowhack.flowcapital.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Юнит-тесты менеджера настроек [SettingsManager].
 *
 * Тестируют реальную логику сохранения и чтения настроек через DataStore
 * на временном файле (без Android-зависимостей).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.Job())
        ) {
            File(tempFolder.root, "test_settings.preferences_pb")
        }
        settingsManager = SettingsManager(dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savePercentages_writesStartAndDailyPercent() = runTest(testDispatcher) {
        settingsManager.savePercentages(0.15, 0.005)

        assertEquals(0.15, settingsManager.startPercentFlow.first(), 0.0001)
        assertEquals(0.005, settingsManager.dailyAdditionFlow.first(), 0.0001)
    }

    @Test
    fun savePnPercentages_writesBonusAndDailyPercent() = runTest(testDispatcher) {
        settingsManager.savePnPercentages(60.0, 2.5)

        assertEquals(60.0, settingsManager.pnBonusPercentFlow.first(), 0.0001)
        assertEquals(2.5, settingsManager.pnDailyPercentFlow.first(), 0.0001)
    }

    @Test
    fun addReminder_addsTimeToReminders() = runTest(testDispatcher) {
        settingsManager.addReminder("09:00")

        assertTrue(settingsManager.remindersFlow.first().contains("09:00"))
    }

    @Test
    fun addReminder_limitsToFiveEntries() = runTest(testDispatcher) {
        repeat(5) { index ->
            settingsManager.addReminder("0$index:00")
        }
        // Шестое напоминание не должно добавиться из-за лимита 5.
        settingsManager.addReminder("09:00")

        val reminders = settingsManager.remindersFlow.first()
        assertEquals(5, reminders.size)
        assertFalse(reminders.contains("09:00"))
    }

    @Test
    fun removeReminder_deletesFromRemindersAndAlarms() = runTest(testDispatcher) {
        settingsManager.addReminder("09:00")
        settingsManager.updateAlarmModeForReminder("09:00", true)

        settingsManager.removeReminder("09:00")

        assertFalse(settingsManager.remindersFlow.first().contains("09:00"))
        assertFalse(settingsManager.alarmRemindersFlow.first().contains("09:00"))
    }

    @Test
    fun addPspReminder_limitsToFiveEntries() = runTest(testDispatcher) {
        repeat(5) { index ->
            settingsManager.addPspReminder("0$index:00")
        }
        settingsManager.addPspReminder("09:00")

        val reminders = settingsManager.pspRemindersFlow.first()
        assertEquals(5, reminders.size)
        assertFalse(reminders.contains("09:00"))
    }

    @Test
    fun setRpVip_enablesVipModeAndUpdatesCoefficients() = runTest(testDispatcher) {
        settingsManager.setRpVip(true)

        assertTrue(settingsManager.isRpVipFlow.first())
        assertEquals(0.3, settingsManager.startPercentFlow.first(), 0.0001)
        // VIP-коэффициенты E-currency должны быть загружены в кэш.
        val coefficients = settingsManager.eCurrencyCoefficientsFlow.first()
        assertTrue(coefficients.isNotEmpty())
        // Порог 100 должен присутствовать в VIP-наборе.
        assertTrue(coefficients.containsKey(100.0))
    }

    @Test
    fun setRpVip_disablesVipModeAndRestoresDefaults() = runTest(testDispatcher) {
        settingsManager.setRpVip(true)
        settingsManager.setRpVip(false)

        assertFalse(settingsManager.isRpVipFlow.first())
        assertEquals(0.1, settingsManager.startPercentFlow.first(), 0.0001)
        // Стандартный набор коэффициентов не содержит порога 100.
        val coefficients = settingsManager.eCurrencyCoefficientsFlow.first()
        assertFalse(coefficients.containsKey(100.0))
    }

    @Test
    fun initializeDefaults_setsDefaultValuesForMissingKeys() = runTest(testDispatcher) {
        settingsManager.initializeDefaults()

        assertTrue(settingsManager.checkUpdateOnStartFlow.first())
        assertTrue(settingsManager.darkThemeFlow.first())
        assertEquals(1, settingsManager.defaultEntryTabFlow.first())
        assertEquals(3, settingsManager.defaultCalcTabFlow.first())
        // Коэффициенты ПСП должны быть загружены.
        assertTrue(settingsManager.pspCoefficientsFlow.first().isNotEmpty())
    }

    @Test
    fun getECurrencyBonusPercent_returnsBonusForThreshold() = runTest(testDispatcher) {
        settingsManager.initializeDefaults()

        // Порог 100000 -> 150%.
        assertEquals(150.0, settingsManager.getECurrencyBonusPercent(100_000.0), 0.0001)
        // Порог 1000 -> 50%.
        assertEquals(50.0, settingsManager.getECurrencyBonusPercent(1_000.0), 0.0001)
        // Сумма ниже минимального порога -> 0.
        assertEquals(0.0, settingsManager.getECurrencyBonusPercent(500.0), 0.0001)
    }

    @Test
    fun setDarkTheme_savesDarkThemeSetting() = runTest(testDispatcher) {
        settingsManager.setDarkTheme(false)

        assertFalse(settingsManager.darkThemeFlow.first())
    }

    @Test
    fun setCheckUpdateOnStart_savesSetting() = runTest(testDispatcher) {
        settingsManager.setCheckUpdateOnStart(false)

        assertFalse(settingsManager.checkUpdateOnStartFlow.first())
    }

    @Test
    fun setSmartNotifications_savesSetting() = runTest(testDispatcher) {
        settingsManager.setSmartNotifications(true)

        assertTrue(settingsManager.getSmartNotifications())
    }

    @Test
    fun savePspCoefficients_updatesFlowAndPersists() = runTest(testDispatcher) {
        val coefficients = mapOf(1 to 30.0, 2 to 55.8, 3 to 78.0)
        settingsManager.savePspCoefficients(coefficients)

        val saved = settingsManager.pspCoefficientsFlow.first()
        assertEquals(30.0, saved[1] ?: 0.0, 0.0001)
        assertEquals(55.8, saved[2] ?: 0.0, 0.0001)
        assertEquals(78.0, saved[3] ?: 0.0, 0.0001)
    }

    @Test
    fun saveBrowserFabOffset_savesOffsets() = runTest(testDispatcher) {
        settingsManager.saveBrowserFabOffset(250, 30)

        assertEquals(250, settingsManager.browserFabOffsetXFlow.first())
        assertEquals(30, settingsManager.browserFabOffsetYFlow.first())
    }
}
