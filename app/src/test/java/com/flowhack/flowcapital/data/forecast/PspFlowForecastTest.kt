package com.flowhack.flowcapital.data.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Тесты для calculatePspForecast (Премиум Стартовый Поток - ПСП).
 * Проверяют логику согласно новому алгоритму "якорных дней":
 * - В месяце всегда 2 периода
 * - Разница между датами закрытия = 14 дней + остаток месяца
 * - Ограничение краевых дней: 29, 30, 31 -> 28
 */
class PspFlowForecastTest {

    @Test
    fun forecast_createsExactly20Periods() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        assertEquals("Должно быть ровно 20 периодов", 20, result.size)
    }

    @Test
    fun forecast_periodsAreSequential() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        for (i in 0 until 20) {
            assertEquals("Период ${i + 1} должен иметь номер ${i + 1}", i + 1, result[i].periodNumber)
        }
    }

    @Test
    fun forecast_accrualCalculatedCorrectly() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = mapOf(1 to 30.0) // 30% для 1-го периода

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        val firstPeriod = result[0]
        val expectedAccrual = 5000.0 * (30.0 / 100.0) // 1500.0
        assertEquals("Начисление за 1-й период должно быть 1500.0", expectedAccrual, firstPeriod.accrualAmount, 0.01)
    }

    @Test
    fun forecast_startDay1_generatesCorrectDates() {
        val startDateMillis = createDateMillis(2026, Calendar.MAY, 1) // startDay = 1 <= 14
        val nominal = 5000.0
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Период 1: day2 = 1 + 14 = 15 -> 15.05.2026
        val period1End = result[0].endDate
        val expectedPeriod1End = createDateMillis(2026, Calendar.MAY, 15)
        assertEquals("Период 1 должен закончиться 15.05.2026", expectedPeriod1End, period1End)

        // Период 2: day1 = 1 -> 01.06.2026
        val period2End = result[1].endDate
        val expectedPeriod2End = createDateMillis(2026, Calendar.JUNE, 1)
        assertEquals("Период 2 должен закончиться 01.06.2026", expectedPeriod2End, period2End)

        // Период 3: day2 = 15 -> 15.06.2026
        val period3End = result[2].endDate
        val expectedPeriod3End = createDateMillis(2026, Calendar.JUNE, 15)
        assertEquals("Период 3 должен закончиться 15.06.2026", expectedPeriod3End, period3End)
    }

    @Test
    fun forecast_startDay24_generatesCorrectDates() {
        val startDateMillis = createDateMillis(2026, Calendar.APRIL, 24) // startDay = 24 > 14
        val nominal = 5000.0
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // day1 = 24 - 14 = 10, day2 = 24
        // Период 1 (odd): monthsToAdd = (1+1)/2 = 1, day = day1 = 10 -> 10.05.2026
        val period1End = result[0].endDate
        val expectedPeriod1End = createDateMillis(2026, Calendar.MAY, 10)
        assertEquals("Период 1 должен закончиться 10.05.2026", expectedPeriod1End, period1End)

        // Период 2 (even): monthsToAdd = 2/2 = 1, day = day2 = 24 -> 24.05.2026
        val period2End = result[1].endDate
        val expectedPeriod2End = createDateMillis(2026, Calendar.MAY, 24)
        assertEquals("Период 2 должен закончиться 24.05.2026", expectedPeriod2End, period2End)

        // Период 3 (odd): monthsToAdd = (3+1)/2 = 2, day = day1 = 10 -> 10.06.2026
        val period3End = result[2].endDate
        val expectedPeriod3End = createDateMillis(2026, Calendar.JUNE, 10)
        assertEquals("Период 3 должен закончиться 10.06.2026", expectedPeriod3End, period3End)
    }

    @Test
    fun forecast_startDay31_edgeDay_handledCorrectly() {
        // startDay = 31 > 14, anchors: day1 = 31-14 = 17, day2 = 31
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 31)
        val nominal = 5000.0
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Период 1 (odd): monthsToAdd = (1+1)/2 = 1, day = day1 = 17 -> но 17.02.2026 обычный день
        val period1End = result[0].endDate
        val expectedPeriod1End = createDateMillis(2026, Calendar.FEBRUARY, 17)
        assertEquals("Период 1 должен закончиться 17.02.2026", expectedPeriod1End, period1End)

        // Период 2 (even): monthsToAdd = 2/2 = 1, day = day2 = 31 -> но 31.02 не существует -> 28
        val period2End = result[1].endDate
        val expectedPeriod2End = createDateMillis(2026, Calendar.FEBRUARY, 28)
        assertEquals("Период 2 должен закончиться 28.02.2026 (ограничение day>28)", expectedPeriod2End, period2End)
    }

    @Test
    fun forecast_yearTransition_handledCorrectly() {
        val startDateMillis = createDateMillis(2025, Calendar.DECEMBER, 1)
        val nominal = 5000.0
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Период 1: day2 = 1+14 = 15 -> 15.12.2025
        assertEquals("Период 1 год", 2025, getYear(result[0].endDate))
        assertEquals("Период 1 месяц", Calendar.DECEMBER, getMonth(result[0].endDate))
        assertEquals("Период 1 день", 15, getDay(result[0].endDate))

        // Период 2: day1 = 1 -> 01.01.2026 (переход года!)
        assertEquals("Период 2 год", 2026, getYear(result[1].endDate))
        assertEquals("Период 2 месяц", Calendar.JANUARY, getMonth(result[1].endDate))
        assertEquals("Период 2 день", 1, getDay(result[1].endDate))
    }

    @Test
    fun forecast_totalAccruedAccumulates() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = (1..20).associateWith { 10.0 }

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Каждый период начисляет 5000 * 10% = 500
        assertEquals("После 1-го периода всего начислено 500", 500.0, result[0].totalAccrued, 0.01)
        assertEquals("После 2-го периода всего начислено 1000", 1000.0, result[1].totalAccrued, 0.01)
        assertEquals("После 20-го периода всего начислено 10000", 10000.0, result[19].totalAccrued, 0.01)
    }

    @Test
    fun forecast_lastPeriodIsCompleted() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        val lastPeriod = result[19]
        assertTrue("20-й период должен быть завершенным (isCompleted=true)", lastPeriod.isCompleted)
        assertEquals("Номер последнего периода должен быть 20", 20, lastPeriod.periodNumber)
    }

    @Test
    fun forecast_missingCoefficientDefaultsToZero() {
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = mapOf(1 to 30.0)

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        for (i in 1 until 20) {
            assertEquals("Период ${i + 1} должен иметь начисление 0 (нет коэффициента)",
                0.0, result[i].accrualAmount, 0.01)
        }
    }

    @Test
    fun forecast_nominalPreservedInAllPeriods() {
        val nominal = 7500.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        result.forEach { period ->
            assertEquals("Номинал должен быть $nominal для всех периодов", nominal, period.nominal, 0.01)
        }
    }

    @Test
    fun periodEndDate_anchorDays_algorithm() {
        // Тест только функции calculatePspPeriodEndDate
        val startDateMillis = createDateMillis(2026, Calendar.MAY, 10)

        // Период 1: startDay=10<=14, odd -> day2=10+14=24
        val period1End = calculatePspPeriodEndDate(startDateMillis, 1)
        assertEquals("Период 1", createDateMillis(2026, Calendar.MAY, 24), period1End)

        // Период 2: even -> day1=10
        val period2End = calculatePspPeriodEndDate(startDateMillis, 2)
        assertEquals("Период 2", createDateMillis(2026, Calendar.JUNE, 10), period2End)

        // Период 3: odd -> day2=24 + 1 месяц
        val period3End = calculatePspPeriodEndDate(startDateMillis, 3)
        assertEquals("Период 3", createDateMillis(2026, Calendar.JUNE, 24), period3End)

        // Период 4: even -> day1=10 + 2 месяца
        val period4End = calculatePspPeriodEndDate(startDateMillis, 4)
        assertEquals("Период 4", createDateMillis(2026, Calendar.JULY, 10), period4End)
    }

    @Test
    fun periodEndDate_startDay29_31_clampedTo28() {
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 29)

        // day1 = 29-14 = 15, day2 = 29
        // Период 2 (even, startDay>14): day=day2=29 -> clamp to 28, +1 month = 28.02
        val period2End = calculatePspPeriodEndDate(startDateMillis, 2)
        assertEquals("Период 2 (day=29) должен быть 28.02", createDateMillis(2026, Calendar.FEBRUARY, 28), period2End)

        // Период 4: day2=29 -> clamp to 28, +2 months = 28.03
        val period4End = calculatePspPeriodEndDate(startDateMillis, 4)
        assertEquals("Период 4 (day=29) должен быть 28.03", createDateMillis(2026, Calendar.MARCH, 28), period4End)
    }

    @Test
    fun periodEndDate_startDay30_clampedTo28() {
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 30)

        // day1 = 30-14 = 16, day2 = 30
        // Период 2 (even, startDay>14): day=day2=30 -> clamp to 28, +1 month = 28.02
        val period2End = calculatePspPeriodEndDate(startDateMillis, 2)
        assertEquals("Период 2 (day=30) должен быть 28.02", createDateMillis(2026, Calendar.FEBRUARY, 28), period2End)
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

    private fun getYear(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    private fun getMonth(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH)
    private fun getDay(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    private fun createDefaultCoefficients(): Map<Int, Double> {
        return mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
            6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
            11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
            16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
    }
}