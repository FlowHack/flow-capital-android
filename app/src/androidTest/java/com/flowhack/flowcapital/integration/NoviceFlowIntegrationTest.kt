package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.NoviceFlowRepository
import com.flowhack.flowcapital.data.forecast.calculateNoviceFlowForecast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Потока Новичка (ПН).
 * Проверяют реальную связку: forecast-функция -> репозиторий -> БД (Room).
 */
class NoviceFlowIntegrationTest : BaseIntegrationTest() {

    private lateinit var repository: NoviceFlowRepository

    override fun setUp() {
        super.setUp()
        repository = NoviceFlowRepository(noviceFlowDao)
    }

    /**
     * Проверка: Прогноз ПН сохраняется в БД через репозиторий.
     */
    @Test
    fun forecast_savesAllRecordsToDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота

        // Act - генерируем прогноз и сохраняем через репозиторий
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Assert
        val saved = repository.allHistory.first()
        assertEquals("Все записи прогноза должны сохраниться", forecast.size, saved.size)
        assertEquals("Первая запись должна быть PN_START", "PN_START", saved.last().actionType)
    }

    /**
     * Проверка: getLastEntry возвращает последнюю запись.
     */
    @Test
    fun getLastEntry_returnsMostRecentRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val lastEntry = repository.getLastEntry()

        // Assert
        assertTrue("Должна быть последняя запись", lastEntry != null)
        assertEquals("Последняя запись должна быть PN_DAILY", "PN_DAILY", lastEntry!!.actionType)
    }

    /**
     * Проверка: getAllEntries возвращает все записи.
     */
    @Test
    fun getAllEntries_returnsAllRecords() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val allEntries = repository.getAllEntries()

        // Assert
        assertEquals("Должны быть все записи", forecast.size, allEntries.size)
    }

    /**
     * Проверка: getEntriesForDateRange возвращает записи за диапазон дат.
     */
    @Test
    fun getEntriesForDateRange_returnsRecordsInRange() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDate = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
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
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
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
     * Проверка: getFirstStartEntry возвращает первую PN_START запись.
     */
    @Test
    fun getFirstStartEntry_returnsFirstStartRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val firstStart = repository.getFirstStartEntry()

        // Assert
        assertTrue("Должна быть PN_START запись", firstStart != null)
        assertEquals("Тип должен быть PN_START", "PN_START", firstStart!!.actionType)
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
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            isExistingFlow = false
        )
        forecast.forEach { repository.insertEntry(it) }

        // Act
        val lastPress = repository.getLastPressEntry()

        // Assert
        assertTrue("Должна быть запись с нажатой кнопкой", lastPress != null)
        assertTrue("Кнопка должна быть нажата", lastPress!!.isButtonPressed)
        assertEquals("Тип должен быть PN_DAILY", "PN_DAILY", lastPress.actionType)
    }

    /**
     * Проверка: clearHistory очищает все записи.
     */
    @Test
    fun clearHistory_removesAllRecords() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
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
        val forecast = calculateNoviceFlowForecast(
            inFlow = 10000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = startDate,
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
