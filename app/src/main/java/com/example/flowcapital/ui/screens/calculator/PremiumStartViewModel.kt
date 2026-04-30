package com.example.flowcapital.ui.screens.calculator

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flowcapital.data.db.PremiumStartFlowEntity
import com.example.flowcapital.data.db.PremiumStartFlowRepository
import com.example.flowcapital.data.db.PremiumStartPeriodEntity
import com.example.flowcapital.data.logging.AppLogger
import com.example.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel для ПСП (Премиум Стартовый Поток).
 * 20 периодов по 14 дней. Коэффициенты из настроек.
 */
class PremiumStartViewModel(
    private val flowRepository: PremiumStartFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    /** Все ПСП потоки */
    val allFlows = flowRepository.allFlows
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentFlowIndex = MutableStateFlow(0)
    val currentFlowIndex: StateFlow<Int> = _currentFlowIndex

    private val _currentFlow = MutableStateFlow<PremiumStartFlowEntity?>(null)
    val currentFlow: StateFlow<PremiumStartFlowEntity?> = _currentFlow

    private val _currentPeriod = MutableStateFlow<PremiumStartPeriodEntity?>(null)
    val currentPeriod: StateFlow<PremiumStartPeriodEntity?> = _currentPeriod

    private val _periods = MutableStateFlow<List<PremiumStartPeriodEntity>>(emptyList())
    val periods: StateFlow<List<PremiumStartPeriodEntity>> = _periods

    private val _forecastResults = MutableStateFlow<List<PremiumStartPeriodEntity>>(emptyList())
    val forecastResults: StateFlow<List<PremiumStartPeriodEntity>> = _forecastResults

    private val _contributionHistory = MutableStateFlow<List<PremiumStartPeriodEntity>>(emptyList())
    val contributionHistory: StateFlow<List<PremiumStartPeriodEntity>> = _contributionHistory

    /** Сумма всех начислений по всем ПСП */
    val totalAccruedAllFlows = allFlows.map { flows ->
        flows.sumOf { it.totalAccrued }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Коэффициенты периодов из настроек (1-20) */
    private val pspCoefficientsFlow = settingsManager.pspCoefficientsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 
            mapOf(1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48, 6 to 127.59, 
                  7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86, 11 to 173.5, 
                  12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1, 
                  17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0))

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
                    _currentPeriod.value = null
                    _periods.value = emptyList()
                    _contributionHistory.value = emptyList()
                }
            }
        }
        
        viewModelScope.launch {
            settingsManager.pspCoefficientsFlow.collect { coefficients ->
                AppLogger.d("PremiumStartViewModel", "Коэффициенты ПСП обновлены: $coefficients")
            }
        }
    }

    /** Переключение на поток по индексу в allFlows */
    fun selectFlow(index: Int) {
        val flows = allFlows.value
        if (flows.isNotEmpty() && index in flows.indices) {
            AppLogger.d("PremiumStartViewModel", "Переключение на поток: index=$index")
            _currentFlowIndex.value = index
            loadFlowData(flows[index].id)
        }
    }

    /** Загрузка данных потока и периодов */
    private fun loadFlowData(flowId: Int) {
        viewModelScope.launch {
            try {
                _currentFlow.value = flowRepository.getFlowById(flowId)
                _currentPeriod.value = flowRepository.getCurrentPeriod(flowId)
                _periods.value = flowRepository.getPeriodsByFlowId(flowId).first()
                _contributionHistory.value = _periods.value.filter { it.isContributionMade }
                AppLogger.d("PremiumStartViewModel", "Загружен поток $flowId: " +
                        "периодов=${_periods.value.size}, текущий=${_currentPeriod.value?.periodNumber}")
            } catch (e: Exception) {
                AppLogger.e("PremiumStartViewModel", "Ошибка загрузки ПСП", e)
                _currentFlow.value = null
                _currentPeriod.value = null
                _periods.value = emptyList()
                _contributionHistory.value = emptyList()
            }
        }
    }

    /**
     * Создание нового ПСП.
     * @param nominalAmount Номинал
     * @param currentPeriod Текущий период (1-20)
     * @param firstPeriodStart Дата начала 1-го периода
     * @param currentPeriodStart Дата начала текущего периода (null если период=1)
     */
    fun createFlow(nominalAmount: Double, currentPeriod: Int, firstPeriodStart: Long, currentPeriodStart: Long?) {
        viewModelScope.launch {
            AppLogger.d("PremiumStartViewModel", "Создание ПСП: номинал=$nominalAmount, " +
                    "период=$currentPeriod, start1=$firstPeriodStart, startCurrent=$currentPeriodStart")
            val periodDuration = 14L * 24 * 60 * 60 * 1000

            // Считаем начисления за прошлые периоды (1..currentPeriod-1)
            var completedAccruals = 0.0
            for (periodNum in 1 until currentPeriod) {
                val percent = pspCoefficientsFlow.value[periodNum] ?: 100.0
                val accrualAmount = nominalAmount * (percent / 100.0)
                completedAccruals += accrualAmount
            }

            val flow = PremiumStartFlowEntity(
                nominalAmount = nominalAmount,
                startDate = firstPeriodStart,
                totalAccrued = completedAccruals,
                isActive = true,
                currentPeriod = currentPeriod
            )
            val flowId = flowRepository.insertFlow(flow)

            // Генерируем все 20 периодов с датами
            var currentCalcStartDate = firstPeriodStart
            val periods = (1..20).map { periodNum ->
                val percent = pspCoefficientsFlow.value[periodNum] ?: 100.0
                val accrualAmount = nominalAmount * (percent / 100.0)

                if (periodNum == currentPeriod && currentPeriodStart != null) {
                    currentCalcStartDate = currentPeriodStart
                }

                val periodStartDate = currentCalcStartDate
                val endDate = periodStartDate + periodDuration
                currentCalcStartDate = endDate

                val isAlreadyDone = periodNum < currentPeriod

                PremiumStartPeriodEntity(
                    flowId = flowId.toInt(),
                    periodNumber = periodNum,
                    percent = percent,
                    startDate = periodStartDate,
                    endDate = endDate,
                    accrualAmount = accrualAmount,
                    isContributionMade = isAlreadyDone,
                    contributionDate = if (isAlreadyDone) periodStartDate else null,
                    isCompleted = isAlreadyDone
                )
            }
            flowRepository.insertPeriods(periods)
            loadFlowData(flowId.toInt())
        }
    }

    /**
     * Взнос номинала за текущий период.
     * @param toPiggyBankAmount Сумма в копилку (Всего получено)
     */
    fun makeContribution(toPiggyBankAmount: Double) {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            val period = _currentPeriod.value ?: return@launch

            AppLogger.d("PremiumStartViewModel", "Взнос ПСП: поток=${flow.id}, " +
                    "период=${period.periodNumber}, вКопилку=$toPiggyBankAmount")

            val contributionDate = System.currentTimeMillis()
            val updatedPeriod = period.copy(
                isContributionMade = true,
                contributionDate = contributionDate,
                isCompleted = true
            )
            flowRepository.updatePeriod(updatedPeriod)

            val newTotalAccrued = flow.totalAccrued + toPiggyBankAmount
            val isLastPeriod = period.periodNumber == 20

            if (isLastPeriod) {
                // 20-й период - закрываем поток
                val updatedFlow = flow.copy(
                    totalAccrued = newTotalAccrued,
                    currentPeriod = 20,
                    isActive = false
                )
                flowRepository.updateFlow(updatedFlow)
                _currentFlow.value = updatedFlow
                AppLogger.i("PremiumStartViewModel", "ПСП закрыт: поток=${flow.id}")
            } else {
                // Переходим к следующему периоду
                val newCurrentPeriodNum = period.periodNumber + 1
                val periodDuration = 14L * 24 * 60 * 60 * 1000
                val endDate = contributionDate + periodDuration
                val percent = pspCoefficientsFlow.value[newCurrentPeriodNum] ?: 100.0
                val accrualAmount = flow.nominalAmount * (percent / 100.0)

                val nextPeriod = PremiumStartPeriodEntity(
                    id = 0,
                    flowId = flow.id,
                    periodNumber = newCurrentPeriodNum,
                    percent = percent,
                    startDate = contributionDate,
                    endDate = endDate,
                    accrualAmount = accrualAmount,
                    isContributionMade = false,
                    contributionDate = null,
                    isCompleted = false
                )
                // Обновляем существующий или создаём новый период
                val existingPeriod = _periods.value.find { it.periodNumber == newCurrentPeriodNum }
                if (existingPeriod != null) {
                    flowRepository.updatePeriod(nextPeriod.copy(id = existingPeriod.id))
                    _currentPeriod.value = nextPeriod.copy(id = existingPeriod.id)
                } else {
                    flowRepository.insertPeriod(nextPeriod)
                    _currentPeriod.value = nextPeriod
                }

                val updatedFlow = flow.copy(
                    totalAccrued = newTotalAccrued,
                    currentPeriod = newCurrentPeriodNum,
                    isActive = true
                )
                flowRepository.updateFlow(updatedFlow)
                _currentFlow.value = updatedFlow
            }
        }
    }

    /** Корректировка периода (сохранение) */
    fun updatePeriod(period: PremiumStartPeriodEntity) {
        viewModelScope.launch {
            flowRepository.updatePeriod(period)
            loadFlowData(_currentFlow.value?.id ?: return@launch)
        }
    }

    /** Корректировка "Всего получено" */
    fun correctTotalAccrued(newTotalAccrued: Double) {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            AppLogger.d("PremiumStartViewModel", "Корректировка totalAccrued: $newTotalAccrued")
            val updatedFlow = flow.copy(totalAccrued = newTotalAccrued)
            flowRepository.updateFlow(updatedFlow)
            _currentFlow.value = updatedFlow
        }
    }

    /** Корректировка даты закрытия периода */
    fun correctPeriodEndDate(newEndDate: Long) {
        viewModelScope.launch {
            val period = _currentPeriod.value ?: return@launch
            AppLogger.d("PremiumStartViewModel", "Корректировка даты периода: ${Date(newEndDate)}")
            val updatedPeriod = period.copy(endDate = newEndDate)
            flowRepository.updatePeriod(updatedPeriod)
            _currentPeriod.value = updatedPeriod
        }
    }

    /** Обновление потока */
    fun updateFlow(flow: PremiumStartFlowEntity) {
        viewModelScope.launch {
            flowRepository.updateFlow(flow)
            _currentFlow.value = flow
            loadFlowData(flow.id)
        }
    }

    /** Генерирует прогноз - оставшиеся периоды */
    fun generateForecast() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            val allPeriods = _periods.value
            val remainingPeriods = allPeriods.filter { it.periodNumber >= flow.currentPeriod }
            _forecastResults.value = remainingPeriods
            AppLogger.d("PremiumStartViewModel", "Прогноз сгенерирован: ${remainingPeriods.size} периодов")
        }
    }

    /** Удаление текущего потока */
    fun deleteCurrentFlow() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            AppLogger.d("PremiumStartViewModel", "Удаление потока: ${flow.id}")
            flowRepository.deleteFlow(flow.id)
        }
    }

    /** Закрытие потока (20-й период) */
    fun closeCurrentFlow() {
        viewModelScope.launch {
            val flow = _currentFlow.value ?: return@launch
            val period = _currentPeriod.value ?: return@launch
            
            AppLogger.d("PremiumStartViewModel", "Закрытие потока: ${flow.id}")
            val updatedPeriod = period.copy(
                isContributionMade = true,
                contributionDate = System.currentTimeMillis(),
                isCompleted = true
            )
            flowRepository.updatePeriod(updatedPeriod)
            
            val updatedFlow = flow.copy(
                isActive = false,
                currentPeriod = 20
            )
            flowRepository.updateFlow(updatedFlow)
            _currentFlow.value = updatedFlow
            loadFlowData(flow.id)
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
                    val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПСП", null)
                    val worksheet = workbook.newWorksheet("Прогноз")
                    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

                    worksheet.value(0, 0, "Период")
                    worksheet.value(0, 1, "Процент")
                    worksheet.value(0, 2, "Начисление")
                    worksheet.value(0, 3, "Дата начала")
                    worksheet.value(0, 4, "Дата окончания")

                    forecastList.forEachIndexed { index, period ->
                        val row = index + 1
                        worksheet.value(row, 0, period.periodNumber)
                        worksheet.value(row, 1, period.percent)
                        worksheet.value(row, 2, period.accrualAmount)
                        worksheet.value(row, 3, dateFormat.format(Date(period.startDate)))
                        worksheet.value(row, 4, dateFormat.format(Date(period.endDate)))
                    }

                    val totalRow = forecastList.size + 1
                    val total = forecastList.sumOf { it.accrualAmount }
                    worksheet.value(totalRow, 0, "Итого")
                    worksheet.value(totalRow, 2, total)

                    val lastRow = forecastList.size + 1 // +1 для строки с итогом
                    worksheet.range(0, 0, lastRow, 4).style().horizontalAlignment("center").set()
                    workbook.finish()
                }
                AppLogger.i("PremiumStartViewModel", "Прогноз экспортирован: ${forecastList.size} периодов")
            } catch (e: Exception) { AppLogger.e("PremiumStartViewModel", "Ошибка экспорта PSP прогноза", e) }
        }
    }
}

/**
 * Factory для создания PremiumStartViewModel с зависимостями.
 */
class PremiumStartViewModelFactory(
    private val flowRepository: PremiumStartFlowRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PremiumStartViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PremiumStartViewModel(flowRepository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
