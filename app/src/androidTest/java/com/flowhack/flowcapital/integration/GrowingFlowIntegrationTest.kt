package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.GrowingFlowRepository
import com.flowhack.flowcapital.data.forecast.calculateFlowForecast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Растущего Потока (РП).
 * Проверяют реальную связку: forecast-функция -> репозиторий -> БД (Room).
 */
class GrowingFlowIntegrationTest : BaseIntegrationTest() {

    private lateinit var repository: GrowingFlowRepository

    override fun setUp() {
        super.setUp()
        repository = GrowingFlowRepository(growingFlowDao)
    }

    /**
     * Проверка: Прогноз РП сохраняется в БД через репозиторий.
     */
    @Test
    fun forecast_savesAllRecordsToDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота

        // Act - генерируем прогноз и сохраняем через репозиторий
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Assert
        val saved = repository.allHistory.first()
        assertEquals("Все записи прогноза должны сохраниться", forecast.size, saved.size)
        assertEquals("Первая запись должна быть START", "START", saved.last().actionType)
    }

    /**
     * Проверка: getLastEntry возвращает последнюю запись.
     */
    @Test
    fun getLastEntry_returnsMostRecentRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val lastEntry = repository.getLastEntry()

        // Assert
        assertTrue("Должна быть последняя запись", lastEntry != null)
        assertEquals("Последняя запись должна быть DAILY", "DAILY", lastEntry!!.actionType)
    }

    /**
     * Проверка: getEntriesForDateRange возвращает записи за диапазон дат.
     */
    @Test
    fun getEntriesForDateRange_returnsRecordsInRange() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act - диапазон только 15.01
        val rangeStart = createDateMillis(2026, Calendar.JANUARY, 15)
        val rangeEnd = createDateMillis(2026, Calendar.JANUARY, 16)
        val inRange = repository.getEntriesForDateRange(rangeStart, rangeEnd)

        // Assert
        assertTrue("Должны быть записи за 15.01", inRange.isNotEmpty())
        inRange.forEach { record ->
            assertTrue("Запись должна быть в диапазоне", record.date >= rangeStart && record.date < rangeEnd)
        }
    }

    /**
     * Проверка: getLastEntryBeforeDate возвращает запись до указанной даты.
     */
    @Test
    fun getLastEntryBeforeDate_returnsRecordBeforeDate() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act - запись до 16.01
        val beforeDate = createDateMillis(2026, Calendar.JANUARY, 16)
        val lastBefore = repository.getLastEntryBeforeDate(beforeDate)

        // Assert
        assertTrue("Должна быть запись до 16.01", lastBefore != null)
        assertTrue("Запись должна быть раньше указанной даты", lastBefore!!.date < beforeDate)
    }

    /**
     * Проверка: getFirstStartEntry возвращает первую START запись.
     */
    @Test
    fun getFirstStartEntry_returnsFirstStartRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val firstStart = repository.getFirstStartEntry()

        // Assert
        assertTrue("Должна быть START запись", firstStart != null)
        assertEquals("Тип должен быть START", "START", firstStart!!.actionType)
        assertEquals("Дата должна быть датой старта", startDate, firstStart.date)
    }

    /**
     * Проверка: getLastPressEntry возвращает последнюю запись с нажатой кнопкой.
     */
    @Test
    fun getLastPressEntry_returnsLastPressedRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val lastPress = repository.getLastPressEntry()

        // Assert
        assertTrue("Должна быть запись с нажатой кнопкой", lastPress != null)
        assertTrue("Кнопка должна быть нажата", lastPress!!.isButtonPressed)
        assertEquals("Тип должен быть DAILY", "DAILY", lastPress.actionType)
    }

    /**
     * Проверка: clearHistory очищает все записи.
     */
    @Test
    fun clearHistory_removesAllRecords() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        repository.clearHistory()

        // Assert
        val saved = repository.allHistory.first()
        assertTrue("После очистки не должно быть записей", saved.isEmpty())
    }

    /**
     * Проверка: updateEntry обновляет существующую запись.
     */
    @Test
    fun updateEntry_updatesExistingRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            dailyAddition = 0.003,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act - обновляем последнюю запись
        val lastEntry = repository.getLastEntry()!!
        val updated = lastEntry.copy(walletAmount = 999.0)
        repository.updateEntry(updated)

        // Assert
        val saved = repository.getLastEntry()!!
        assertEquals("Кошелек должен обновиться", 999.0, saved.walletAmount, 0.01)
    }

    /**
     * Вспомогательная функция для создания timestamp.
     */
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
