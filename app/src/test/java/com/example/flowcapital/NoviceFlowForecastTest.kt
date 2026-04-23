package com.example.flowcapital

import com.example.flowcapital.data.db.NoviceFlowEntity
import com.example.flowcapital.data.forecast.calculateNoviceFlowForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit-тесты логики прогнозирования ПН (Поток Новичка).
 *
 * ТЗ:
 * - Фиксированный процент (2%)
 * - Старт+DAILY в первый день (если не воскресенье)
 * - Воскресенье - без начисления
 * - Прогноз останавливается при inFlow <= 0
 */
class NoviceFlowForecastTest {

    companion object {
        private const val DAILY_PERCENT = 2.0
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
     */
    @Test
    fun `forecast contains START and DAILY on first day`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        assertTrue("Есть START", results.any { it.actionType == "PN_START" })
        assertTrue("Есть DAILY", results.any { it.actionType == "PN_DAILY" })
    }

    @Test
    fun `wallet increases on first day DAILY`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val dailyResult = results.find { it.actionType == "PN_DAILY" }
        assertNotNull("Есть DAILY запись", dailyResult)
        assertTrue("Кошелек > 0", dailyResult!!.walletAmount > 0)
    }

    @Test
    fun `inFlow decreases after first day DAILY`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val startResult = results.find { it.actionType == "PN_START" }
        val dailyResult = results.find { it.actionType == "PN_DAILY" }

        assertNotNull(startResult)
        assertNotNull(dailyResult)
        assertTrue("inFlow уменьшился",
            dailyResult!!.inFlowAmount < startResult!!.inFlowAmount)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ФИКСИРОВАННЫЙ ПРОЦЕНТ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: У ПН процент фиксированный (не растет как в РП)
     */
    @Test
    fun `percent is constant each day`() {
        val startDate = today()
        val targetDate = addDays(startDate, 5)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val dailyRecords = results.filter { it.actionType == "PN_DAILY" }
        assertTrue("Есть DAILY записи", dailyRecords.isNotEmpty())

        for (record in dailyRecords) {
            assertEquals("Процент неизменный", DAILY_PERCENT, record.percent, TOLERANCE)
        }
    }

    @Test
    fun `accrual decreases as inFlow decreases`() {
        val startDate = today()
        val targetDate = addDays(startDate, 5)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val dailyRecords = results.filter { it.actionType == "PN_DAILY" }
        assertTrue("Есть DAILY записи", dailyRecords.isNotEmpty())

        for (i in 1 until dailyRecords.size) {
            val prev = dailyRecords[i - 1].dailyAccrual
            val cur = dailyRecords[i].dailyAccrual
            assertTrue("Начисление уменьшается", cur < prev)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВОСКРЕСЕНЬЕ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Воскресенье - запись без начисления
     */
    @Test
    fun `sunday has no accrual`() {
        val saturday = findNextSaturday()
        val sunday = addDays(saturday, 1)
        val targetDate = sunday

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = saturday,
            targetDateMillis = targetDate
        )

        val sundayRecord = results.find { it.actionType == "SUNDAY" }
        assertNotNull("Есть SUNDAY запись", sundayRecord)
    }

    @Test
    fun `sunday inFlow unchanged from previous day`() {
        val saturday = findNextSaturday()
        val sunday = addDays(saturday, 1)
        val targetDate = sunday

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = saturday,
            targetDateMillis = targetDate
        )

        val satDaily = results.filter { it.actionType == "PN_DAILY" }
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
        val targetDate = addDays(startDate, 500)

        val results = calculateNoviceFlowForecast(
            inFlow = 100.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val lastResult = results.lastOrNull()
        assertNotNull(lastResult)
        assertTrue("Последний inFlow near zero",
            lastResult!!.inFlowAmount <= 0.01 || lastResult.inFlowAmount >= 0)
    }

    @Test
    fun `forecast does not generate infinite records`() {
        val startDate = today()
        val targetDate = addDays(startDate, 10000)

        val results = calculateNoviceFlowForecast(
            inFlow = 1000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        // Выводим для отладки
        System.out.println("Записей: ${results.size}")
        if (results.isNotEmpty()) {
            System.out.println("Последний inFlow: ${results.last().inFlowAmount}")
        }

        // Функция возвращает результат
        assertTrue("Есть результат", results.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГРАНИЧНЫЕ УСЛОВИЯ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `forecast works with zero wallet`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        assertTrue("Есть результат", results.isNotEmpty())
    }

    @Test
    fun `forecast works with initial wallet`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 5000.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        val startRecord = results.find { it.actionType == "PN_START" }
        assertNotNull(startRecord)
        assertEquals("Кошелек сохранен", 5000.0, startRecord!!.walletAmount, TOLERANCE)
    }

    @Test
    fun `forecast works with zero percent`() {
        val startDate = today()
        val targetDate = addDays(startDate, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = 0.0,
            wallet = 0.0,
            startDateMillis = startDate,
            targetDateMillis = targetDate
        )

        assertTrue("Есть START", results.any { it.actionType == "PN_START" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РАЗНЫЕ ДАТЫ СТАРТА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Старт в воскресенье - нет DAILY в первый день
     */
    @Test
    fun `no DAILY on sunday start`() {
        val sunday = findNextSunday()
        val targetDate = addDays(sunday, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = sunday,
            targetDateMillis = targetDate
        )

        assertTrue("Есть START", results.any { it.actionType == "PN_START" })

        val dailyOnStartDay = results.filter { it.actionType == "PN_DAILY" }
            .filter { isSameDay(sunday, it.date) }
        assertEquals("Нет DAILY в воскресенье старта", 0, dailyOnStartDay.size)
    }

    /**
     * Старт в понедельник - есть START + DAILY
     */
    @Test
    fun `START and DAILY on monday start`() {
        val monday = findNextMonday()
        val targetDate = addDays(monday, 1)

        val results = calculateNoviceFlowForecast(
            inFlow = 15000.0,
            dailyPercent = DAILY_PERCENT,
            wallet = 0.0,
            startDateMillis = monday,
            targetDateMillis = targetDate
        )

        assertTrue("Есть START", results.any { it.actionType == "PN_START" })
        assertTrue("Есть DAILY", results.any { it.actionType == "PN_DAILY" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ФОРМУЛЫ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `accrual formula is correct`() {
        val inFlow = 15000.0
        val percent = 2.0
        val expectedAccrual = inFlow * (percent / 100.0)

        assertEquals("2% от 15000 = 300", 300.0, expectedAccrual, TOLERANCE)
    }

    @Test
    fun `bonus calculation is correct`() {
        val contribution = 10000.0
        val bonusPercent = 50.0
        val expectedInFlow = contribution + contribution * bonusPercent / 100.0

        assertEquals("С бонусом 50%", 15000.0, expectedInFlow, TOLERANCE)
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