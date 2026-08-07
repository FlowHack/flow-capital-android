package com.flowhack.flowcapital.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Расширение контекста для создания DataStore.
 */
val Context.dataStore by preferencesDataStore(name = "user_settings")

/**
 * Менеджер настроек приложения.
 * Управляет сохранением и чтением пользовательских настроек через DataStore.
 *
 * @property context Контекст приложения
 */
class SettingsManager(context: Context) {
    private val dataStore = context.dataStore
    private var initialized = false

    companion object {
        /** Ключ для стартового процента РП */
        val START_PERCENT = doublePreferencesKey("start_percent")
        /** Ключ для ежедневного прироста процента РП */
        val DAILY_ADDITION = doublePreferencesKey("daily_addition")
        /** Ключ для вкладки по умолчанию */
        val DEFAULT_TAB = intPreferencesKey("default_tab")
        /** Ключ для вкладки расчётов по умолчанию */
        val DEFAULT_CALC_TAB = intPreferencesKey("default_calc_tab")
        /** Ключ для вкладки при входе по умолчанию (0=Браузер, 1=Расчёты, 2=Настройки) */
        val DEFAULT_ENTRY_TAB = intPreferencesKey("default_entry_tab")
        /** Ключ для списка напоминаний для РП/ПН */
        val REMINDERS_KEY = stringSetPreferencesKey("reminders_list")
        /** Ключ для списка будильников (тайм-теги напоминаний в режиме будильника) */
        val ALARM_REMINDERS_KEY = stringSetPreferencesKey("alarm_reminders_list")
        /** Ключ для бонусного процента ПН */
        val PN_BONUS_PERCENT = doublePreferencesKey("pn_bonus_percent")
        /** Ключ для дневного процента ПН */
        val PN_DAILY_PERCENT = doublePreferencesKey("pn_daily_percent")
        /** Ключ для списка напоминаний для ПСП */
        val PSP_REMINDERS_KEY = stringSetPreferencesKey("psp_reminders_list")
        /** Ключ для процентов периодов ПСП (сериализованная строка) */
        val PSP_PERIOD_PERCENTAGES = stringPreferencesKey("psp_period_percentages")
                /** Ключ для флага РП VIP */
        val IS_RP_VIP = booleanPreferencesKey("is_rp_vip")
        /** Ключ для коэффициентов E-currency РП */
        val E_CURRENCY_COEFFICIENTS = stringPreferencesKey("e_currency_coefficients")
        /** Ключ для пропуска автопроверки обновлений */
        val SKIP_AUTO_UPDATE = booleanPreferencesKey("skip_auto_update")
        /** Ключ для пропущенной версии (чтобы не показывать повторно) */
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
/** Ключ для проверки обновлений при входе */
val CHECK_UPDATE_ON_START = booleanPreferencesKey("check_update_on_start")

/** Ключ для умных уведомлений (учёт времени клика по кнопке) */
val SMART_NOTIFICATIONS = booleanPreferencesKey("smart_notifications")

/** Ключ для темной темы (true = тёмная, false = светлая) */
val DARK_THEME = booleanPreferencesKey("dark_theme")

/** Ключ для смещения кнопки обновления браузера по X (dp) */
val BROWSER_FAB_OFFSET_X = intPreferencesKey("browser_fab_offset_x")
/** Ключ для смещения кнопки обновления браузера по Y (dp) */
val BROWSER_FAB_OFFSET_Y = intPreferencesKey("browser_fab_offset_y")

        private val DEFAULT_PSP_COEFFICIENTS = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
            5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
            9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
            13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
            17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )

        private val DEFAULT_E_CURRENCY_COEFFICIENTS = mapOf(
            1000.0 to 50.0,
            5000.0 to 75.0,
            10000.0 to 100.0,
            50000.0 to 125.0,
            100000.0 to 150.0,
            500000.0 to 175.0,
            1000000.0 to 200.0
        )

