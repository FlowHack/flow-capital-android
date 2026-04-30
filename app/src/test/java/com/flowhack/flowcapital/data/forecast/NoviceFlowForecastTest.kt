package com.flowhack.flowcapital.data.forecast

import com.flowhack.flowcapital.data.db.NoviceFlowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Тесты для calculateNoviceFlowForecast (Поток Новичка - ПН).
 * Проверяют логику согласно ТЗ:
 * - Фиксированный ежедневный процент (не растет)
 * - Обработка воскресений (SUNDAY)
 * - Сложный процент (реинвест при накоплении)
 * - Логика действующего потока (isExistingFlow)
 * - Остановка при inFlow <= 0
 */
class NoviceFlowForecastTest {

    /**
     * Проверка: Старт потока создает PN_START запись.
     * Согласно ТЗ: "Старт: записывается с процентом, в потоке, начислением, кошельком"
     */
    @Test
    fun start_newFlow_createsPnStartRecord() {
        // Arrange
        val inFlow = 5000.0
        val dailyPercent = 2.0
        val wallet = 0.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = inFlow,
            dailyPercent = dailyPercent,
            wallet = wallet,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        assertTrue("Должны быть записи (START + возможно DAILY)", result.isNotEmpty())
        assertEquals("Первая запись должна быть PN_START", "PN_START", result[0].actionType)
        assertEquals("Процент должен быть равен dailyPercent", dailyPercent, result[0].percent, 0.01)
        assertEquals("В потоке должно быть 5000", 5000.0, result[0].inFlowAmount, 0.01)
        // При старте в четверг (не воскресенье) должно быть 2 записи: PN_START и PN_DAILY
        assertEquals("Должно быть 2 записи (START + DAILY)", 2, result.size)
    }

