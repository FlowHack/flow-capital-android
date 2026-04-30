package com.flowhack.flowcapital.data.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Тесты для calculatePspForecast (Премиум Стартовый Поток - ПСП).
 * Проверяют логику согласно ТЗ:
 * - 20 периодов по 14 дней
 * - Коэффициенты из БД Настроек
 * - Считается, что взнос делается идеально день в день
 * - Дата окончания периода = startDate + (periodNum * 14 дней)
 */
class PspFlowForecastTest {

    /**
     * Проверка: Прогноз создает ровно 20 периодов.
     * Согласно ТЗ: "20 периодов по 14 дней"
     */
    @Test
    fun forecast_createsExactly20Periods() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        assertEquals("Должно быть ровно 20 периодов", 20, result.size)
    }

    /**
     * Проверка: Периоды идут последовательно от 1 до 20.
     */
    @Test
    fun forecast_periodsAreSequential() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        for (i in 0 until 20) {
            assertEquals("Период ${i + 1} должен иметь номер ${i + 1}", i + 1, result[i].periodNumber)
        }
    }

    /**
     * Проверка: Расчет начисления для периода (nominal * percent / 100).
     * Согласно ТЗ: коэффициенты из БД Настроек
     */
    @Test
    fun forecast_accrualCalculatedCorrectly() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = mapOf(1 to 30.0) // 30% для 1-го периода

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        val firstPeriod = result[0]
        val expectedAccrual = 5000.0 * (30.0 / 100.0) // 1500.0
        assertEquals("Начисление за 1-й период должно быть 1500.0", expectedAccrual, firstPeriod.accrualAmount, 0.01)
    }

    /**
     * Проверка: Дата окончания периода рассчитывается как startDate + (periodNum * 14 дней).
     * Согласно ТЗ: "Дата окончания периода = startDate + (periodNum * 14 дней)"
     */
    @Test
    fun forecast_periodEndDateCalculatedCorrectly() {
        // Arrange
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1) // 01.01.2026
        val nominal = 5000.0
        val coefficients = createDefaultCoefficients()

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        // Период 1: endDate = 01.01 + 14 дней = 15.01
        val period1EndDate = result[0].endDate
        val expectedPeriod1End = createDateMillis(2026, Calendar.JANUARY, 15)
        assertEquals("Период 1 должен закончиться 15.01.2026", expectedPeriod1End, period1EndDate)

        // Период 2: endDate = 01.01 + 28 дней = 29.01
        val period2EndDate = result[1].endDate
        val expectedPeriod2End = createDateMillis(2026, Calendar.JANUARY, 29)
        assertEquals("Период 2 должен закончиться 29.01.2026", expectedPeriod2End, period2EndDate)
    }

    /**
     * Проверка: "Всего начислено" (totalAccrued) накапливается с каждым периодом.
     */
    @Test
    fun forecast_totalAccruedAccumulates() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        // Простые коэффициенты: 10% для всех периодов
        val coefficients = (1..20).associateWith { 10.0 }

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        // Каждый период начисляет 5000 * 10% = 500
        // После 1-го периода: 500
        assertEquals("После 1-го периода всего начислено 500", 500.0, result[0].totalAccrued, 0.01)
        // После 2-го периода: 1000
        assertEquals("После 2-го периода всего начислено 1000", 1000.0, result[1].totalAccrued, 0.01)
        // После 20-го периода: 10000
        assertEquals("После 20-го периода всего начислено 10000", 10000.0, result[19].totalAccrued, 0.01)
    }

    /**
     * Проверка: Последний период (20-й) имеет isCompleted = true.
     * Согласно ТЗ: "Закрытие (20-й период)"
     */
    @Test
    fun forecast_lastPeriodIsCompleted() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        val lastPeriod = result[19]
        assertTrue("20-й период должен быть завершенным (isCompleted=true)", lastPeriod.isCompleted)
        assertEquals("Номер последнего периода должен быть 20", 20, lastPeriod.periodNumber)
    }

    /**
     * Проверка: Если коэффициент для периода не найден, используется 0.0.
     */
    @Test
    fun forecast_missingCoefficientDefaultsToZero() {
        // Arrange
        val nominal = 5000.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = mapOf(1 to 30.0) // Только для 1-го периода

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        // Периоды 2-20 должны иметь начисление 0 (коэффициент 0)
        for (i in 1 until 20) {
            assertEquals("Период ${i + 1} должен иметь начисление 0 (нет коэффициента)",
                0.0, result[i].accrualAmount, 0.01)
        }
    }

    /**
     * Проверка: Номинал сохраняется для каждого периода.
     */
    @Test
    fun forecast_nominalPreservedInAllPeriods() {
        // Arrange
        val nominal = 7500.0
        val startDateMillis = createDateMillis(2026, Calendar.JANUARY, 1)
        val coefficients = createDefaultCoefficients()

        // Act
        val result = calculatePspForecast(
            nominal = nominal,
            startDateMillis = startDateMillis,
            coefficients = coefficients
        )

        // Assert
        result.forEach { period ->
            assertEquals("Номинал должен быть $nominal для всех периодов", nominal, period.nominal, 0.01)
        }
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

    /**
     * Вспомогательная функция для создания дефолтных коэффициентов (из ТЗ).
     */
    private fun createDefaultCoefficients(): Map<Int, Double> {
        return mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
            6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
            11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
            16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
    }
}