        /** Дефолтные коэффициенты для РП VIP (стартовый 0.3%, daily 0.003%) */
        val VIP_START_PERCENT = 0.3
        val VIP_DAILY_ADDITION = 0.003
        val VIP_E_CURRENCY_COEFFICIENTS = mapOf(
            100.0 to 30.0,
            500.0 to 40.0,
            1000.0 to 50.0,
            2500.0 to 60.0,
            5000.0 to 70.0,
            10000.0 to 100.0,
            25000.0 to 110.0,
            50000.0 to 130.0,
            100000.0 to 150.0,
            250000.0 to 160.0,
            500000.0 to 170.0,
            1000000.0 to 180.0
        )
    }

    /**
     * Поток со стартовым процентом РП.
     * По умолчанию 0.1%.
     */
    val startPercentFlow: Flow<Double> = dataStore.data.map { it[START_PERCENT] ?: 0.1 }

    /**
     * Поток с ежедневным приростом процента РП.
     * По умолчанию 0.003%.
     */
    val dailyAdditionFlow: Flow<Double> = dataStore.data.map { it[DAILY_ADDITION] ?: 0.003 }

    /**
     * Поток с флагом РП VIP.
     * true — VIP-режим (стартовый 0.3%, daily 0.003%).
     * false — обычный РП.
     */
    val isRpVipFlow: Flow<Boolean> = dataStore.data.map { it[IS_RP_VIP] ?: false }

    /**
     * Поток с бонусным процентом при взносе в ПН.
     * По умолчанию 50%.
     */
    val pnBonusPercentFlow: Flow<Double> = dataStore.data.map { it[PN_BONUS_PERCENT] ?: 50.0 }

    /**
     * Поток с дневным процентом начислений ПН.
     * По умолчанию 2%.
     */
    val pnDailyPercentFlow: Flow<Double> = dataStore.data.map { it[PN_DAILY_PERCENT] ?: 2.0 }

    /**
     * Поток с индексом вкладки расчётов по умолчанию.
     * По умолчанию 3 (РП).
     */
    val defaultCalcTabFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_CALC_TAB] ?: 3 }

    /**
     * Поток с индексом вкладки при входе в приложение.
     * 0 = Браузер, 1 = Расчёты, 2 = Настройки
     * По умолчанию 1 (Расчёты).
     */
    val defaultEntryTabFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_ENTRY_TAB] ?: 1 }

    /**
     * Поток со списком напоминаний для РП/ПН.
     * Каждое напоминание - строка формата "ЧЧ:ММ".
     */
    val remindersFlow: Flow<Set<String>> = dataStore.data.map { it[REMINDERS_KEY] ?: emptySet() }

    /**
     * Поток со списком напоминаний для ПСП.
     * Отдельный от напоминаний РП/ПН.
     */
    val pspRemindersFlow: Flow<Set<String>> = dataStore.data.map { it[PSP_REMINDERS_KEY] ?: emptySet() }

    /**
     * Поток со списком будильников (тайм-теги напоминаний в режиме будильника).
     * Если данных нет — возвращает пустой Set.
     */
    val alarmRemindersFlow: Flow<Set<String>> = dataStore.data.map { it[ALARM_REMINDERS_KEY] ?: emptySet() }

    /**
     * Поток для запомненной пропущенной версии.
     */
    val skippedVersionFlow: Flow<String?> = dataStore.data.map { it[SKIPPED_VERSION] }

    /**
     * Установить режим РП VIP и записать дефолтные коэффициенты.
     * @param vip true — VIP (0.3%, 0.003%), false — обычный РП (0.1%, 0.003%)
     */
    suspend fun setRpVip(vip: Boolean) {
        dataStore.edit { prefs ->
            prefs[IS_RP_VIP] = vip
            prefs[START_PERCENT] = if (vip) VIP_START_PERCENT else 0.1
            prefs[DAILY_ADDITION] = if (vip) VIP_DAILY_ADDITION else 0.003
            val coefficients = if (vip) VIP_E_CURRENCY_COEFFICIENTS else DEFAULT_E_CURRENCY_COEFFICIENTS
            val entries = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
            prefs[E_CURRENCY_COEFFICIENTS] = entries
            _eCurrencyCoefficientsFlow.value = coefficients
            cachedECurrencyCoefficients = coefficients
        }
    }

    /**
     * Сохранить проценты для РП.
     * @param start Стартовый процент
     * @param daily Ежедневный прирост процента
     */
    suspend fun savePercentages(start: Double, daily: Double) {
        dataStore.edit { prefs ->
            prefs[START_PERCENT] = start
            prefs[DAILY_ADDITION] = daily
        }
    }

    /**
     * Сохранить проценты для ПН.
     * @param bonus Бонусный процент при взносе
     * @param daily Дневной процент начислений
     */
    suspend fun savePnPercentages(bonus: Double, daily: Double) {
        dataStore.edit { prefs ->
            prefs[PN_BONUS_PERCENT] = bonus
            prefs[PN_DAILY_PERCENT] = daily
        }
    }

    /**
     * Установить вкладку расчётов по умолчанию.
     * @param index Индекс вкладки (0-4)
     */
    suspend fun setDefaultCalcTab(index: Int) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_CALC_TAB] = index
        }
    }

    /**
     * Установить вкладку при входе в приложение.
     * @param index 0=Браузер, 1=Расчёты, 2=Настройки
     */
    suspend fun setDefaultEntryTab(index: Int) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_ENTRY_TAB] = index
        }
    }

    /**
     * Добавить новое напоминание.
     * Максимум 5 напоминаний.
     * @param time Время напоминания в формате "ЧЧ:ММ"
     */
    suspend fun addReminder(time: String) {
        dataStore.edit { prefs ->
            val current = prefs[REMINDERS_KEY] ?: emptySet()
            if (current.size < 5) {
                prefs[REMINDERS_KEY] = current + time
            }
        }
    }

    /**
     * Удалить напоминание.
     * @param time Время напоминания для удаления
     */
    suspend fun removeReminder(time: String) {
        dataStore.edit { prefs ->
            val current = prefs[REMINDERS_KEY] ?: emptySet()
            prefs[REMINDERS_KEY] = current - time
            val alarmCurrent = prefs[ALARM_REMINDERS_KEY] ?: emptySet()
            prefs[ALARM_REMINDERS_KEY] = alarmCurrent - time
        }
    }

    /**
     * Добавить новое напоминание для ПСП.
     * Максимум 5 напоминаний.
     * @param time Время напоминания в формате "ЧЧ:ММ"
     */
    suspend fun addPspReminder(time: String) {
        dataStore.edit { prefs ->
            val current = prefs[PSP_REMINDERS_KEY] ?: emptySet()
            if (current.size < 5) {
                prefs[PSP_REMINDERS_KEY] = current + time
            }
        }
    }

    /**
     * Удалить напоминание для ПСП.
     * @param time Время напоминания для удаления
     */
    suspend fun removePspReminder(time: String) {
        dataStore.edit { prefs ->
            val current = prefs[PSP_REMINDERS_KEY] ?: emptySet()
            prefs[PSP_REMINDERS_KEY] = current - time
        }
    }

    /**
     * Переключить режим будильника для указанного напоминания.
     * @param timeTag Тайм-тег в формате "ЧЧ:ММ"
     * @param isAlarm true — добавить в будильники, false — убрать из будильников
     */
    suspend fun updateAlarmModeForReminder(timeTag: String, isAlarm: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[ALARM_REMINDERS_KEY] ?: emptySet()
            prefs[ALARM_REMINDERS_KEY] = if (isAlarm) current + timeTag else current - timeTag
        }
    }

    /** Перезаписать весь список напоминаний РП/ПН (для импорта). */
    suspend fun saveReminders(reminders: Set<String>) {
        dataStore.edit { prefs -> prefs[REMINDERS_KEY] = reminders }
    }

    /** Перезаписать весь список напоминаний ПСП (для импорта). */
    suspend fun savePspReminders(reminders: Set<String>) {
        dataStore.edit { prefs -> prefs[PSP_REMINDERS_KEY] = reminders }
    }

    /** Перезаписать весь список будильников (для импорта). */
    suspend fun saveAlarmReminders(reminders: Set<String>) {
        dataStore.edit { prefs -> prefs[ALARM_REMINDERS_KEY] = reminders }
    }

    private var cachedPspCoefficients: Map<Int, Double> = DEFAULT_PSP_COEFFICIENTS

    /**
     * Инициализировать кеш коэффициентов ПСП.
     * Вызывать при старте приложения.
     */
    fun initializePspCache(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            dataStore.data.collect { preferences ->
                val serialized = preferences[PSP_PERIOD_PERCENTAGES]
                if (!serialized.isNullOrEmpty()) {
                    val parsed = mutableMapOf<Int, Double>()
                    serialized.split(";").forEach { entry ->
                        val parts = entry.split("=")
                        if (parts.size == 2) {
                            val key = parts[0].toIntOrNull()
                            val value = parts[1].toDoubleOrNull()
                            if (key != null && value != null) {
                                parsed[key] = value
                            }
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        cachedPspCoefficients = parsed
                    }
                }
            }
        }
    }

    /**
     * Принудительно обновить кэш коэффициентов ПСП из DataStore.
     * Вызывать из корутины!
     */
    suspend fun refreshPspCacheSync() {
        dataStore.data.first().let { preferences ->
            val serialized = preferences[PSP_PERIOD_PERCENTAGES]
            if (!serialized.isNullOrEmpty()) {
                val parsed = mutableMapOf<Int, Double>()
                serialized.split(";").forEach { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2) {
                        val key = parts[0].toIntOrNull()
                        val value = parts[1].toDoubleOrNull()
                        if (key != null && value != null) {
                            parsed[key] = value
                        }
                    }
                }
                if (parsed.isNotEmpty()) {
                    cachedPspCoefficients = parsed
                    _pspCoefficientsFlow.value = parsed
                }
            }
        }
    }

    /**
     * MutableStateFlow для коэффициентов ПСП - обновляется сразу после сохранения.
     */
    private val _pspCoefficientsFlow = MutableStateFlow(DEFAULT_PSP_COEFFICIENTS)

    /**
     * Поток с коэффициентами периодов ПСП для Compose.
     */
    val pspCoefficientsFlow: StateFlow<Map<Int, Double>> = _pspCoefficientsFlow.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            dataStore.data.first().let { preferences ->
                val serialized = preferences[PSP_PERIOD_PERCENTAGES]
                val parsed = mutableMapOf<Int, Double>()
                if (!serialized.isNullOrEmpty()) {
                    serialized.split(";").forEach { entry ->
                        val parts = entry.split("=")
                        if (parts.size == 2) {
                            val key = parts[0].toIntOrNull()
                            val value = parts[1].toDoubleOrNull()
                            if (key != null && value != null) {
                                parsed[key] = value
                            }
                        }
                    }
                }
                if (parsed.isNotEmpty()) {
                    _pspCoefficientsFlow.value = parsed
                }
            }
        }
    }

    /**
     * Сохранить коэффициенты периодов ПСП.
     * @param coefficients Карта номеров периодов к процентам
     */
    suspend fun savePspCoefficients(coefficients: Map<Int, Double>) {
        _pspCoefficientsFlow.value = coefficients
        cachedPspCoefficients = coefficients
        dataStore.edit { prefs ->
            val entries = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
            prefs[PSP_PERIOD_PERCENTAGES] = entries
        }
    }

    /**
     * Установить флаг пропуска автопроверки обновлений.
     * @param skip true - не проверять автоматически
     */
    suspend fun setSkipAutoUpdate(skip: Boolean) {
        dataStore.edit { prefs ->
            prefs[SKIP_AUTO_UPDATE] = skip
        }
    }

    /**
     * Запомнить пропущенную версию.
     * @param version Версия, которую пользователь пропустил
     */
    suspend fun setSkippedVersion(version: String?) {
        dataStore.edit { prefs ->
            if (version != null) {
                prefs[SKIPPED_VERSION] = version
            } else {
                prefs.remove(SKIPPED_VERSION)
            }
        }
    }