    /**
     * Проверка: В день старта (не воскресенье) сразу происходит начисление (PN_DAILY).
     * Согласно ТЗ: "Если не воскресенье: в день старта СРАЗУ делается начисление"
     */
    @Test
    fun start_newFlowOnWeekday_createsDailyOnSameDay() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = 5000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        val dailyRecord = result.find { it.actionType == "PN_DAILY" }
        assertTrue("Должна быть запись PN_DAILY в день старта", dailyRecord != null)
        assertEquals("PN_DAILY должен быть в тот же день, что и START", startDateMillis, dailyRecord!!.date)
        // Процент фиксированный - не растет
        assertEquals("Процент должен оставаться 2.0", 2.0, dailyRecord.percent, 0.01)
    }

    /**
     * Проверка: В воскресенье начисление не происходит, создается SUNDAY.
     * Согласно ТЗ: "Воскресенье: SUNDAY запись без начисления"
     */
    @Test
    fun start_newFlowOnSunday_createsSundayRecord() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 18) // Воскресенье
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 18)

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = 5000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        // Должно быть 2 записи: PN_START и SUNDAY (так как старт в воскресенье)
        assertEquals("Должно быть 2 записи (PN_START + SUNDAY)", 2, result.size)
        assertEquals("Первая запись должна быть PN_START", "PN_START", result[0].actionType)
        val sundayRecord = result.find { it.actionType == "SUNDAY" }
        assertTrue("В воскресенье должна быть запись SUNDAY", sundayRecord != null)
        assertEquals("SUNDAY должен быть в тот же день, что и START", startDateMillis, sundayRecord!!.date)

        // Проверяем следующий день (понедельник) - должен быть PN_DAILY
        val nextDay = createDateMillis(2026, Calendar.JANUARY, 19)
        val resultWithNextDay = calculateNoviceFlowForecast(
            inFlow = 5000.0,
            dailyPercent = 2.0,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = nextDay,
            isExistingFlow = false
        )
        val dailyRecord = resultWithNextDay.find { it.actionType == "PN_DAILY" }
        assertTrue("В понедельник должна быть запись PN_DAILY", dailyRecord != null)
    }

    /**
     * Проверка: Процент фиксированный (не растет как в РП).
     * Согласно ТЗ: "Процент фиксированный (не растет как в РП)"
     */
    @Test
    fun daily_percentRemainsFixed() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота (3 дня)
        val dailyPercent = 2.0

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = 10000.0, // Достаточно для всех начислений
            dailyPercent = dailyPercent,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        val dailyRecords = result.filter { it.actionType == "PN_DAILY" }
        assertTrue("Должно быть минимум 2 PN_DAILY записи", dailyRecords.size >= 2)
        // Все PN_DAILY должны иметь одинаковый процент (фиксированный)
        dailyRecords.forEach { record ->
            assertEquals("Процент должен оставаться фиксированным: $dailyPercent",
                dailyPercent, record.percent, 0.01)
        }
    }

    /**
     * Проверка: Сложный процент - реинвест при накоплении кошелька.
     * Согласно ТЗ: "Сложный процент: при compoundInterest=true, когда wallet >= reinvestAmount,
     * происходит реинвест (кошелек переходит в поток) в тот же день"
     */
    @Test
    fun compoundInterest_reinvestWhenWalletReachesThreshold() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        // Делаем период на несколько дней, чтобы накопить на реинвест
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 20) // Вторник
        val dailyPercent = 2.0
        val reinvestAmount = 2000.0
        // inFlow достаточно большой, чтобы начисления быстро накопились
        val inFlow = 100000.0

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = inFlow,
            dailyPercent = dailyPercent,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false,
            compoundInterest = true,
            reinvestAmount = reinvestAmount,
            bonusPercent = 50.0
        )

        // Assert
        val reinvestRecords = result.filter { it.actionType == "PN_REINVEST" }
        assertTrue("При compoundInterest=true должны быть записи PN_REINVEST", reinvestRecords.isNotEmpty())

        // Проверяем, что при реинвесте кошелек обнуляется, а inFlow растет
        if (reinvestRecords.isNotEmpty()) {
            val reinvestRecord = reinvestRecords.first()
            assertEquals("После реинвеста кошелек должен быть 0", 0.0, reinvestRecord.walletAmount, 0.01)
            assertTrue("После реинвеста inFlow должен вырасти", reinvestRecord.inFlowAmount > inFlow)
        }
    }

    /**
     * Проверка: Для действующего потока (isExistingFlow=true) DAILY начинается только со следующего дня.
     * Согласно ТЗ: "Для действующего потока: DAILY начинается только со следующего дня"
     */
    @Test
    fun existingFlow_dailyStartsNextDay() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Только день старта

        // Act - новый поток
        val newFlowResult = calculateNoviceFlowForecast(
            inFlow = 5000.0,
            dailyPercent = 2.0,
            wallet = 100.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Act - действующий поток
        val existingFlowResult = calculateNoviceFlowForecast(
            inFlow = 5000.0,
            dailyPercent = 2.0,
            wallet = 100.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = true
        )

        // Assert
        assertTrue("Новый поток: должна быть запись PN_DAILY в день старта",
            newFlowResult.any { it.actionType == "PN_DAILY" && it.date == startDateMillis })
        assertTrue("Действующий поток: НЕ должно быть PN_DAILY в день старта",
            existingFlowResult.none { it.actionType == "PN_DAILY" && it.date == startDateMillis })
        assertEquals("Действующий поток: только PN_START", 1, existingFlowResult.size)
    }

    /**
     * Проверка: Начисление рассчитывается как inFlow * (dailyPercent / 100).
     */
    @Test
    fun accrual_calculatedCorrectly() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = 1000.0,
            dailyPercent = 2.0, // 2%
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        val dailyRecord = result.find { it.actionType == "PN_DAILY" }
        val expectedAccrual = 1000.0 * (2.0 / 100.0) // 20.0
        assertEquals("Начисление должно быть 20.0", expectedAccrual, dailyRecord!!.dailyAccrual, 0.01)
    }

    /**
     * Проверка: Прогноз останавливается при inFlow <= 0.
     * Согласно ТЗ: "Прогноз останавливается при inFlow <= 0"
     */
    @Test
    fun forecast_stopsWhenInFlowBecomesZero() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        // Только 2 дня для проверки
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 16)
        // inFlow=0.02, 100% начисление -> после первого начисления inFlow станет 0
        val inFlow = 0.02
        val dailyPercent = 100.0 // 100% чтобы сразу обнулить

        // Act
        val result = calculateNoviceFlowForecast(
            inFlow = inFlow,
            dailyPercent = dailyPercent,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            isExistingFlow = false
        )

        // Assert
        val lastRecord = result.last()
        assertTrue("Последняя запись должна иметь inFlow <= 0.01", lastRecord.inFlowAmount <= 0.01)
    }

    /**
     * Вспомогательная функция для создания timestamp определенной даты.
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
