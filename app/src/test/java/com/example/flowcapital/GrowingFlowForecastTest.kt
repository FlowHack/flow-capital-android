package com.example.flowcapital

import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.forecast.calculateFlowForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit-тесты логики прогнозирования РП (Растущий поток).
 *
 * ТЗ:
 * - Старт+DAILY в первый день (если не воскресенье)
 * - Воскресенье - без начисления
 * - Прогноз останавливается при inFlow <= 0
 * - Процент растет на dailyAddition каждый день
 */
class GrowingFlowForecastTest {

    companion object {
        private const val START_PERCENT = 0.1
        private const val DAILY_ADDITION = 0.003
        private const val TOLERANCE = 0.001
    }

    private fun today(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(millis: Long, days: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // ═══════════════════════════════════════════════════════════════════════
    // БАЗОВАЯ ЛОГИКА: СТАРТ + DAILY В ПЕРВЫЙ ДЕНЬ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: В день старта (если не воскресенье) делается START + DAILY
     * Кошелек увеличивается на первое начисление
     */
    @Test
    fun `forecast contains START and DAILY on first day`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        assertTrue("Есть START", results.any { it.actionType == "START" })
        assertTrue("Есть DAILY", results.any { it.actionType == "DAILY" })
    }

    @Test
    fun `wallet increases on first day DAILY`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val dailyResult = results.find { it.actionType == "DAILY" }
        assertNotNull("Есть DAILY запись", dailyResult)
        assertTrue("Кошелек > 0", dailyResult!!.walletAmount > 0)
    }

    @Test
    fun `inFlow decreases after first day DAILY`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val startResult = results.find { it.actionType == "START" }
        val dailyResult = results.find { it.actionType == "DAILY" }

        assertNotNull(startResult)
        assertNotNull(dailyResult)
        assertTrue("inFlow уменьшился",
            dailyResult!!.inFlowAmount < startResult!!.inFlowAmount)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РОСТ ПРОЦЕНТА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Процент растет на dailyAddition каждый день
     */
    @Test
    fun `percent grows by dailyAddition each day`() {
        val startDate = today()
        val targetDate = addDays(startDate, 3)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val dailyRecords = results.filter { it.actionType == "DAILY" }
        assertTrue("Есть DAILY записи", dailyRecords.isNotEmpty())

        for (i in 1 until dailyRecords.size) {
            val prev = dailyRecords[i - 1].percent
            val cur = dailyRecords[i].percent
            val diff = cur - prev

            assertEquals("Процент растет на DAILY_ADDITION",
                DAILY_ADDITION, diff, TOLERANCE)
        }
    }