/**
 * Поток для проверки обновлений при входе.
 */
val checkUpdateOnStartFlow: Flow<Boolean> = dataStore.data.map { it[CHECK_UPDATE_ON_START] ?: true }

/**
 * Поток для умных уведомлений (учёт времени клика по кнопке).
 */
val smartNotificationsFlow: Flow<Boolean> = dataStore.data.map { it[SMART_NOTIFICATIONS] ?: false }

/**
 * Поток флага пропуска автопроверки обновлений.
 */
val skipAutoUpdateFlow: Flow<Boolean> = dataStore.data.map { it[SKIP_AUTO_UPDATE] ?: false }

/**
 * Поток для состояния темной темы.
 * По умолчанию true (тёмная тема).
 */
val darkThemeFlow: Flow<Boolean> = dataStore.data.map { it[DARK_THEME] ?: true }

/**
 * Поток со смещением X кнопки обновления браузера (dp).
 * По умолчанию 400 (слева).
 */
val browserFabOffsetXFlow: Flow<Int> = dataStore.data.map { it[BROWSER_FAB_OFFSET_X] ?: 400 }

/**
 * Поток со смещением Y кнопки обновления браузера (dp).
 * По умолчанию 16.
 */
val browserFabOffsetYFlow: Flow<Int> = dataStore.data.map { it[BROWSER_FAB_OFFSET_Y] ?: 16 }

