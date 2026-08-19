package com.flowhack.flowcapital.ui.screens.calculator

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowhack.flowcapital.data.db.FastFlowDayEntity
import com.flowhack.flowcapital.data.db.FastFlowEntity
import com.flowhack.flowcapital.data.db.FastFlowRepository
import com.flowhack.flowcapital.data.forecast.FAST_FLOW_TYPE_BP
import com.flowhack.flowcapital.data.forecast.calculateFastFlowCloseDate
import com.flowhack.flowcapital.data.forecast.calculateFastFlowDailyAccrual
import com.flowhack.flowcapital.data.forecast.calculateFastFlowForecast
import com.flowhack.flowcapital.data.forecast.generateFastFlowPastDays
import com.flowhack.flowcapital.data.forecast.getFastFlowDayCount
import com.flowhack.flowcapital.data.forecast.getFastFlowPercentForNominal
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel для Быстрого Потока (БП) и Супер Быстрого Потока (СБП).
 *
 * Обрабатывает:
 * - Создание потоков (с генерацией прошлых дней при currentDay > 1)
 * - Ежедневное нажатие кнопки (воскресенья, пропуски, защита от дублей)
 * - Генерацию пропущенных дней
 * - Корректировки (Всего начислено, Начисление, Текущий день)
 * - Прогноз от текущего дня до конца потока
 * - Экспорт прогноза в Excel
 */