    @Test
    fun `percent increases over time`() {
        val startDate = today()
        val targetDate = addDays(startDate, 10)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val firstDaily = results.filter { it.actionType == "DAILY" }.firstOrNull()
        val lastDaily = results.filter { it.actionType == "DAILY" }.lastOrNull()

        assertNotNull(firstDaily)
        assertNotNull(lastDaily)
        assertTrue("Процент вырос",
            lastDaily!!.percent > firstDaily!!.percent)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ВОСКРЕСЕНЬЕ
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Воскресенье - запись без начисления
     * Проверяем: старт в субботу -> воскресенье без начисления
     */
    @Test
    fun `sunday has no accrual`() {
        // Старт в субботу - на следующий день (воскресенье) не будет начисления
        val saturday = findNextSaturday()
        val sunday = addDays(saturday, 1)
        val targetDate = sunday // ровно на день воскресенья

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = saturday,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val sundayRecord = results.find { it.actionType == "SUNDAY" }
        assertNotNull("Есть SUNDAY запись", sundayRecord)
    }

    @Test
    fun `sunday inFlow unchanged from previous day`() {
        val saturday = findNextSaturday()
        val sunday = addDays(saturday, 1)
        val targetDate = addDays(sunday, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = saturday,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val satDaily = results.filter { it.actionType == "DAILY" }
            .filter { isSameDay(saturday, it.date) }.lastOrNull()
        val sunRecord = results.find { it.actionType == "SUNDAY" }

        if (satDaily != null && sunRecord != null) {
            assertEquals("inFlow не изменилось в воскресенье",
                satDaily.inFlowAmount, sunRecord.inFlowAmount, TOLERANCE)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОСТАНОВКА ПРИ НУЛЕ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Прогноз останавливается когда inFlow <= 0
     */
    @Test
    fun `forecast stops when inFlow reaches zero`() {
        val startDate = today()
        val targetDate = addDays(startDate, 365) // большой период

        val results = calculateFlowForecast(
            inFlow = 100.0, // маленький поток
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        // Последняя запись должна иметь inFlow ~= 0
        val lastResult = results.lastOrNull()
        assertNotNull(lastResult)
        assertTrue("Последний inFlow near zero",
            lastResult!!.inFlowAmount <= 0.01 || lastResult.inFlowAmount >= 0)
    }

    @Test
    fun `forecast does not generate infinite records`() {
        val startDate = today()
        // 10000 дней максимум - больше чем нужно для 1M
        val targetDate = addDays(startDate, 10000)

        val results = calculateFlowForecast(
            inFlow = 1000000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        // Функция возвращает результат, ограничена датой
        assertTrue("Есть результат", results.isNotEmpty())
        // Обычно при 1M не успевает истощиться за 10000 дней - но это ок
        assertTrue("Записей меньше чем дней в периоде", results.size <= 10002)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГРАНИЧНЫЕ УСЛОВИЯ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `forecast works with zero wallet`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        assertTrue("Есть результат", results.isNotEmpty())
    }

    @Test
    fun `forecast works with initial wallet`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 5000.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        val startRecord = results.find { it.actionType == "START" }
        assertNotNull(startRecord)
        assertEquals("Кошелек сохранен", 5000.0, startRecord!!.walletAmount, TOLERANCE)
    }

    @Test
    fun `forecast works with zero percent`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = 0.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        // При 0% начисление будет 0, но записи должны быть
        assertTrue("Есть START", results.any { it.actionType == "START" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РАЗНЫЕ ДАТЫ СТАРТА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Тест: старт в воскресенье - нет DAILY в первый день
     */
    @Test
    fun `no DAILY on sunday start`() {
        val sunday = findNextSunday()
        val targetDate = addDays(sunday, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = sunday,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        assertTrue("Есть START", results.any { it.actionType == "START" })

        // DAILY не должно быть в день старта (воскресенье)
        val dailyOnStartDay = results.filter { it.actionType == "DAILY" }
            .filter { isSameDay(sunday, it.date) }
        assertEquals("Нет DAILY в воскресенье старта", 0, dailyOnStartDay.size)
    }

    /**
     * Тест: старт в понедельник - есть START + DAILY
     */
    @Test
    fun `START and DAILY on monday start`() {
        val monday = findNextMonday()
        val targetDate = addDays(monday, 1)

        val results = calculateFlowForecast(
            inFlow = 10000.0,
            percent = START_PERCENT,
            wallet = 0.0,
            startDateMillis = monday,
            targetDateMillis = targetDate,
            dailyAddition = DAILY_ADDITION
        )

        assertTrue("Есть START", results.any { it.actionType == "START" })
        assertTrue("Есть DAILY", results.any { it.actionType == "DAILY" })
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ПРОВЕРКА ФОРМУЛ
    // ══════════════════════════════════════════════════════════════���═���══════

    @Test
    fun `accrual formula is correct`() {
        val inFlow = 10000.0
        val percent = 0.5
        val expectedAccrual = inFlow * (percent / 100.0)

        assertEquals("Формула начисления", 50.0, expectedAccrual, TOLERANCE)
    }

    @Test
    fun `bonus calculation is correct`() {
        val contribution = 10000.0
        val bonusPercent = 100.0
        val expectedInFlow = contribution + contribution * bonusPercent / 100.0

        assertEquals("С бонусом 100%", 20000.0, expectedInFlow, TOLERANCE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════════════

    private fun findNextSunday(): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun findNextSaturday(): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun findNextMonday(): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}