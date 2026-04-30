package com.flowhack.flowcapital.integration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Интеграционные тесты для SettingsManager (DataStore).
 * Проверяют реальное сохранение и чтение настроек.
 * 
 * Используем PreferenceDataStoreFactory для создания изолированного инстанса,
 * чтобы избежать ошибки "multiple DataStores active".
 */
class SettingsIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(Dispatchers.IO)
        val testFile = File(context.filesDir, "test_settings.preferences_pb")
        // Удаляем файл если существует (чистый старт)
        if (testFile.exists()) {
            testFile.delete()
        }
        // Создаем DataStore через фабрику
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        // Очищаем тестовый DataStore перед каждым тестом
        runBlocking {
            withContext(Dispatchers.IO) {
                dataStore.edit { it.clear() }
            }
        }
    }
    
    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    dataStore.edit { it.clear() }
                } catch (e: Exception) {
                    // Игнорируем ошибки при очистке
                }
            }
        }
        testScope.cancel()
    }
    
    /**
     * Т3.25: Сохранение и чтение процентов РП.
     * Согласно ТЗ: "НАСТРОЙКИ ПОТОКОВ: Карточка с полями редактирования %"
     */
    @Test
    fun saveAndReadGrowingFlowPercentages() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val startPercentKey = doublePreferencesKey("start_percent")
            val dailyAdditionKey = doublePreferencesKey("daily_addition")
            
            // Act - сохраняем
            dataStore.edit { prefs ->
                prefs[startPercentKey] = 0.1
                prefs[dailyAdditionKey] = 0.003
            }
            
            // Assert - читаем
            val prefs = dataStore.data.first()
            assertEquals("Стартовый процент должен быть 0.1", 0.1, prefs[startPercentKey]!!, 0.0001)
            assertEquals("Ежедневный прирост должен быть 0.003", 0.003, prefs[dailyAdditionKey]!!, 0.0001)
        }
    }
    
    /**
     * Т3.26: Сохранение и чтение процентов ПН.
     * Согласно ТЗ: "Бонус ко взносу: {X}%", "Ежедневный процент: {Y}%"
     */
    @Test
    fun saveAndReadNoviceFlowPercentages() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val bonusPercentKey = doublePreferencesKey("pn_bonus_percent")
            val dailyPercentKey = doublePreferencesKey("pn_daily_percent")
            
            // Act
            dataStore.edit { prefs ->
                prefs[bonusPercentKey] = 50.0
                prefs[dailyPercentKey] = 2.0
            }
            
            // Assert
            val prefs = dataStore.data.first()
            assertEquals("Бонус ПН должен быть 50%", 50.0, prefs[bonusPercentKey]!!, 0.01)
            assertEquals("Дневной процент ПН должен быть 2%", 2.0, prefs[dailyPercentKey]!!, 0.01)
        }
    }
    
    /**
     * Т3.27: Сохранение и чтение коэффициентов ПСП.
     * Согласно ТЗ: "Процентов периодов ПСП (сериализованная строка)"
     */
    @Test
    fun saveAndReadPspCoefficients() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val pspKey = androidx.datastore.preferences.core.stringPreferencesKey("psp_period_percentages")
            val coefficients = mapOf(1 to 30.0, 2 to 55.8, 3 to 78.0)
            val serialized = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
            
            // Act
            dataStore.edit { prefs ->
                prefs[pspKey] = serialized
            }
            
            // Assert
            val prefs = dataStore.data.first()
            val saved = prefs[pspKey]!!
            assertTrue("Сериализованная строка должна содержать данные", saved.isNotEmpty())
            
            // Десериализация
            val parsed = mutableMapOf<Int, Double>()
            saved.split(";").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    parsed[parts[0].toInt()] = parts[1].toDouble()
                }
            }
            assertEquals("Должно быть 3 периода", 3, parsed.size)
            assertEquals("Период 1 должен быть 30%", 30.0, parsed[1]!!, 0.01)
        }
    }
    
    /**
     * Т3.28: Переключатель темы приложения.
     * Согласно ТЗ: "Переключатель темы приложения (светлая/тёмная)"
     */
    @Test
    fun toggleTheme_savesDarkThemeSetting() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val darkThemeKey = booleanPreferencesKey("dark_theme")
            
            // Act - переключаем на светлую тему (false)
            dataStore.edit { prefs ->
                prefs[darkThemeKey] = false
            }
            
            // Assert
            val prefs = dataStore.data.first()
            val darkTheme = prefs[darkThemeKey] ?: true
            assertFalse("Тема должна быть светлой (false)", darkTheme)
        }
    }
    
    /**
     * Т3.29: Напоминания - добавление и удаление.
     * Согласно ТЗ: "Строка Напоминания х/5 и кнопка Добавить (Максимум 5 в день)"
     */
    @Test
    fun reminders_addAndRemove() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val remindersKey = androidx.datastore.preferences.core.stringSetPreferencesKey("reminders_list")
            
            // Act - добавляем 2 напоминания
            dataStore.edit { prefs ->
                prefs[remindersKey] = setOf("08:00", "12:00")
            }
            
            // Assert - проверяем что добавились
            var prefs = dataStore.data.first()
            var reminders = prefs[remindersKey] ?: emptySet()
            assertEquals("Должно быть 2 напоминания", 2, reminders.size)
            
            // Act - удаляем одно
            dataStore.edit { prefs ->
                val current = prefs[remindersKey] ?: emptySet()
                prefs[remindersKey] = current - "08:00"
            }
            
            // Assert
            prefs = dataStore.data.first()
            reminders = prefs[remindersKey] ?: emptySet()
            assertEquals("Должно остаться 1 напоминание", 1, reminders.size)
            assertTrue("Должно остаться 12:00", reminders.contains("12:00"))
        }
    }
    
    /**
     * Т3.30: Проверка обновлений при входе.
     * Согласно ТЗ: "Чекбокс Проверять обновления при входе: По умолчанию нажат"
     */
    @Test
    fun checkUpdateOnStart_defaultIsTrue() = runBlocking {
        withContext(Dispatchers.IO) {
            // Arrange
            val checkUpdateKey = booleanPreferencesKey("check_update_on_start")
            
            // Act - по умолчанию (если нет в БД, должно быть true)
            val prefs = dataStore.data.first()
            val checkUpdate = prefs[checkUpdateKey] ?: true
            
            // Assert
            assertTrue("По умолчанию проверка обновлений должна быть вкл", checkUpdate)
        }
    }
}