class FastFlowViewModel(
    private val flowRepository: FastFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val missedMutex = Mutex()

    /** Все БП/СБП потоки */
    val allFlows = flowRepository.allFlows
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentFlowIndex = MutableStateFlow(0)
    val currentFlowIndex: StateFlow<Int> = _currentFlowIndex

    private val _currentFlow = MutableStateFlow<FastFlowEntity?>(null)
    val currentFlow: StateFlow<FastFlowEntity?> = _currentFlow

    private val _days = MutableStateFlow<List<FastFlowDayEntity>>(emptyList())
    val days: StateFlow<List<FastFlowDayEntity>> = _days

    private val _forecastResults = MutableStateFlow<List<FastFlowDayEntity>>(emptyList())
    val forecastResults: StateFlow<List<FastFlowDayEntity>> = _forecastResults

    /** Сумма всех начислений по всем БП/СБП */
    val totalAccruedAllFlows = allFlows.map { flows ->
        flows.sumOf { it.totalAccrued }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Коэффициенты БП из настроек */
    private val bpCoefficientsFlow = settingsManager.bpCoefficientsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsManager.bpCoefficientsFlow.value)

    /** Коэффициенты СБП из настроек */
    private val sbpCoefficientsFlow = settingsManager.sbpCoefficientsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsManager.sbpCoefficientsFlow.value)

    init {
        viewModelScope.launch {
            allFlows.collect { flows ->
                if (flows.isNotEmpty()) {
                    val safeIndex = _currentFlowIndex.value.coerceIn(0, flows.size - 1)
                    if (safeIndex < flows.size) {
                        _currentFlowIndex.value = safeIndex
                        loadFlowData(flows[safeIndex].id)
                    }
                } else {
                    _currentFlowIndex.value = 0
                    _currentFlow.value = null
                    _days.value = emptyList()
                }
            }
        }
    }

    /** Переключение на поток по индексу в allFlows */
    fun selectFlow(index: Int) {
        val flows = allFlows.value
        if (flows.isNotEmpty() && index in flows.indices) {
            AppLogger.d("FastFlowViewModel", "Переключение на поток: index=$index")
            _currentFlowIndex.value = index
            loadFlowData(flows[index].id)
        }
    }

    /** Загрузка данных потока и дней */
    private fun loadFlowData(flowId: Int) {
        viewModelScope.launch {
            try {
                _currentFlow.value = flowRepository.getFlowById(flowId)
                _days.value = flowRepository.getDaysByFlowId(flowId).first()
                AppLogger.d("FastFlowViewModel", "Загружен поток $flowId: дней=${_days.value.size}")
            } catch (e: Exception) {
                AppLogger.e("FastFlowViewModel", "Ошибка загрузки БП/СБП", e)
                _currentFlow.value = null
                _days.value = emptyList()
            }
        }
    }

    /**
     * Создание нового БП/СБП потока.
     * @param type Тип потока ("BP" или "SBP")
     * @param nominal Номинал
     * @param currentDay Текущий день (1..dayCount)
     * @param startDate Дата старта
     */
    fun createFlow(type: String, nominal: Double, currentDay: Int, startDate: Long) {
        viewModelScope.launch {
            AppLogger.d("FastFlowViewModel", "Создание потока: type=$type, номинал=$nominal, " +
                    "день=$currentDay, старт=$startDate")

            val coefficients = if (type == FAST_FLOW_TYPE_BP) bpCoefficientsFlow.value else sbpCoefficientsFlow.value
            val percent = getFastFlowPercentForNominal(nominal, coefficients)
            val dailyAccrual = calculateFastFlowDailyAccrual(nominal, percent, type)
            val dayCount = getFastFlowDayCount(type)
            val totalAccrued = (currentDay - 1) * dailyAccrual

            val flow = FastFlowEntity(
                type = type,
                nominalAmount = nominal,
                startDate = startDate,
                currentDay = currentDay,
                totalAccrued = totalAccrued,
                dailyAccrual = dailyAccrual,
                percent = percent,
                isActive = currentDay <= dayCount
            )
            val flowId = flowRepository.insertFlow(flow)

            // Генерация прошлых дней (1..currentDay-1) с учётом воскресений
            val pastDays = generateFastFlowPastDays(startDate, currentDay, type, dailyAccrual)
                .map { it.copy(flowId = flowId.toInt()) }
            if (pastDays.isNotEmpty()) {
                flowRepository.insertDays(pastDays)
            }

            loadFlowData(flowId.toInt())
        }
    }

    /**
     * Обработка ежедневного нажатия кнопки для БП/СБП.
     *
     * Логика:
     * - Воскресенье: создаётся запись SUNDAY (без начисления, currentDay не растёт)
     * - Защита от двойного нажатия в один день
     * - При нажатии: создаётся DAILY запись, totalAccrued растёт на dailyAccrual,
     *   currentDay увеличивается на 1
     * - Пропущенный день не теряется: currentDay растёт только по нажатиям,
     *   поэтому все dayCount начислений будут получены (поток растягивается)
     */
    fun pressDailyButton() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            if (!flow.isActive) return@launch

            val today = Calendar.getInstance()
            val isSunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val todayStart = today.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val allDays = flowRepository.getAllDaysForFlow(flow.id)

            // Воскресенье — выходной
            if (isSunday) {
                val hasSundayToday = allDays.any {
                    it.actionType == "SUNDAY" && isSameDay(it.date, todayStart)
                }
                if (!hasSundayToday) {
                    flowRepository.insertDay(FastFlowDayEntity(
                        flowId = flow.id,
                        dayNumber = flow.currentDay,
                        date = System.currentTimeMillis(),
                        accrualAmount = 0.0,
                        isButtonPressed = false,
                        actionType = "SUNDAY"
                    ))
                    AppLogger.d("FastFlowViewModel", "Создана SUNDAY для потока ${flow.id}")
                }
                loadFlowData(flow.id)
                return@launch
            }

            // Защита от двойного нажатия в один день
            val pressedToday = allDays.any {
                it.isButtonPressed && isSameDay(it.date, todayStart)
            }
            if (pressedToday) return@launch

            // Создаём DAILY запись для текущего дня
            val dayNumber = flow.currentDay
            val accrual = flow.dailyAccrual
            flowRepository.insertDay(FastFlowDayEntity(
                flowId = flow.id,
                dayNumber = dayNumber,
                date = System.currentTimeMillis(),
                accrualAmount = accrual,
                isButtonPressed = true,
                actionType = "DAILY"
            ))

            // Обновляем поток
            val newTotal = flow.totalAccrued + accrual
            val newCurrentDay = dayNumber + 1
            val dayCount = getFastFlowDayCount(flow.type)
            val updatedFlow = flow.copy(
                totalAccrued = newTotal,
                currentDay = newCurrentDay,
                isActive = newCurrentDay <= dayCount
            )
            flowRepository.updateFlow(updatedFlow)
            _currentFlow.value = updatedFlow
            loadFlowData(flow.id)
            AppLogger.d("FastFlowViewModel", "Нажатие: поток=${flow.id}, день=$dayNumber, " +
                    "начисление=$accrual, всего=$newTotal")
        }
    }

    /**
     * Генерация пропущенных дней для текущего БП/СБП потока.
     * Создаёт записи MISSED за пропущенные дни (где нет нажатия) и SUNDAY за воскресенья.
     * Цикл прерывается при нахождении START (день 1).
     */
    fun generateMissedDaysForFastFlow() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            missedMutex.withLock {
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val startDate = Instant.ofEpochMilli(flow.startDate).atZone(zoneId).toLocalDate()

                val allDays = flowRepository.getAllDaysForFlow(flow.id)

                var checkDate = today
                var needSundayCheck = true
                var needMissedCheck = true

                while ((needSundayCheck || needMissedCheck) && !checkDate.isBefore(startDate)) {
                    val dayStart = checkDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val dayEnd = checkDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val entriesForDate = allDays.filter { it.date in dayStart until dayEnd }

                    val hasStartInDay = entriesForDate.any { it.actionType == "START" }
                    val hasSundayRecord = entriesForDate.any { it.actionType == "SUNDAY" }
                    val hasDailyRecord = entriesForDate.any {
                        it.isButtonPressed && it.actionType != "SUNDAY"
                    }
                    val hasMissedRecord = entriesForDate.any { it.actionType == "MISSED" }

                    if (hasStartInDay) {
                        if (needSundayCheck && checkDate.dayOfWeek == java.time.DayOfWeek.SUNDAY && !hasSundayRecord) {
                            flowRepository.insertDay(FastFlowDayEntity(
                                flowId = flow.id,
                                dayNumber = flow.currentDay,
                                date = dayStart,
                                accrualAmount = 0.0,
                                isButtonPressed = false,
                                actionType = "SUNDAY"
                            ))
                        }
                        break
                    }

                    if (checkDate == today) {
                        // Текущий день — только воскресенья
                        if (needSundayCheck && checkDate.dayOfWeek == java.time.DayOfWeek.SUNDAY && !hasSundayRecord) {
                            flowRepository.insertDay(FastFlowDayEntity(
                                flowId = flow.id,
                                dayNumber = flow.currentDay,
                                date = dayStart,
                                accrualAmount = 0.0,
                                isButtonPressed = false,
                                actionType = "SUNDAY"
                            ))
                            needSundayCheck = false
                        }
                    } else {
                        // Прошлые дни — воскресенья и пропуски
                        if (needSundayCheck && checkDate.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            if (!hasSundayRecord) {
                                flowRepository.insertDay(FastFlowDayEntity(
                                    flowId = flow.id,
                                    dayNumber = flow.currentDay,
                                    date = dayStart,
                                    accrualAmount = 0.0,
                                    isButtonPressed = false,
                                    actionType = "SUNDAY"
                                ))
                            } else {
                                needSundayCheck = false
                            }
                        } else if (needMissedCheck && checkDate.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                            if (!hasDailyRecord && !hasMissedRecord) {
                                flowRepository.insertDay(FastFlowDayEntity(
                                    flowId = flow.id,
                                    dayNumber = flow.currentDay,
                                    date = dayStart,
                                    accrualAmount = 0.0,
                                    isButtonPressed = false,
                                    actionType = "MISSED"
                                ))
                                AppLogger.d("FastFlowViewModel", "Создана MISSED за $checkDate")
                            } else if (hasDailyRecord || hasMissedRecord) {
                                needMissedCheck = false
                            }
                        }
                    }

                    checkDate = checkDate.minusDays(1)
                }
                loadFlowData(flow.id)
            }
        }
    }

    /**
     * Корректировка значений БП/СБП.
     * @param totalAccrued Новое значение "Всего начислено"
     * @param dailyAccrual Новое значение "Начисление"
     * @param currentDay Новое значение "Текущий день"
     */
    fun makeCorrection(totalAccrued: Double, dailyAccrual: Double, currentDay: Int) {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            AppLogger.d("FastFlowViewModel", "Корректировка потока ${flow.id}: " +
                    "всего=$totalAccrued, начисление=$dailyAccrual, день=$currentDay")

            val dayCount = getFastFlowDayCount(flow.type)
            val updatedFlow = flow.copy(
                totalAccrued = totalAccrued,
                dailyAccrual = dailyAccrual,
                currentDay = currentDay,
                isActive = currentDay <= dayCount
            )
            flowRepository.updateFlow(updatedFlow)
            _currentFlow.value = updatedFlow

            // Запись CORRECTION в историю
            flowRepository.insertDay(FastFlowDayEntity(
                flowId = flow.id,
                dayNumber = currentDay,
                date = System.currentTimeMillis(),
                accrualAmount = dailyAccrual,
                isButtonPressed = false,
                actionType = "CORRECTION"
            ))
            loadFlowData(flow.id)
        }
    }

    /** Генерирует прогноз от текущего дня до конца потока */
    fun generateForecast() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            val dayCount = getFastFlowDayCount(flow.type)
            val remaining = dayCount - flow.currentDay + 1
            if (remaining <= 0) {
                _forecastResults.value = emptyList()
                return@launch
            }

            // Прогноз: генерируем оставшиеся рабочие дни с учётом воскресений.
            // Если сегодня кнопка уже нажата — начинаем со следующего дня.
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val allDays = flowRepository.getAllDaysForFlow(flow.id)
            val pressedToday = allDays.any { it.isButtonPressed && isSameDay(it.date, todayStart) }

            val forecast = mutableListOf<FastFlowDayEntity>()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (pressedToday) add(Calendar.DAY_OF_YEAR, 1)
            }

            var dayNumber = flow.currentDay
            var accruedSum = 0.0
            val total = flow.nominalAmount * (1.0 + flow.percent / 100.0)

            while (dayNumber <= dayCount) {
                val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                if (isSunday) {
                    forecast.add(FastFlowDayEntity(
                        flowId = flow.id,
                        dayNumber = dayNumber,
                        date = calendar.timeInMillis,
                        accrualAmount = 0.0,
                        isButtonPressed = false,
                        actionType = "SUNDAY"
                    ))
                } else {
                    val isLastDay = dayNumber == dayCount
                    val accrual = if (isLastDay) {
                        (total - flow.totalAccrued - accruedSum).let {
                            if (it < 0) 0.0 else it
                        }
                    } else {
                        flow.dailyAccrual
                    }
                    accruedSum += accrual
                    forecast.add(FastFlowDayEntity(
                        flowId = flow.id,
                        dayNumber = dayNumber,
                        date = calendar.timeInMillis,
                        accrualAmount = accrual,
                        isButtonPressed = true,
                        actionType = "DAILY"
                    ))
                    dayNumber++
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            _forecastResults.value = forecast
            AppLogger.d("FastFlowViewModel", "Прогноз сгенерирован: ${forecast.size} записей")
        }
    }

    /** Удаление текущего потока */
    fun deleteCurrentFlow() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            AppLogger.d("FastFlowViewModel", "Удаление потока: ${flow.id}")
            flowRepository.deleteFlow(flow.id)
        }
    }

    fun clearForecast() {
        _forecastResults.value = emptyList()
    }

    /** Экспорт прогноза в Excel */
    fun exportForecastToExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val forecastList = _forecastResults.value
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз БП/СБП", null)
                    val worksheet = workbook.newWorksheet("Прогноз")
                    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

                    worksheet.value(0, 0, "День")
                    worksheet.value(0, 1, "Дата")
                    worksheet.value(0, 2, "Начислено")

                    forecastList.forEachIndexed { index, day ->
                        val row = index + 1
                        worksheet.value(row, 0, day.dayNumber)
                        worksheet.value(row, 1, dateFormat.format(Date(day.date)))
                        worksheet.value(row, 2, day.accrualAmount)
                    }

                    val totalRow = forecastList.size + 1
                    val total = forecastList.sumOf { it.accrualAmount }
                    worksheet.value(totalRow, 0, "Итого")
                    worksheet.value(totalRow, 2, total)

                    val lastRow = forecastList.size + 1
                    worksheet.range(0, 0, lastRow, 2).style().horizontalAlignment("center").set()
                    workbook.finish()
                }
                AppLogger.i("FastFlowViewModel", "Прогноз экспортирован: ${forecastList.size} записей")
            } catch (e: Exception) {
                AppLogger.e("FastFlowViewModel", "Ошибка экспорта прогноза БП/СБП", e)
            }
        }
    }

    /** Проверка, что две даты приходятся на один календарный день */
    private fun isSameDay(millis: Long, todayStart: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis == todayStart
    }
}

/**
 * Factory для создания FastFlowViewModel с зависимостями.
 */
class FastFlowViewModelFactory(
    private val flowRepository: FastFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FastFlowViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FastFlowViewModel(flowRepository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
