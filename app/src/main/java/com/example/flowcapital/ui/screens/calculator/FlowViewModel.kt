package com.example.flowcapital.ui.screens.calculator

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flowcapital.data.db.*
import com.example.flowcapital.data.logging.AppLogger
import com.example.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
            } else {
                // Новый поток - применяем бонус
                val amountToAdd = calculateECurrencyBonus(amount)
                newInFlowAmount = previousInFlow + amountToAdd
                newPercent = percentOrAccrual ?: startPercent.value
                newDailyAccrual = newInFlowAmount * (newPercent / 100.0)
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
     * - В воскресенье создаётся запись SUNDAY (значения не меняются)
     * - При пропуске дня создаётся запись MISSED
     * - При нажатии: уменьшается поток, увеличивается кошелёк, растёт процент
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

            // Пропущенный день - создаём MISSED запись (дата +1 день от последней записи)
            if (!isSameDay && !lastEntry.isButtonPressed) {
                val missedEntry = GrowingFlowEntity(
                    date = lastEntry.date + (24 * 60 * 60 * 1000),
                    percent = lastEntry.percent,
                    inFlowAmount = lastEntry.inFlowAmount,
                    dailyAccrual = lastEntry.dailyAccrual,
                    walletAmount = lastEntry.walletAmount,
                    isButtonPressed = false,
                    actionType = "MISSED"
                )
                growingRepository.insertEntry(missedEntry)
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
                    // Изменилось только начислени�� - пересчитываем процент
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
     * @param amount Сумма взноса
     * @param wallet Кошелёк (null - не менять)
     * @param useECurrency Использовать E-currency бонус (150% от суммы)
     */
    fun addToNoviceFlow(amount: Double, wallet: Double?, useECurrency: Boolean) {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry()
            val previousInFlow = lastEntry?.inFlowAmount ?: 0.0

            val pnBonus = settingsManager.pnBonusPercentFlow.first()
            val pnDaily = settingsManager.pnDailyPercentFlow.first()

            val amountToAdd = if (useECurrency) amount * (1 + pnBonus / 100.0) else amount
            val newInFlowAmount = previousInFlow + amountToAdd

            val dailyPercent = pnDaily
            val newDailyAccrual = newInFlowAmount * (pnDaily / 100.0)
            val newWallet = wallet ?: lastEntry?.walletAmount ?: 0.0

            val newEntry = NoviceFlowEntity(
                date = System.currentTimeMillis(),
                percent = dailyPercent,
                inFlowAmount = newInFlowAmount,
                dailyAccrual = newDailyAccrual,
                walletAmount = newWallet,
                isButtonPressed = false,
                actionType = if (lastEntry == null) "PN_START" else "PN_REINVEST"
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
     */
    fun generateNoviceForecast(targetDateMillis: Long) {
        viewModelScope.launch {
            val lastEntry = noviceRepository.getLastEntry() ?: return@launch
            val forecastList = mutableListOf<NoviceFlowEntity>()
            val dailyPercent = settingsManager.pnDailyPercentFlow.first()

            val currentCal = Calendar.getInstance().apply {
                timeInMillis = lastEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                timeInMillis = targetDateMillis
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

            while (currentCal.timeInMillis <= endCal.timeInMillis && simInFlow > 0.0) {
                val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (isSunday) {
                    forecastList.add(NoviceFlowEntity(
                        date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                    ))
                } else {
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

                    forecastList.add(NoviceFlowEntity(
                        date = currentCal.timeInMillis, percent = dailyPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = true, actionType = "PN_FORECAST"
                    ))
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

            // Цикл до исчерпания потока или 730 дней (защита от бесконечного цикла)
            while (simInFlow > 1 && safetyCounter < 730) {
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
     */
    fun generateForecast(targetDateMillis: Long) {
        viewModelScope.launch {
            val lastEntry = growingRepository.getLastEntry() ?: return@launch
            val forecastList = mutableListOf<GrowingFlowEntity>()
            val currentCal = Calendar.getInstance().apply {
                timeInMillis = lastEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                timeInMillis = targetDateMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            var simInFlow = lastEntry.inFlowAmount
            var simPercent = lastEntry.percent
            var simWallet = lastEntry.walletAmount
            var simAccrual = lastEntry.dailyAccrual

            currentCal.add(Calendar.DAY_OF_YEAR, 1)

            while (currentCal.timeInMillis <= endCal.timeInMillis && simInFlow > 0) {
                val isSunday = currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                if (isSunday) {
                    forecastList.add(GrowingFlowEntity(
                        date = currentCal.timeInMillis, percent = simPercent, inFlowAmount = simInFlow,
                        dailyAccrual = simAccrual, walletAmount = simWallet, isButtonPressed = false, actionType = "SUNDAY"
                    ))
                } else {
                    // Расчёт с ростом процента
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
     * По ТЗ: колонка "Шаг" быть не должно.
     */
    fun exportNoviceForecastToCSV(context: Context, uri: Uri, forecastList: List<NoviceFlowEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПН", null)
                    val worksheet = workbook.newWorksheet("ПН")
                    worksheet.value(0, 0, "Дата")
                    worksheet.value(0, 1, "В потоке")
                    worksheet.value(0, 2, "Начисление")
                    worksheet.value(0, 3, "Кошелек")
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    forecastList.forEachIndexed { index, entry ->
                        val row = index + 1
                        worksheet.value(row, 0, dateFormat.format(Date(entry.date)))
                        worksheet.value(row, 1, entry.inFlowAmount)
                        worksheet.value(row, 2, entry.dailyAccrual)
                        worksheet.value(row, 3, entry.walletAmount)
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
