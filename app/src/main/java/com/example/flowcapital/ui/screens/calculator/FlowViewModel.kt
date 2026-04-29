package com.example.flowcapital.ui.screens.calculator

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.GrowingFlowRepository
import com.example.flowcapital.data.db.NoviceFlowEntity
import com.example.flowcapital.data.db.NoviceFlowRepository
import com.example.flowcapital.data.logging.AppLogger
import com.example.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel для управления РП (Растущий Поток) и ПН (Поток Новичка).
 *
 * Обрабатывает:
 * - Ежедневные начисления
 * - Реинвест/старт операции
 * - Корректировки
 * - Прогнозы (по дате, конец цикла, лучшая дата)
 * - Экспорт данных в CSV
 */
class FlowViewModel(
    private val growingRepository: GrowingFlowRepository,
    private val noviceRepository: NoviceFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    /** История РП */
    val growingHistory = growingRepository.allHistory
    /** История ПН */
    val noviceHistory = noviceRepository.allHistory

    /** Стартовый процент вывода для РП */
    private val startPercent = settingsManager.startPercentFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0.1)
    /** Ежедневное увеличение процента для РП */
    private val dailyAddition = settingsManager.dailyAdditionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0.003)

    /** Бонус за взнос для ПН - получаем актуальное значение из settingsManager */
    val pnBonusPercent get() = settingsManager.pnBonusPercentFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 50.0)
    /** Ежедневный процент для ПН - получаем актуальное значение из settingsManager */
    val pnDailyPercent get() = settingsManager.pnDailyPercentFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 2.0)

    /** E-currency коэффициенты из настроек */
    private val eCurrencyCoefficients: Flow<Map<Double, Double>>
        get() = settingsManager.eCurrencyCoefficientsFlow

    /** Результаты прогноза РП */
    private val _forecastResults = MutableStateFlow<List<GrowingFlowEntity>>(emptyList())
    val forecastResults: StateFlow<List<GrowingFlowEntity>> = _forecastResults

    /** Результаты прогноза лучшей даты РП */
    private val _bestDateForecast = MutableStateFlow<List<GrowingFlowEntity>>(emptyList())
    val bestDateForecast: StateFlow<List<GrowingFlowEntity>> = _bestDateForecast

    /** Результаты прогноза ПН */
    private val _pnForecastResults = MutableStateFlow<List<NoviceFlowEntity>>(emptyList())
    val pnForecastResults: StateFlow<List<NoviceFlowEntity>> = _pnForecastResults

    /** Результаты прогноза конца цикла ПН */
    private val _pnCycleEndForecast = MutableStateFlow<List<NoviceFlowEntity>>(emptyList())
    val pnCycleEndForecast: StateFlow<List<NoviceFlowEntity>> = _pnCycleEndForecast

    /**
     * Расчёт E-currency бонуса в зависимости от суммы взноса.
     * Таблица коэффициентов из настроек:
     * - >= 1 000 000: +200%
     * - >= 500 000: +175%
     * - >= 100 000: +150%
     * - >= 50 000: +125%
     * - >= 10 000: +100%
     * - >= 5 000: +75%
     * - >= 1 000: +50%
     */
    private suspend fun calculateECurrencyBonus(amount: Double): Double {
        val coefficients = eCurrencyCoefficients.first().entries.sortedByDescending { it.key }
        for ((threshold, bonus) in coefficients) {
            if (amount >= threshold) {
                return amount * (1 + bonus / 100.0)
            }
        }
        return amount
    }

    /**
     * Получить процент бонуса для суммы (из настроек).
     */
    private suspend fun getECurrencyBonusPercent(amount: Double): Double {
        val coefficients = eCurrencyCoefficients.first().entries.sortedByDescending { it.key }
        for ((threshold, bonus) in coefficients) {
            if (amount >= threshold) {
                return bonus
            }
        }
        return 0.0
    }

    /**
     * Добавить реинвест или старт для РП.
     *
     * @param amount Сумма взноса (для нового - с бонусом, для существующего - без)
     * @param percentOrAccrual Процент вывода (для нового) или начисление (для существующего)
     * @param wallet Текущий кошелёк (если null - остаётся без изменений)
     * @param isExistingFlow true если это перенос существующего потока
     */
    fun addReinvestOrStart(amount: Double, percentOrAccrual: Double?, wallet: Double?, isExistingFlow: Boolean = false) {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry()

            Timber.tag("FlowViewModel").d("addReinvestOrStart: amount=%.2f, exists=%b, isExistingFlow=%b", amount, lastEntry != null, isExistingFlow)
            
            val previousInFlow = lastEntry?.inFlowAmount ?: 0.0
            val newWallet = wallet ?: lastEntry?.walletAmount ?: 0.0
            
            val newInFlowAmount: Double
            val newPercent: Double
            val newDailyAccrual: Double
            
            if (isExistingFlow) {
                // Режим действующего потока - процент рассчитывается из начисления
                newInFlowAmount = amount
                val accrual = percentOrAccrual ?: 0.0
                newPercent = if (amount > 0) (accrual * 100.0) / amount else startPercent.value
                newDailyAccrual = accrual
                Timber.tag("FlowViewModel").d("Режим действующего: inFlow=%.2f, percent=%.3f, accrual=%.2f", newInFlowAmount, newPercent, newDailyAccrual)
            } else {
                // Новый поток - применяем бонус
                val amountToAdd = calculateECurrencyBonus(amount)
                newInFlowAmount = previousInFlow + amountToAdd
                newPercent = percentOrAccrual ?: startPercent.value
                newDailyAccrual = newInFlowAmount * (newPercent / 100.0)
                Timber.tag("FlowViewModel").d("Новый поток: bonus=%.2f, inFlow=%.2f, percent=%.3f, accrual=%.2f", amountToAdd, newInFlowAmount, newPercent, newDailyAccrual)
            }

            val newEntry = GrowingFlowEntity(
                date = System.currentTimeMillis(),
                percent = newPercent,
                inFlowAmount = newInFlowAmount,
                dailyAccrual = newDailyAccrual,
                walletAmount = newWallet,
                isButtonPressed = false,
                actionType = if (lastEntry == null) "START" else "REINVEST"
            )
            growingRepository.insertEntry(newEntry)
        }
    }

    /**
     * Обработка ежедневного нажатия кнопки для РП.
     *
     * Логика:
     * - Пропущенные дни уже сгенерированы через generateMissedDaysForGrowingFlow() при открытии вкладки
     * - В воскресенье создаётся запись SUNDAY (значения не меняются)
     * - При нажатии: уменьшается поток, увеличивается кошелёк, растёт процент
     * - Если на сегодня уже есть MISSED - обновляем его в DAILY, не создаём новый
     */
    fun pressDailyButton() {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry() ?: return@launch
            val today = Calendar.getInstance()
            val isSunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

            val entryDate = Calendar.getInstance().apply { timeInMillis = lastEntry.date }
            val isSameDay = today.get(Calendar.YEAR) == entryDate.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == entryDate.get(Calendar.DAY_OF_YEAR)

            // Воскресенье - создаём SUNDAY запись (значения не меняются)
            if (isSunday) {
                val newEntry = GrowingFlowEntity(
                    date = System.currentTimeMillis(),
                    percent = lastEntry.percent,
                    inFlowAmount = lastEntry.inFlowAmount,
                    dailyAccrual = lastEntry.dailyAccrual,
                    walletAmount = lastEntry.walletAmount,
                    isButtonPressed = false,
                    actionType = "SUNDAY"
                )
                growingRepository.insertEntry(newEntry)
                return@launch
            }

            // Уже нажато сегодня - выходим
            if (isSameDay && lastEntry.isButtonPressed) return@launch

            // Расчёт новых значений
            var currentAccrual = lastEntry.dailyAccrual
            var newInFlow = lastEntry.inFlowAmount - currentAccrual

            // Защита от отрицательного потока
            if (newInFlow <= 0) {
                currentAccrual = lastEntry.inFlowAmount
                newInFlow = 0.0
            }

            // Увеличиваем кошелёк на сумму начисления
            val newWallet = lastEntry.walletAmount + currentAccrual
            // Увеличиваем процент на ежедневный прирост
            val newPercent = lastEntry.percent + dailyAddition.value
            // Пересчитываем начисление для нового потока
            val newDailyAccrual = if (newInFlow > 0) newInFlow * (newPercent / 100.0) else 0.0

            // Проверяем, есть ли MISSED на сегодня (созданный generateMissedDaysForGrowingFlow)
            val todayStart = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayEnd = todayStart + (24 * 60 * 60 * 1000)

            val todayEntries = growingRepository.getEntriesForDateRange(todayStart, todayEnd)
            val missedEntry = todayEntries.find { it.actionType == "MISSED" }

            if (missedEntry != null) {
                // Обновляем MISSED в DAILY (не создаём новую запись!)
                val updatedEntry = missedEntry.copy(
                    date = System.currentTimeMillis(),
                    percent = newPercent,
                    inFlowAmount = newInFlow,
                    dailyAccrual = newDailyAccrual,
                    walletAmount = newWallet,
                    isButtonPressed = true,
                    actionType = "DAILY"
                )
                growingRepository.updateEntry(updatedEntry)
            } else {
                // Нет MISSED - создаём новую DAILY запись
                val newEntry = GrowingFlowEntity(
                    date = System.currentTimeMillis(),
                    percent = newPercent,
                    inFlowAmount = newInFlow,
                    dailyAccrual = newDailyAccrual,
                    walletAmount = newWallet,
                    isButtonPressed = true,
                    actionType = "DAILY"
                )
                growingRepository.insertEntry(newEntry)
            }
        }
    }

    /**
     * Генерация пропущенных дней для РП при отрисовке таблицы истории.
     * Согласно ТЗ О1:
     * - Проверяем с D-1 (вчера) и идем назад до дня старта потока (START) включительно
     * - Создаем записи MISSED за пропущенные дни, где нет DAILY
     * - В день старта потока кнопка также активна - если нет DAILY, создаем MISSED (будет 2 записи: START и MISSED)
     * - Цикл прерывается ТОЛЬКО при нахождении DAILY (не START!)
     * - Учитываем, что могут быть реинвесты/корректировки, но DAILY должен быть каждый день (кроме воскресенья)
     */
    fun generateMissedDaysForGrowingFlow() {
        viewModelScope.launch {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            
            // Получаем дату первой записи START (начало потока)
            val firstStartEntry = growingRepository.getFirstStartEntry() ?: return@launch
            val startDate = Instant.ofEpochMilli(firstStartEntry.date).atZone(zoneId).toLocalDate()
            
            // Начинаем проверку со вчера
            var checkDate = today.minusDays(1)
            
            // Если вчера было воскресенье - проверяем еще на день назад (с субботы)
            if (checkDate.dayOfWeek == DayOfWeek.SUNDAY) {
                checkDate = checkDate.minusDays(1)
            }
            
            // Идем назад по дням, пока не дойдем до дня ПЕРЕД стартом (startDate - 1)
            // ТЗ: цикл продолжается пока checkDate >= startDate (включительно)
            while (!checkDate.isBefore(startDate)) {
                val dayStart = checkDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val dayEnd = checkDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val entriesForDate = growingRepository.getEntriesForDateRange(dayStart, dayEnd)
                
                // Воскресенье - создаем SUNDAY запись (если еще нет)
                if (checkDate.dayOfWeek == DayOfWeek.SUNDAY) {
                    val hasSunday = entriesForDate.any { it.actionType == "SUNDAY" }
                    if (!hasSunday) {
                        // Получаем последнюю запись перед этим днем для значений
                        val previousEntry = growingRepository.getLastEntryBeforeDate(dayStart) 
                            ?: firstStartEntry
                        
                        // Определяем дату для SUNDAY: если в этот день уже есть записи,
                        // ставим SUNDAY после них (на 1 мс после последней записи дня)
                        val sundayDate = if (entriesForDate.isNotEmpty()) {
                            entriesForDate.maxOf { it.date } + 1
                        } else {
                            dayStart
                        }
                        
                        val sundayEntry = GrowingFlowEntity(
                            date = sundayDate,
                            percent = previousEntry.percent,
                            inFlowAmount = previousEntry.inFlowAmount,
                            dailyAccrual = previousEntry.dailyAccrual,
                            walletAmount = previousEntry.walletAmount,
                            isButtonPressed = false,
                            actionType = "SUNDAY"
                        )
                        growingRepository.insertEntry(sundayEntry)
                        AppLogger.d("FlowViewModel", "Создана запись SUNDAY за ${checkDate}")
                    }
                    checkDate = checkDate.minusDays(1)
                    continue
                }
                
                // Проверяем ТОЛЬКО DAILY (не START!)
                val hasDaily = entriesForDate.any { it.actionType == "DAILY" }
                
                if (hasDaily) {
                    // Нашли DAILY - завершаем проверку
                    break
                }
                
                // Нет DAILY - создаем MISSED (если еще нет)
                // В день старта может быть START, но если нет DAILY - все равно создаем MISSED
                val hasMissed = entriesForDate.any { it.actionType == "MISSED" }
                if (!hasMissed) {
                    // Получаем последнюю запись перед этим днем для значений
                    val previousEntry = growingRepository.getLastEntryBeforeDate(dayStart) 
                        ?: firstStartEntry
                    
                    // Определяем дату для MISSED: если в этот день уже есть записи (например START),
                    // ставим MISSED после них (на 1 мс после последней записи дня)
                    val missedDate = if (entriesForDate.isNotEmpty()) {
                        entriesForDate.maxOf { it.date } + 1
                    } else {
                        dayStart
                    }
                    
                    val missedEntry = GrowingFlowEntity(
                        date = missedDate,
                        percent = previousEntry.percent,
                        inFlowAmount = previousEntry.inFlowAmount,
                        dailyAccrual = previousEntry.dailyAccrual,
                        walletAmount = previousEntry.walletAmount,
                        isButtonPressed = false,
                        actionType = "MISSED"
                    )
                    growingRepository.insertEntry(missedEntry)
                    AppLogger.d("FlowViewModel", "Создана запись MISSED за ${checkDate}")
                }
                
                checkDate = checkDate.minusDays(1)
            }
        }
    }

    /**
     * Корректировка значений РП.
     * Если указано только "В потоке" - пересчитывается начисление (процент остаётся).
     * Если указано только "Начисление" - пересчитывается процент.
     * Кошелёк меняется напрямую.
     */
    fun makeCorrection(inFlow: Double, accrual: Double, wallet: Double, isButtonPressed: Boolean) {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry()
            val currentInFlow = lastEntry?.inFlowAmount ?: 0.0
            val currentAccrual = lastEntry?.dailyAccrual ?: 0.0
            val currentPercent = lastEntry?.percent ?: startPercent.value
            
            val newPercent: Double
            val newAccrual: Double
            
            val inFlowChanged = inFlow != currentInFlow
            val accrualChanged = accrual != currentAccrual
            
            when {
                inFlowChanged && !accrualChanged -> {
                    // Изменился только поток - пересчитываем начисление
                    newPercent = currentPercent
                    newAccrual = inFlow * (newPercent / 100.0)
                }
                accrualChanged && !inFlowChanged -> {
                    // Изменилось только начисление - пересчитываем процент
                    newPercent = if (inFlow > 0) (accrual * 100.0) / inFlow else currentPercent
                    newAccrual = accrual
                }
                inFlowChanged && accrualChanged -> {
                    // Оба изменились - пересчитываем процент по новому начислению
                    newPercent = if (inFlow > 0) (accrual * 100.0) / inFlow else currentPercent
                    newAccrual = accrual
                }
                else -> {
                    // Только кошелёк или чекбокс изменился
                    newPercent = currentPercent
                    newAccrual = currentAccrual
                }
            }
            
            val newEntry = GrowingFlowEntity(
                date = System.currentTimeMillis(),
                percent = newPercent,
                inFlowAmount = inFlow,
                dailyAccrual = newAccrual,
                walletAmount = wallet,
                isButtonPressed = isButtonPressed,
                actionType = "CORRECTION"
            )
            growingRepository.insertEntry(newEntry)
        }
    }

    /**
     * Добавить реинвест или старт для ПН.
     *
     * @param inFlow Сумма в потоке (уже с бонусом или без)
     * @param dailyAccrual Начисление (рассчитанное)
     * @param wallet Кошелёк (0.0 если пустое поле)
     * @param isFirstEntry true если это старт потока (первая запись)
     */
    fun addToNoviceFlow(inFlow: Double, dailyAccrual: Double, wallet: Double, isFirstEntry: Boolean) {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry()
            val previousInFlow = lastEntry?.inFlowAmount ?: 0.0
            val newInFlowAmount = previousInFlow + inFlow
            val newWallet = wallet
            val dailyPercent = lastEntry?.percent ?: settingsManager.pnDailyPercentFlow.first()

            val newEntry = NoviceFlowEntity(
                date = System.currentTimeMillis(),
                percent = dailyPercent,
                inFlowAmount = newInFlowAmount,
                dailyAccrual = dailyAccrual,
                walletAmount = newWallet,
                isButtonPressed = false,
                actionType = if (lastEntry == null || isFirstEntry) "PN_START" else "PN_REINVEST"
            )
            noviceRepository.insertEntry(newEntry)
        }
    }

    /**
     * Обработка ежедневного нажатия кнопки для ПН.
     * Логика аналогична РП: воскресенье, пропуски, ежедневное начисление 2%.
     */
    fun pressNoviceButton() {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry() ?: return@launch
            val today = Calendar.getInstance()
            val isSunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

            val entryDate = Calendar.getInstance().apply { timeInMillis = lastEntry.date }
            val isSameDay = today.get(Calendar.YEAR) == entryDate.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == entryDate.get(Calendar.DAY_OF_YEAR)

            // Воскресенье - SUNDAY запись
            if (isSunday) {
                val newEntry = NoviceFlowEntity(
                    date = System.currentTimeMillis(),
                    percent = lastEntry.percent,
                    inFlowAmount = lastEntry.inFlowAmount,
                    dailyAccrual = lastEntry.dailyAccrual,
                    walletAmount = lastEntry.walletAmount,
                    isButtonPressed = false,
                    actionType = "SUNDAY"
                )
                noviceRepository.insertEntry(newEntry)
                return@launch
            }

            // Уже нажато сегодня - выходим
            if (isSameDay && lastEntry.isButtonPressed) return@launch

            // Пропущенный день - создаём MISSED запись
            if (!isSameDay && !lastEntry.isButtonPressed) {
                val missedEntry = NoviceFlowEntity(
                    date = lastEntry.date + (24 * 60 * 60 * 1000),
                    percent = lastEntry.percent,
                    inFlowAmount = lastEntry.inFlowAmount,
                    dailyAccrual = lastEntry.dailyAccrual,
                    walletAmount = lastEntry.walletAmount,
                    isButtonPressed = false,
                    actionType = "MISSED"
                )
                noviceRepository.insertEntry(missedEntry)
            }

            // ПН использует фиксированный процент
            val dailyPercent = lastEntry.percent
            var currentAccrual = lastEntry.dailyAccrual
            var newInFlow = lastEntry.inFlowAmount - currentAccrual

            if (newInFlow <= 0) {
                currentAccrual = lastEntry.inFlowAmount
                newInFlow = 0.0
            }

            val newWallet = lastEntry.walletAmount + currentAccrual
            // Для ПН процент не растёт, поэтому новый начисление = новый поток * фикс. процент
            val newDailyAccrual = if (newInFlow > 0) newInFlow * (dailyPercent / 100.0) else 0.0

            val newEntry = NoviceFlowEntity(
                date = System.currentTimeMillis(),
                percent = dailyPercent,
                inFlowAmount = newInFlow,
                dailyAccrual = newDailyAccrual,
                walletAmount = newWallet,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
            noviceRepository.insertEntry(newEntry)
        }
    }

    /** Корректировка значений ПН */
    fun makeNoviceCorrection(inFlow: Double, accrual: Double, wallet: Double?, isButtonPressed: Boolean) {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry()
            val dailyPercent = lastEntry?.percent ?: settingsManager.pnDailyPercentFlow.first()
            val newWallet = wallet ?: lastEntry?.walletAmount ?: 0.0
            val newEntry = NoviceFlowEntity(
                date = System.currentTimeMillis(),
                percent = dailyPercent,
                inFlowAmount = inFlow,
                dailyAccrual = accrual,
                walletAmount = newWallet,
                isButtonPressed = isButtonPressed,
                actionType = "PN_CORRECTION"
            )
            noviceRepository.insertEntry(newEntry)
        }
    }

     /**
      * Генерация прогноза ПН до указанной даты.
      * Учитывает воскресенья (SUNDAY записи).
      * Останавливается когда поток достигает 0.
      *
      * Логика:
      * - Используется фиксированный процент из БД настроек
      * - Если кнопка нажата сегодня: прогноз начинается с текущих значений
      * - Если кнопка НЕ нажата сегодня: прогноз начинается с расчётом начисления за сегодня
      * - Если compoundInterest=true: при wallet >= reinvestAmount делается реинвест на всю сумму
      *
      * @param targetDateMillis Дата окончания прогноза
      * @param compoundInterest Включить сложный процент (реинвест при накоплении)
      * @param reinvestAmount Сумма для реинвеста (по умолчанию 2000)
      * @param bonusPercent Процент бонуса за взнос (из настроек)
      */
     fun generateNoviceForecast(
         targetDateMillis: Long,
         compoundInterest: Boolean = false,
         reinvestAmount: Double = 2000.0,
         bonusPercent: Double = 50.0
     ) {
         viewModelScope.launch {
             val lastEntry = noviceRepository.getLastEntry() ?: return@launch
             val forecastList = mutableListOf<NoviceFlowEntity>()
             val dailyPercent = settingsManager.pnDailyPercentFlow.first()

             val today = Calendar.getInstance()
             val todayStart = today.apply {
                 set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
             }.timeInMillis
             val lastEntryDay = Calendar.getInstance().apply { timeInMillis = lastEntry.date }.apply {
                 set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
             }.timeInMillis
             val isLastEntryIsToday = lastEntryDay == todayStart
             val isButtonPressed = lastEntry.isButtonPressed && lastEntry.actionType != "SUNDAY"
             val isTodaySunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

             var simInFlow: Double
             var simWallet: Double
             var simAccrual: Double
             var step = 1

             if (isLastEntryIsToday && isButtonPressed && !isTodaySunday) {
                 simInFlow = lastEntry.inFlowAmount
                 simWallet = lastEntry.walletAmount
                 simAccrual = lastEntry.dailyAccrual
             } else if (isLastEntryIsToday && !isButtonPressed && !isTodaySunday) {
                 val actualAccrual = minOf(lastEntry.inFlowAmount, lastEntry.dailyAccrual)
                 simInFlow = lastEntry.inFlowAmount - actualAccrual
                 if (simInFlow < 0) simInFlow = 0.0
                 simWallet = lastEntry.walletAmount + actualAccrual
                 simAccrual = if (simInFlow > 0) simInFlow * (dailyPercent / 100.0) else 0.0
             } else if (isTodaySunday) {
                 simInFlow = lastEntry.inFlowAmount
                 simWallet = lastEntry.walletAmount
                 simAccrual = lastEntry.dailyAccrual
             } else {
                 val actualAccrual = minOf(lastEntry.inFlowAmount, lastEntry.dailyAccrual)
                 simInFlow = lastEntry.inFlowAmount - actualAccrual
                 if (simInFlow < 0) simInFlow = 0.0
                 simWallet = lastEntry.walletAmount + actualAccrual
                 simAccrual = if (simInFlow > 0) simInFlow * (dailyPercent / 100.0) else 0.0
             }

             val currentCal = Calendar.getInstance().apply {
                 timeInMillis = todayStart
                 set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
             }
             val endCal = Calendar.getInstance().apply {
                 timeInMillis = targetDateMillis
                 set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
             }

             // Первая запись прогноза с учетом состояния кнопки
             if (isLastEntryIsToday && !isButtonPressed && !isTodaySunday && currentCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                 // Кнопка не нажата сегодня - делаем начисление
                 forecastList.add(NoviceFlowEntity(
                     date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                     dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_DAILY"
                 ))
             } else {
                 forecastList.add(NoviceFlowEntity(
                     date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                     dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_FORECAST"
                 ))
             }
             currentCal.add(Calendar.DAY_OF_YEAR, 1)

             // Основной цикл прогноза
             while (currentCal.timeInMillis <= endCal.timeInMillis && simInFlow > 0.0) {
                 val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                 if (isSunday) {
                     forecastList.add(NoviceFlowEntity(
                         date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                         dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                     ))
                 } else {
                     // Нажатие кнопки (начисление)
                     val actualAccrual = simInFlow * (dailyPercent / 100.0)
                     val nextInFlow = simInFlow - actualAccrual
                     val finalAccrual: Double
                     val finalInFlow: Double

                     if (nextInFlow < 0.005) {
                         finalAccrual = simInFlow
                         finalInFlow = 0.0
                         simWallet += simInFlow
                         forecastList.add(NoviceFlowEntity(
                             date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = 0.0,
                             dailyAccrual = 0.0, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_FORECAST"
                         ))
                         break
                     } else {
                         finalAccrual = actualAccrual
                         finalInFlow = nextInFlow
                         simWallet += actualAccrual
                         simAccrual = finalInFlow * (dailyPercent / 100.0)
                     }
                     simInFlow = finalInFlow
                     step++

                     forecastList.add(NoviceFlowEntity(
                         date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                         dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_FORECAST"
                     ))

                     // Сложный процент: проверяем, нужно ли делать реинвест
                     if (compoundInterest && simWallet >= reinvestAmount) {
                         val reinvestAmountActual = simWallet
                         // Реинвест с учётом бонуса (как при обычном взносе)
                         val withBonus = reinvestAmountActual + reinvestAmountActual * (bonusPercent / 100.0)
                         simInFlow += withBonus
                         simWallet = 0.0
                         simAccrual = if (simInFlow > 0) simInFlow * (dailyPercent / 100.0) else 0.0
                         step++

                         forecastList.add(NoviceFlowEntity(
                             date = currentCal.timeInMillis, // Та же дата, что и для DAILY
                             percent = dailyPercent, inFlowAmount = simInFlow,
                             dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true,
                             actionType = "PN_REINVEST"
                         ))
                     }
                 }
                 currentCal.add(Calendar.DAY_OF_YEAR, 1)
             }
             _pnForecastResults.value = forecastList
         }
     }

    /**
     * Генерация прогноза до конца цикла (поток = 0).
     * Симулирует дни до полного исчерпания потока.
     */
    fun generateNoviceCycleEndForecast() {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry() ?: return@launch
            val forecastList = mutableListOf<NoviceFlowEntity>()
            val dailyPercent = settingsManager.pnDailyPercentFlow.first()
            val currentCal = Calendar.getInstance().apply {
                timeInMillis = lastEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            var simInFlow = lastEntry.inFlowAmount
            var simWallet = lastEntry.walletAmount
            var simAccrual = lastEntry.dailyAccrual

            forecastList.add(NoviceFlowEntity(
                date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_START"
            ))

            currentCal.add(Calendar.DAY_OF_YEAR, 1)
            var safetyCounter = 0

            // Цикл до исчерпания потока (когда остаток <= 0.005, что округляется до 0.00) или 730 дней
            while (simInFlow > 0.005 && safetyCounter < 730) {
                val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (isSunday) {
                    forecastList.add(NoviceFlowEntity(
                        date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                    ))
                } else {
                    var actualAccrual = simInFlow * (dailyPercent / 100.0)
                    var nextInFlow = simInFlow - actualAccrual
                    if (nextInFlow < 0.005) {
                        actualAccrual = simInFlow
                        nextInFlow = 0.0
                    }

                    simWallet += actualAccrual
                    simAccrual = if (nextInFlow > 0) nextInFlow * (dailyPercent / 100.0) else 0.0
                    simInFlow = nextInFlow

                    forecastList.add(NoviceFlowEntity(
                        date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_CYCLE_END"
                    ))
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
                safetyCounter++
            }
            _pnCycleEndForecast.value = forecastList
        }
    }

    /**
     * Генерация прогноза РП до указанной даты.
     * Учитывает воскресенья и рост процента (+0.003 за каждое нажатие).
     * Останавливается когда поток достигает 0.
     *
     * Логика:
     * - Если кнопка нажата сегодня: прогноз начинается с текущих значений (без начисления сегодня)
     * - Если кнопка НЕ нажата сегодня: прогноз начинается с расчётом начисления за сегодня
     */
    fun generateForecast(targetDateMillis: Long) {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry() ?: return@launch
            val forecastList = mutableListOf<GrowingFlowEntity>()

            val today = Calendar.getInstance()
            val todayStart = today.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val lastEntryDay = Calendar.getInstance().apply { timeInMillis = lastEntry.date }.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val isLastEntryIsToday = lastEntryDay == todayStart
            val isButtonPressed = lastEntry.isButtonPressed && lastEntry.actionType != "SUNDAY"
            val isTodaySunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

            var simInFlow: Double
            var simPercent: Double
            var simWallet: Double
            var simAccrual: Double

            if (isLastEntryIsToday && isButtonPressed && !isTodaySunday) {
                simInFlow = lastEntry.inFlowAmount
                simPercent = lastEntry.percent
                simWallet = lastEntry.walletAmount
                simAccrual = lastEntry.dailyAccrual
            } else if (isLastEntryIsToday && !isButtonPressed && !isTodaySunday) {
                simAccrual = minOf(lastEntry.inFlowAmount, lastEntry.dailyAccrual)
                simInFlow = lastEntry.inFlowAmount - simAccrual
                if (simInFlow < 0) simInFlow = 0.0
                simWallet = lastEntry.walletAmount + simAccrual
                simPercent = if (simInFlow > 0) lastEntry.percent + dailyAddition.value else lastEntry.percent
                simAccrual = if (simInFlow > 0) simInFlow * (simPercent / 100.0) else 0.0
            } else if (isTodaySunday) {
                simInFlow = lastEntry.inFlowAmount
                simPercent = lastEntry.percent
                simWallet = lastEntry.walletAmount
                simAccrual = lastEntry.dailyAccrual
            } else {
                simAccrual = minOf(lastEntry.inFlowAmount, lastEntry.dailyAccrual)
                simInFlow = lastEntry.inFlowAmount - simAccrual
                if (simInFlow < 0) simInFlow = 0.0
                simWallet = lastEntry.walletAmount + simAccrual
                simPercent = if (simInFlow > 0) lastEntry.percent + dailyAddition.value else lastEntry.percent
                simAccrual = if (simInFlow > 0) simInFlow * (simPercent / 100.0) else 0.0
            }

            val currentCal = Calendar.getInstance().apply {
                timeInMillis = todayStart
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                timeInMillis = targetDateMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            if (isLastEntryIsToday && !isButtonPressed && !isTodaySunday && currentCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                forecastList.add(GrowingFlowEntity(
                    date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                    dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "DAILY"
                ))
            } else {
                forecastList.add(GrowingFlowEntity(
                    date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                    dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "FORECAST"
                ))
            }
            currentCal.add(Calendar.DAY_OF_YEAR, 1)

            while (currentCal.timeInMillis <= endCal.timeInMillis && simInFlow > 0) {
                val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (isSunday) {
                    forecastList.add(GrowingFlowEntity(
                        date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                    ))
                } else {
                    var actualAccrual = simAccrual
                    var nextInFlow = simInFlow - actualAccrual
                    if (nextInFlow <= 0) {
                        actualAccrual = simInFlow
                        simInFlow = 0.0
                    } else {
                        simInFlow = nextInFlow
                    }

                    simWallet += actualAccrual
                    simPercent += dailyAddition.value
                    simAccrual = if (simInFlow > 0) simInFlow * (simPercent / 100.0) else 0.0

                    forecastList.add(GrowingFlowEntity(
                        date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "FORECAST"
                    ))
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            _forecastResults.value = forecastList
        }
    }

    /**
     * Поиск лучшей даты для реинвеста.
     * Находит дату, когда начисление начинает уменьшаться (после точки максимума).
     */
    fun findBestReinvestDate() {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry() ?: return@launch
            val forecastList = mutableListOf<GrowingFlowEntity>()
            val currentCal = Calendar.getInstance().apply {
                timeInMillis = lastEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            var simInFlow = lastEntry.inFlowAmount
            var simPercent = lastEntry.percent
            var simWallet = lastEntry.walletAmount
            var simAccrual = lastEntry.dailyAccrual

            forecastList.add(GrowingFlowEntity(
                date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "START"
            ))

            currentCal.add(Calendar.DAY_OF_YEAR, 1)
            var foundDrop = false
            var showOneMoreAfterDrop = false
            var safetyCounter = 0

            // Ищем момент когда начисление начнёт падать
            while ((!foundDrop || showOneMoreAfterDrop) && safetyCounter < 730) {
                val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (isSunday) {
                    forecastList.add(GrowingFlowEntity(
                        date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                    ))
                } else {
                    var actualAccrual = simAccrual
                    var nextInFlow = simInFlow - actualAccrual
                    if (nextInFlow <= 0) {
                        actualAccrual = simInFlow
                        nextInFlow = 0.0
                    }

                    simWallet += actualAccrual
                    simPercent += dailyAddition.value
                    val nextAccrual = if (nextInFlow > 0) nextInFlow * (simPercent / 100.0) else 0.0

                    // Проверяем начало падения начисления
                    if (nextAccrual < actualAccrual && !foundDrop) {
                        simAccrual = nextAccrual
                        simInFlow = nextInFlow
                        forecastList.add(GrowingFlowEntity(
                            date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                            dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "DROP_DAY"
                        ))
                        foundDrop = true
                    } else {
                        simAccrual = nextAccrual
                        simInFlow = nextInFlow
                        val actionType = if (showOneMoreAfterDrop) "AFTER_BEST_DATE" else if (foundDrop) "FORECAST" else "FORECAST"
                        forecastList.add(GrowingFlowEntity(
                            date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                            dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = actionType
                        ))
                        if (foundDrop && !showOneMoreAfterDrop) {
                            showOneMoreAfterDrop = true
                        } else {
                            showOneMoreAfterDrop = false
                        }
                    }
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
                safetyCounter++
            }
            _bestDateForecast.value = forecastList
        }
    }

    /** Очистка результатов прогнозов */
    fun clearForecast() { _forecastResults.value = emptyList() }
    fun clearBestDateForecast() { _bestDateForecast.value = emptyList() }
    fun clearPnForecast() { _pnForecastResults.value = emptyList() }
    fun clearPnCycleEndForecast() { _pnCycleEndForecast.value = emptyList() }

    /**
     * Экспорт прогноза РП в Excel файл с использованием FastExcel.
     * Формат: Дата,Процент,В потоке,Начисление,Кошелек
     */
    fun exportForecastToCSV(context: Context, uri: Uri, forecastList: List<GrowingFlowEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз РП", null)
                    val worksheet = workbook.newWorksheet("РП")
                    worksheet.value(0, 0, "Дата")
                    worksheet.value(0, 1, "Процент")
                    worksheet.value(0, 2, "В потоке")
                    worksheet.value(0, 3, "Начисление")
                    worksheet.value(0, 4, "Кошелек")
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    forecastList.forEachIndexed { index, entry ->
                        val row = index + 1
                        worksheet.value(row, 0, dateFormat.format(Date(entry.date)))
                        worksheet.value(row, 1, entry.percent)
                        worksheet.value(row, 2, entry.inFlowAmount)
                        worksheet.value(row, 3, entry.dailyAccrual)
                        worksheet.value(row, 4, entry.walletAmount)
                    }
                    workbook.finish()
                }
            } catch (e: Exception) { AppLogger.e("FlowViewModel", "Ошибка экспорта GrowingFlow прогноза", e) }
        }
    }

    /**
     * Экспорт прогноза ПН в Excel файл с использованием FastExcel.
     * По ТЗ: первая колонка "Шаг", затем Дата, В потоке, Начисление, Кошелек.
     * Шаг инкрементируется только для активных действий (START, DAILY, REINVEST).
     * Для SUNDAY и MISSED ставится прочерк ("-").
     */
    fun exportNoviceForecastToCSV(context: Context, uri: Uri, forecastList: List<NoviceFlowEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПН", null)
                val worksheet = workbook.newWorksheet("ПН")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "В потоке")
                worksheet.value(0, 3, "Начисление")
                worksheet.value(0, 4, "Кошелек")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var step = 0
                forecastList.forEachIndexed { index, entry ->
                    val row = index + 1
                    val isActiveAction = entry.actionType in listOf("PN_START", "PN_DAILY", "PN_FORECAST", "PN_REINVEST")
                    if (isActiveAction) {
                        step++
                        worksheet.value(row, 0, step)
                    } else {
                        worksheet.value(row, 0, "-")
                    }
                    worksheet.value(row, 1, dateFormat.format(Date(entry.date)))
                    worksheet.value(row, 2, entry.inFlowAmount)
                    worksheet.value(row, 3, entry.dailyAccrual)
                    worksheet.value(row, 4, entry.walletAmount)
                }
                    workbook.finish()
                }
            } catch (e: Exception) { AppLogger.e("FlowViewModel", "Ошибка экспорта NoviceFlow прогноза", e) }
        }
    }
}

/** Фабрика для создания FlowViewModel с зависимостями */
class FlowViewModelFactory(
    private val growingRepository: GrowingFlowRepository,
    private val noviceRepository: NoviceFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlowViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FlowViewModel(growingRepository, noviceRepository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
