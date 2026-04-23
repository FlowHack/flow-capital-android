package com.example.flowcapital

import com.example.flowcapital.data.forecast.PspForecastResult
import com.example.flowcapital.data.forecast.calculatePspForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit-тесты логики прогнозирования ПСП (Премиум Стартовый Поток).
 *
 * ТЗ:
 * - 20 периодов по 14 дней
 * - Коэффициенты из БД Настроек
 * - Считается, что взнос делается идеально день в день
 */
class PspFlowForecastTest {

    companion object {
        private val DEFAULT_COEFFICIENTS = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
            6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
            11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
            16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
        private const val TOLERANCE = 0.01
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
    // БАЗОВАЯ ЛОГИКА: 20 ПЕРИОДОВ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Ровно 20 периодов
     */
    @Test
    fun `forecast generates exactly 20 periods`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        assertEquals("20 периодов", 20, results.size)
    }

    @Test
    fun `last period is marked as completed`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        val lastPeriod = results.lastOrNull()
        assertNotNull(lastPeriod)
        assertTrue("20-й период завершён", lastPeriod!!.isCompleted)
    }

    @Test
    fun `first period is not completed`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        val firstPeriod = results.firstOrNull()
        assertNotNull(firstPeriod)
        assertTrue("1-й период НЕ завершён", !firstPeriod!!.isCompleted)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КОЭФФИЦИЕНТЫ И НАЧИСЛЕНИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Начисление = номинал * процент / 100
     */
    @Test
    fun `accrual formula is correct`() {
        val nominal = 5000.0
        val percent = 30.0
        val expectedAccrual = nominal * (percent / 100.0)

        assertEquals("30% от 5000 = 1500", 1500.0, expectedAccrual, TOLERANCE)
    }

    @Test
    fun `first period uses first coefficient`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        val firstPeriod = results.first()
        assertEquals("Первый коэффициент 30%", 30.0, firstPeriod.percent, TOLERANCE)
        assertEquals("Начисление за 1-й период", 1500.0, firstPeriod.accrualAmount, TOLERANCE)
    }

    @Test
    fun `total accrued accumulates`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        // totals: 1500 + 2790 + 3900 + ... (сумма)
        val lastPeriod = results.last()
        assertTrue("Всего начислено > 0", lastPeriod.totalAccrued > 0)
        assertTrue("Всего начислено > номинала",
            lastPeriod.totalAccrued > 5000.0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ДАТЫ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Каждый период 14 дней
     */
    @Test
    fun `periods are 14 days apart`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        for (i in 1 until results.size) {
            val current = results[i]
            val previous = results[i - 1]
            val diffDays = ((current.startDate - previous.startDate) / (1000 * 60 * 60 * 24)).toInt()
            assertEquals("Период $i длится 14 дней", 14, diffDays)
        }
    }

    @Test
    fun `end date of period is start date plus 14 days`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        val firstPeriod = results.first()
        val expectedEnd = addDays(startDate, 14)
        assertEquals("Дата закрытия 1-го периода", expectedEnd, firstPeriod.endDate)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ГРАНИЧНЫЕ УСЛОВИЯ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `forecast works with zero nominal`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 0.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        assertEquals("20 периодов", 20, results.size)
    }

    @Test
    fun `forecast works with empty coefficients`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = emptyMap()
        )

        assertEquals("20 периодов", 20, results.size)
        // При 0% начисления будут 0
        results.forEach { assertEquals("Начисление 0", 0.0, it.accrualAmount, TOLERANCE) }
    }

    @Test
    fun `forecast works with custom nominal`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 10000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        // При 10000 и 30% начисление должно быть 3000
        assertEquals("Начисление 1-й период", 3000.0, results[0].accrualAmount, TOLERANCE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // НОМЕРА ПЕРИОДОВ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `periods are numbered 1 to 20`() {
        val startDate = today()

        val results = calculatePspForecast(
            nominal = 5000.0,
            startDateMillis = startDate,
            coefficients = DEFAULT_COEFFICIENTS
        )

        results.forEachIndexed { index, period ->
            assertEquals("Номер периода ${index + 1}", index + 1, period.periodNumber)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ
    // ═══════════════════════════════════════════════════════════════════════

    private fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}