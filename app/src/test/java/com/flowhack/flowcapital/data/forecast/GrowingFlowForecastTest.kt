package com.flowhack.flowcapital.data.forecast

import com.flowhack.flowcapital.data.db.GrowingFlowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Тесты для calculateFlowForecast (Растущий поток - РП).
 * Проверяют логику согласно ТЗ:
 * - Старт с начальным процентом
 * - Рост процента на dailyAddition при каждом нажатии
 * - Отсутствие начислений в воскресенье (SUNDAY)
 * - Остановка при inFlow <= 0
 * - Логика действующего потока (isExistingFlow)
 */
class GrowingFlowForecastTest {

    /**
     * Проверка: Старт потока создает запись START с правильными значениями.
     * Согласно ТЗ: "Старт: записывается с процентом, в потоке, начислением, кошельком"
     */
    @Test
    fun start_newFlow_createsStartRecordWithCorrectValues() {
        // Arrange
        val inFlow = 1000.0
        val percent = 0.1
        val wallet = 0.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val dailyAddition = 0.003

        // Act
        val result = calculateFlowForecast(
            inFlow = inFlow,
            percent = percent,
            wallet = wallet,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = dailyAddition,
            isExistingFlow = false
        )

        // Assert
        assertTrue("Должно быть минимум 1 запись (START)", result.isNotEmpty())
        assertEquals("Первая запись должна быть START", "START", result[0].actionType)
        assertEquals("Процент START должен быть стартовым", 0.1, result[0].percent, 0.0001)
        assertEquals("В потоке START должно быть 1000", 1000.0, result[0].inFlowAmount, 0.01)
    }