/**
 * Установить флаг проверки обновлений при входе.
 * @param check true - проверять при входе
 */
suspend fun setCheckUpdateOnStart(check: Boolean) {
    dataStore.edit { prefs ->
        prefs[CHECK_UPDATE_ON_START] = check
    }
}

/**
 * Получить состояние умных уведомлений.
 */
suspend fun getSmartNotifications(): Boolean = smartNotificationsFlow.first()

/**
 * Установить состояние умных уведомлений.
 * @param enabled true - включено
 */
suspend fun setSmartNotifications(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[SMART_NOTIFICATIONS] = enabled
    }
}

/**
 * Установить состояние темной темы.
 * @param dark true - тёмная тема, false - светлая
 */
suspend fun setDarkTheme(dark: Boolean) {
    dataStore.edit { prefs ->
        prefs[DARK_THEME] = dark
    }
}

/**
 * Сохранить позицию кнопки обновления браузера.
 * @param offsetX Смещение по X в dp
 * @param offsetY Смещение по Y в dp
 */
suspend fun saveBrowserFabOffset(offsetX: Int, offsetY: Int) {
    dataStore.edit { prefs ->
        prefs[BROWSER_FAB_OFFSET_X] = offsetX
        prefs[BROWSER_FAB_OFFSET_Y] = offsetY
    }
}

    /**
     * Инициализировать настройки значениями по умолчанию при первом запуске.
     * Записывает дефолтные значения ПН, РП и ПСП в БД настроек.
     */
    suspend fun initializeDefaults() {
        if (initialized) return
        dataStore.edit { prefs ->
            if (!prefs.contains(PN_BONUS_PERCENT)) {
                prefs[PN_BONUS_PERCENT] = 50.0
            }
            if (!prefs.contains(PN_DAILY_PERCENT)) {
                prefs[PN_DAILY_PERCENT] = 2.0
            }
            if (!prefs.contains(START_PERCENT)) {
                prefs[START_PERCENT] = 0.1
            }
            if (!prefs.contains(DAILY_ADDITION)) {
                prefs[DAILY_ADDITION] = 0.003
            }
            if (!prefs.contains(PSP_PERIOD_PERCENTAGES)) {
                val entries = DEFAULT_PSP_COEFFICIENTS.entries.joinToString(";") { "${it.key}=${it.value}" }
                prefs[PSP_PERIOD_PERCENTAGES] = entries
            }
            if (!prefs.contains(E_CURRENCY_COEFFICIENTS)) {
                val entries = DEFAULT_E_CURRENCY_COEFFICIENTS.entries.joinToString(";") { "${it.key}=${it.value}" }
                prefs[E_CURRENCY_COEFFICIENTS] = entries
            }
if (!prefs.contains(CHECK_UPDATE_ON_START)) {
    prefs[CHECK_UPDATE_ON_START] = true
}
if (!prefs.contains(DARK_THEME)) {
    prefs[DARK_THEME] = true
}
if (!prefs.contains(BROWSER_FAB_OFFSET_X)) {
    prefs[BROWSER_FAB_OFFSET_X] = 0
}
if (!prefs.contains(BROWSER_FAB_OFFSET_Y)) {
    prefs[BROWSER_FAB_OFFSET_Y] = 0
}
if (!prefs.contains(DEFAULT_ENTRY_TAB)) {
    prefs[DEFAULT_ENTRY_TAB] = 1
}
if (!prefs.contains(DEFAULT_CALC_TAB)) {
    prefs[DEFAULT_CALC_TAB] = 3
}
        }
        initialized = true
    }

    private var cachedECurrencyCoefficients: Map<Double, Double> = DEFAULT_E_CURRENCY_COEFFICIENTS

    /**
     * MutableStateFlow для коэффициентов E-currency - обновляется сразу после сохранения.
     */
    private val _eCurrencyCoefficientsFlow = MutableStateFlow(DEFAULT_E_CURRENCY_COEFFICIENTS)

    /**
     * Поток с коэффициентами E-currency для РП.
     */
    val eCurrencyCoefficientsFlow: StateFlow<Map<Double, Double>> = _eCurrencyCoefficientsFlow.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            dataStore.data.first().let { preferences ->
                val serialized = preferences[E_CURRENCY_COEFFICIENTS]
                val parsed = mutableMapOf<Double, Double>()
                if (!serialized.isNullOrEmpty()) {
                    serialized.split(";").forEach { entry ->
                        val parts = entry.split("=")
                        if (parts.size == 2) {
                            val key = parts[0].toDoubleOrNull()
                            val value = parts[1].toDoubleOrNull()
                            if (key != null && value != null) {
                                parsed[key] = value
                            }
                        }
                    }
                }
                if (parsed.isNotEmpty()) {
                    _eCurrencyCoefficientsFlow.value = parsed
                }
            }
        }
    }

    /**
     * Сохранить коэффициенты E-currency для РП.
     * @param coefficients Карта пороговых сумм к процентам бонуса
     */
    suspend fun saveECurrencyCoefficients(coefficients: Map<Double, Double>) {
        _eCurrencyCoefficientsFlow.value = coefficients
        cachedECurrencyCoefficients = coefficients
        dataStore.edit { prefs ->
            val entries = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
            prefs[E_CURRENCY_COEFFICIENTS] = entries
        }
    }

    /**
     * Инициализировать кеш коэффициентов E-currency.
     */
    fun initializeECurrencyCache(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            dataStore.data.collect { preferences ->
                val serialized = preferences[E_CURRENCY_COEFFICIENTS]
                if (!serialized.isNullOrEmpty()) {
                    val parsed = mutableMapOf<Double, Double>()
                    serialized.split(";").forEach { entry ->
                        val parts = entry.split("=")
                        if (parts.size == 2) {
                            val key = parts[0].toDoubleOrNull()
                            val value = parts[1].toDoubleOrNull()
                            if (key != null && value != null) {
                                parsed[key] = value
                            }
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        cachedECurrencyCoefficients = parsed
                    }
                }
            }
        }
    }

    /**
     * Получить процент бонуса для суммы.
     * Использует .first() для получения актуальных данных из DataStore.
     * Важно: вызывать из корутины!
     */
    suspend fun getECurrencyBonusPercent(amount: Double): Double {
        val coefficients = eCurrencyCoefficientsFlow.first().entries.sortedByDescending { it.key }
        for ((threshold, bonus) in coefficients) {
            if (amount >= threshold) {
                return bonus
            }
        }
        return 0.0
    }

    /**
     * StateFlow для отображения бонуса в UI при изменении суммы.
     * Обновляется автоматически при изменении суммы.
     */
    private val _eCurrencyBonusPercentState = MutableStateFlow(0.0)
    val eCurrencyBonusPercentState: StateFlow<Double> = _eCurrencyBonusPercentState.asStateFlow()

    /**
     * Обновить процент бонуса для указанной суммы.
     * Вызывать из корутины!
     */
    suspend fun updateECurrencyBonusPercent(amount: Double) {
        _eCurrencyBonusPercentState.value = getECurrencyBonusPercent(amount)
    }
}
