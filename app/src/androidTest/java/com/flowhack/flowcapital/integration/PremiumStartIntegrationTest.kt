package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.PremiumStartFlowEntity
import com.flowhack.flowcapital.data.db.PremiumStartFlowRepository
import com.flowhack.flowcapital.data.db.PremiumStartPeriodEntity
import com.flowhack.flowcapital.data.forecast.calculatePspPeriodEndDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Премиум Стартового Потока (ПСП).
 * Проверяют реальную связку: репозиторий -> БД (Room) с якорным алгоритмом дат.
 */
class PremiumStartIntegrationTest : BaseIntegrationTest() {

    private lateinit var repository: PremiumStartFlowRepository

    override fun setUp() {
        super.setUp()
        repository = PremiumStartFlowRepository(premiumStartFlowDao, premiumStartPeriodDao)
    }

    /**
     * Проверка: Создание потока ПСП сохраняется в БД.
     */
    @Test
    fun createPspFlow_createsFlowInDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)

        // Act
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )

        // Assert
        val flow = repository.getFlowById(flowId.toInt())!!
        assertEquals("Номинал должен быть 5000", 5000.0, flow.nominalAmount, 0.01)
        assertEquals("Текущий период должен быть 1", 1, flow.currentPeriod)
        assertEquals("Всего получено должно быть 0", 0.0, flow.totalAccrued, 0.01)
    }

    /**
     * Проверка: getFlowsCount возвращает количество потоков.
     */
    @Test
    fun getFlowsCount_returnsNumberOfFlows() = runBlocking {
        // Arrange
        repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = createDateMillis(2026, Calendar.APRIL, 1),
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )
        repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = createDateMillis(2026, Calendar.APRIL, 5),
                nominalAmount = 10000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )

        // Act
        val count = repository.getFlowsCount()

        // Assert
        assertEquals("Должно быть 2 потока", 2, count)
    }

    /**
     * Проверка: Создание периодов с якорным алгоритмом дат.
     */
    @Test
    fun createPspPeriods_usesAnchorAlgorithm() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1) // startDay = 1
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        // Act - создаём периоды с якорным алгоритмом
        val periods = listOf(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            ),
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 2,
                percent = 55.8,
                startDate = calculatePspPeriodEndDate(startDate, 1),
                endDate = calculatePspPeriodEndDate(startDate, 2),
                accrualAmount = 2790.0,
                isCompleted = false
            )
        )
        repository.insertPeriods(periods)

        // Assert
        val savedPeriods = repository.getPeriodsByFlowId(flowId).first()
        assertEquals("Должно быть 2 периода", 2, savedPeriods.size)

        // Период 1: startDay=1 -> day2=15 -> 15.04.2026
        assertEquals("Период 1 должен закончиться 15.04.2026",
            createDateMillis(2026, Calendar.APRIL, 15), savedPeriods[0].endDate)
        // Период 2: day1=1 -> 01.05.2026
        assertEquals("Период 2 должен закончиться 01.05.2026",
            createDateMillis(2026, Calendar.MAY, 1), savedPeriods[1].endDate)
    }

    /**
     * Проверка: getCurrentPeriod возвращает незавершённый период.
     */
    @Test
    fun getCurrentPeriod_returnsIncompletePeriod() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        repository.insertPeriod(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act
        val current = repository.getCurrentPeriod(flowId)

        // Assert
        assertTrue("Должен быть текущий период", current != null)
        assertEquals("Текущий период должен быть 1", 1, current!!.periodNumber)
        assertFalse("Период не должен быть завершён", current.isCompleted)
    }

    /**
     * Проверка: getPeriodByNumber возвращает период по номеру.
     */
    @Test
    fun getPeriodByNumber_returnsCorrectPeriod() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        repository.insertPeriod(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act
        val period = repository.getPeriodByNumber(flowId, 1)

        // Assert
        assertTrue("Должен быть период 1", period != null)
        assertEquals("Процент должен быть 30%", 30.0, period!!.percent, 0.01)
    }

    /**
     * Проверка: updatePeriod обновляет период.
     */
    @Test
    fun updatePeriod_updatesExistingPeriod() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        val periodId = repository.insertPeriod(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act - обновляем период
        val period = repository.getPeriodByNumber(flowId, 1)!!
        repository.updatePeriod(period.copy(isCompleted = true))

        // Assert
        val updated = repository.getPeriodByNumber(flowId, 1)!!
        assertTrue("Период должен быть завершён", updated.isCompleted)
    }

    /**
     * Проверка: deleteFlow удаляет поток и его периоды.
     */
    @Test
    fun deleteFlow_removesFlowAndPeriods() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        repository.insertPeriod(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act
        repository.deleteFlow(flowId)

        // Assert
        assertEquals("Поток должен быть удалён", 0, repository.getFlowsCount())
        val periods = repository.getPeriodsByFlowId(flowId).first()
        assertTrue("Периоды должны быть удалены", periods.isEmpty())
    }

    /**
     * Проверка: clearAll очищает все потоки и периоды.
     */
    @Test
    fun clearAll_removesAllFlowsAndPeriods() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        repository.insertPeriod(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = calculatePspPeriodEndDate(startDate, 1),
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act
        repository.clearAll()

        // Assert
        assertEquals("Потоки должны быть очищены", 0, repository.getFlowsCount())
        val periods = repository.getPeriodsByFlowId(flowId).first()
        assertTrue("Периоды должны быть очищены", periods.isEmpty())
    }

    /**
     * Проверка: updateFlow обновляет поток.
     */
    @Test
    fun updateFlow_updatesExistingFlow() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = repository.insertFlow(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        // Act
        val flow = repository.getFlowById(flowId)!!
        repository.updateFlow(flow.copy(currentPeriod = 2, totalAccrued = 1500.0))

        // Assert
        val updated = repository.getFlowById(flowId)!!
        assertEquals("Текущий период должен быть 2", 2, updated.currentPeriod)
        assertEquals("Всего получено должно быть 1500", 1500.0, updated.totalAccrued, 0.01)
    }

    private fun createDateMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