    /**
     * Проверка: В день старта (не воскресенье) сразу происходит начисление (DAILY).
     * Согласно ТЗ: "Если не воскресенье: в день старта СРАЗУ делается начисление"
     */
    @Test
    fun start_newFlowOnWeekday_createsDailyOnSameDay() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг (не воскресенье)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val result = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = false
        )

        // Assert
        val dailyRecord = result.find { it.actionType == "DAILY" }
        assertTrue("Должна быть запись DAILY в день старта", dailyRecord != null)
        assertEquals("DAILY должен быть в тот же день, что и START", startDateMillis, dailyRecord!!.date)
        assertEquals("Процент DAILY должен вырасти на dailyAddition", 0.103, dailyRecord.percent, 0.0001)
    }

    /**
     * Проверка: В воскресенье начисление не происходит, создается SUNDAY.
     * Согласно ТЗ: "Воскресенье: SUNDAY запись без начисления"
     */
    @Test
    fun start_newFlowOnSunday_createsSundayRecord() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 18) // Воскресенье
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 19) // Включая понедельник

        // Act - старт в воскресенье
        val result = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = false
        )

        // Assert
        // Должен быть START (18.01) и SUNDAY (18.01) и DAILY (19.01)
        val startRecord = result.find { it.actionType == "START" && it.date == startDateMillis }
        val sundayRecord = result.find { it.actionType == "SUNDAY" }
        assertTrue("Должна быть запись START", startRecord != null)
        assertTrue("В воскресенье должна быть запись SUNDAY", sundayRecord != null)
        assertEquals("SUNDAY должен быть в воскресенье (18.01)", startDateMillis, sundayRecord!!.date)
    }

    /**
     * Проверка: Процент растет на dailyAddition при каждом нажатии.
     * Согласно ТЗ: "Каждый день (кроме воскресений): кнопка нажимается, процент растет на dailyAddition"
     */
    @Test
    fun dailyAddition_percentGrowsEachDay() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 17) // Суббота (3 дня)
        val dailyAddition = 0.003

        // Act
        val result = calculateFlowForecast(
            inFlow = 10000.0, // Достаточно для всех начислений
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = dailyAddition,
            isExistingFlow = false
        )

        // Assert
        val dailyRecords = result.filter { it.actionType == "DAILY" }
        assertTrue("Должно быть минимум 2 DAILY записи", dailyRecords.size >= 2)
        // Первый DAILY (15.01) - процент 0.1 + 0.003 = 0.103
        assertEquals("Первый DAILY: процент должен быть 0.103", 0.103, dailyRecords[0].percent, 0.0001)
        // Второй DAILY (16.01) - процент 0.103 + 0.003 = 0.106
        assertEquals("Второй DAILY: процент должен быть 0.106", 0.106, dailyRecords[1].percent, 0.0001)
    }

    /**
     * Проверка: Прогноз останавливается при inFlow <= 0.
     * Согласно ТЗ: "Прогноз останавливается при inFlow <= 0"
     */
    @Test
    fun forecast_stopsWhenInFlowBecomesZero() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 16) // Завтра
        val dailyAddition = 0.0

        // Act: 100% начисление, inFlow=0.02 -> после первого начисления inFlow станет 0
        val result = calculateFlowForecast(
            inFlow = 0.02,
            percent = 100.0, // 100% чтобы сразу обнулить
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = dailyAddition,
            isExistingFlow = false
        )

        // Assert
        val lastRecord = result.last()
        assertTrue("Последняя запись должна иметь inFlow <= 0.01", lastRecord.inFlowAmount <= 0.01)
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
        val newFlowResult = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.5,
            wallet = 100.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = false
        )

        // Act - действующий поток
        val existingFlowResult = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.5,
            wallet = 100.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = true
        )

        // Assert
        assertTrue("Новый поток: должна быть запись DAILY в день старта",
            newFlowResult.any { it.actionType == "DAILY" && it.date == startDateMillis })
        assertTrue("Действующий поток: НЕ должно быть DAILY в день старта",
            existingFlowResult.none { it.actionType == "DAILY" && it.date == startDateMillis })
        assertEquals("Действующий поток: только START", 1, existingFlowResult.size)
    }

    /**
     * Проверка: Начисление правильно рассчитывается как inFlow * (percent / 100).
     */
    @Test
    fun accrual_calculatedCorrectly() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val result = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 10.0, // 10%
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.0, // Без роста для простоты
            isExistingFlow = false
        )

        // Assert
        val dailyRecord = result.find { it.actionType == "DAILY" }
        val expectedAccrual = 1000.0 * (10.0 / 100.0) // 100.0
        assertEquals("Начисление должно быть 100.0", expectedAccrual, dailyRecord!!.dailyAccrual, 0.01)
    }

    /**
     * Проверка: В основном цикле воскресенье создаёт SUNDAY без начисления.
     * SUNDAY сохраняет текущие значения (процент, поток, начисление), но кнопка не нажата.
     */
    @Test
    fun sundayInMainLoop_createsSundayWithoutAccrual() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 18) // Воскресенье
        val dailyAddition = 0.003

        // Act
        val result = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = dailyAddition,
            isExistingFlow = false
        )

        // Assert
        val sundayRecord = result.find { it.actionType == "SUNDAY" && it.date == targetDateMillis }
        assertTrue("В воскресенье в основном цикле должна быть SUNDAY", sundayRecord != null)
        assertTrue("Кнопка в воскресенье не должна быть нажата", !sundayRecord!!.isButtonPressed)
        // Процент не должен расти в воскресенье (SUNDAY сохраняет текущий процент).
        val lastDaily = result.filter { it.actionType == "DAILY" }.lastOrNull()
        if (lastDaily != null) {
            assertEquals("Процент не должен расти в воскресенье", lastDaily.percent, sundayRecord.percent, 0.0001)
        }
    }

    /**
     * Проверка: Ветка minOf(inFlow, accrual) - когда начисление больше остатка потока.
     * Начисление ограничивается остатком inFlow.
     */
    @Test
    fun accrualGreaterThanRemainingInFlow_limitsAccrualToInFlow() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val dailyAddition = 0.0

        // Act: inFlow=10, percent=100% -> начисление 10, остаток станет 0
        val result = calculateFlowForecast(
            inFlow = 10.0,
            percent = 100.0,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = dailyAddition,
            isExistingFlow = false
        )

        // Assert
        val dailyRecord = result.find { it.actionType == "DAILY" }
        assertTrue("Должна быть DAILY запись", dailyRecord != null)
        // Начисление не может превысить остаток потока.
        assertEquals("Начисление должно быть ограничено остатком", 10.0, dailyRecord!!.dailyAccrual, 0.01)
        // После начисления inFlow становится 0.
        assertEquals("inFlow после начисления должен быть 0", 0.0, dailyRecord.inFlowAmount, 0.01)
    }

    /**
     * Проверка: Вход с inFlow=0 - создаётся START и DAILY с нулевым начислением.
     * START создаётся всегда; на будний день при !isExistingFlow дополнительно создаётся DAILY.
     */
    @Test
    fun zeroInFlow_createsStartAndDailyWithZeroAccrual() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 16)

        // Act
        val result = calculateFlowForecast(
            inFlow = 0.0,
            percent = 0.1,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = false
        )

        // Assert
        assertEquals("Должны быть записи START и DAILY", 2, result.size)
        assertEquals("Первая запись должна быть START", "START", result[0].actionType)
        assertEquals("Начисление START должно быть 0", 0.0, result[0].dailyAccrual, 0.0001)
        assertEquals("Вторая запись должна быть DAILY", "DAILY", result[1].actionType)
        assertEquals("Начисление DAILY должно быть 0", 0.0, result[1].dailyAccrual, 0.0001)
        assertEquals("Поток должен остаться 0", 0.0, result[1].inFlowAmount, 0.0001)
    }

    /**
     * Проверка: Вход с percent=0 - первое начисление равно 0, но процент растёт.
     * DAILY создаётся, но с нулевым начислением; процент увеличивается на dailyAddition.
     */
    @Test
    fun zeroPercent_firstAccrualIsZeroButPercentGrows() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 15)
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 16)

        // Act
        val result = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.0,
            wallet = 0.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = false
        )

        // Assert
        val dailyRecords = result.filter { it.actionType == "DAILY" }
        assertTrue("Должна быть DAILY запись", dailyRecords.isNotEmpty())
        assertEquals("Первое начисление должно быть 0", 0.0, dailyRecords[0].dailyAccrual, 0.0001)
        // Процент растёт на dailyAddition даже при стартовом percent=0.
        assertEquals("Процент должен вырасти на dailyAddition", 0.003, dailyRecords[0].percent, 0.0001)
    }

    /**
     * Проверка: Действующий поток (isExistingFlow=true) в воскресенье - только START и SUNDAY.
     */
    @Test
    fun existingFlowOnSunday_createsStartAndSunday() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 18) // Воскресенье
        val targetDateMillis = createDateMillis(2026, Calendar.JANUARY, 18)

        // Act
        val result = calculateFlowForecast(
            inFlow = 1000.0,
            percent = 0.5,
            wallet = 100.0,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            dailyAddition = 0.003,
            isExistingFlow = true
        )

        // Assert
        assertTrue("Должна быть START запись", result.any { it.actionType == "START" })
        assertTrue("В воскресенье должна быть SUNDAY", result.any { it.actionType == "SUNDAY" })
        assertTrue("Не должно быть DAILY для действующего потока в день старта",
            result.none { it.actionType == "DAILY" })
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
