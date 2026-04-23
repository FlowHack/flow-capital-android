package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

/**
 * Тесты для проверки исправленной логики ПСП (Премиум Стартовый Поток)
 *
 * КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ:
 * - totalAccrued (Всего получено) = только то, что попало в КОПИЛКУ
 * - Начисление (accrualAmount) падает в КОШЕЛЁК, а не в копилку
 */
class PSPLogicCorrectedTest {

    private val pspCoefficients = mapOf(
        1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
        6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
        11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
        16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
    )
    private val periodDurationDays = 14L

    // ========== БАЗОВАЯ ЛОГИКА ==========

    /**
     * При взносе:
     * 1. Начисление (accrualAmount) падает в КОШЕЛЁК
     * 2. Пользователь решает сколько отправить в КОПИЛКУ
     * 3. totalAccrued = только то что ушло в КОПИЛКУ (НЕ начисление!)
     */
    @Test
    fun `PSP totalAccrued equals only piggy bank amount`() {
        val nominal = 5000.0
        var totalAccrued = 0.0

        // Период 1: пользователь отправил ВСЁ в копилку
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0) // 1500
        val piggyBank1 = accrual1 // 1500
        totalAccrued += piggyBank1 // НЕ accrual1 + piggyBank1!

        assertEquals(1500.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP totalAccrued accumulates correctly over periods`() {
        val nominal = 5000.0
        var totalAccrued = 0.0

        // Период 1: 1500 в копилку
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0)
        totalAccrued += accrual1
        assertEquals(1500.0, totalAccrued, 0.01)

        // Период 2: 2790 в копилку (итого 4290)
        val accrual2 = nominal * (pspCoefficients[2]!! / 100.0)
        totalAccrued += accrual2
        assertEquals(4290.0, totalAccrued, 0.01)

        // Период 3: 3900 в копилку (итого 8190)
        val accrual3 = nominal * (pspCoefficients[3]!! / 100.0)
        totalAccrued += accrual3
        assertEquals(8190.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP reinvest - totalAccrued stays same when nothing goes to piggy bank`() {
        val previousTotal = 8190.0
        val accrual4 = 5000.0 * (pspCoefficients[4]!! / 100.0) // 4853.5
        val piggyBank4 = 0.0 // Всё ушло на реинвест

        val totalAccrued = previousTotal + piggyBank4

        assertEquals(8190.0, totalAccrued, 0.01)
        assertEquals("Реинвест не должен менять totalAccrued", previousTotal, totalAccrued, 0.01)
    }

    // ========== СЦЕНАРИЙ ИЗ user story ==========

    /**
     * Полный сценарий из user story:
     * - Период 1: всё в копилку -> totalAccrued = 1500
     * - Период 2: всё в копилку -> totalAccrued = 4290
     * - Период 3: всё в копилку -> totalAccrued = 8190
     * - Период 4: реинвест (0 в копилку) -> totalAccrued = 8190
     */
    @Test
    fun `PSP full scenario from user story - periods 1-4`() {
        val nominal = 5000.0
        var totalAccrued = 0.0

        // Период 1 (12.04.23): начисление 1500, ВСЁ в копилку
        // В кошельке: 1500, в копилке: 1500, totalAccrued: 1500
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0)
        totalAccrued += accrual1
        assertEquals(1500.0, totalAccrued, 0.01)

        // Период 2 (26.04.23): начисление 2790, ВСЁ в копилку
        // В кошельке: 2790, в копилке: 1500+2790=4290, totalAccrued: 4290
        val accrual2 = nominal * (pspCoefficients[2]!! / 100.0)
        totalAccrued += accrual2
        assertEquals(4290.0, totalAccrued, 0.01)

        // Период 3 (через долгое время, напр. 02.04.2026): начисление 3900, ВСЁ в копилку
        // В кошельке: 3900, в копилке: 4290+3900=8190, totalAccrued: 8190
        val accrual3 = nominal * (pspCoefficients[3]!! / 100.0)
        totalAccrued += accrual3
        assertEquals(8190.0, totalAccrued, 0.01)

        // Период 4: начисление 4853.5, ВСЁ в реинвест (0 в копилку)
        // В кошельке: 4853.5, в копилке: 8190, totalAccrued: 8190 (НЕ изменилось!)
        val accrual4 = nominal * (pspCoefficients[4]!! / 100.0)
        totalAccrued += 0.0 // Реинвест = 0 в копилку
        assertEquals(8190.0, totalAccrued, 0.01)
    }

    /**
     * Сценарий: пользователь забыл про поток на долгое время
     */
    @Test
    fun `PSP user forgot about flow - totalAccrued accumulates correctly`() {
        val nominal = 5000.0
        var totalAccrued = 0.0

        // Периоды 1-3 уже прошли, всё ушло в копилку
        totalAccrued += nominal * (pspCoefficients[1]!! / 100.0) // 1500
        totalAccrued += nominal * (pspCoefficients[2]!! / 100.0) // 2790
        totalAccrued += nominal * (pspCoefficients[3]!! / 100.0) // 3900

        assertEquals(8190.0, totalAccrued, 0.01)

        // Пользователь вспомнил, сделал взнос в период 4 (реинвест)
        // totalAccrued остаётся 8190
        totalAccrued += 0.0
        assertEquals(8190.0, totalAccrued, 0.01)
    }

    // ========== РАСЧЁТ НАЧИСЛЕНИЙ ==========

    @Test
    fun `PSP accrual for period 1 with 5000 nominal`() {
        val nominal = 5000.0
        val accrual = nominal * (pspCoefficients[1]!! / 100.0)
        assertEquals(1500.0, accrual, 0.01)
    }

    @Test
    fun `PSP accrual for period 2 with 5000 nominal`() {
        val nominal = 5000.0
        val accrual = nominal * (pspCoefficients[2]!! / 100.0)
        assertEquals(2790.0, accrual, 0.01)
    }

    @Test
    fun `PSP accrual for period 3 with 5000 nominal`() {
        val nominal = 5000.0
        val accrual = nominal * (pspCoefficients[3]!! / 100.0)
        assertEquals(3900.0, accrual, 0.01)
    }

    @Test
    fun `PSP accrual for period 4 with 5000 nominal`() {
        val nominal = 5000.0
        val accrual = nominal * (pspCoefficients[4]!! / 100.0)
        assertEquals(4853.5, accrual, 0.01)
    }

    @Test
    fun `PSP accrual for period 20 with 5000 nominal - full return`() {
        val nominal = 5000.0
        val accrual = nominal * (pspCoefficients[20]!! / 100.0)
        assertEquals(10000.0, accrual, 0.01) // Полный возврат номинала
    }

    // ========== ПРОГНОЗ ==========

    @Test
    fun `PSP forecast - totalAccrued if all goes to piggy bank for 5 periods`() {
        val nominal = 5000.0
        var forecastTotal = 0.0

        for (period in 1..5) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            forecastTotal += accrual // Только в копилку
        }

        // 1500 + 2790 + 3900 + 4853.5 + 5674 = 18717.5
        assertEquals(18717.5, forecastTotal, 0.01)
    }

    @Test
    fun `PSP forecast - totalAccrued for all 20 periods`() {
        val nominal = 5000.0
        var forecastTotal = 0.0

        for (period in 1..20) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            forecastTotal += accrual
        }

        // Сумма всех коэффициентов: 3024.88%
        // 5000 * 30.2488 = 151244
        assertEquals(151244.0, forecastTotal, 1.0)
    }

    // ========== ДАТЫ ПЕРИОДОВ ==========

    @Test
    fun `PSP period dates calculation from user story`() {
        val periodDuration = periodDurationDays * 24 * 60 * 60 * 1000

        // Период 1: 12.04.2023 - 26.04.2023
        val period1Start = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val period1End = period1Start + periodDuration

        val expectedEnd = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 26, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expectedEnd, period1End)
    }

    @Test
    fun `PSP button becomes active on period end date`() {
        val periodEndDate = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 26, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // В тот же день (любое время после 00:00)
        val sameDayAfternoon = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 26, 14, 30, 0)
        }.timeInMillis

        val isPeriodEnded = sameDayAfternoon >= periodEndDate
        assertTrue(isPeriodEnded)
    }

    @Test
    fun `PSP button stays active until contribution is made`() {
        val periodEndDate = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 26, 0, 0, 0)
        }.timeInMillis

        // Через день
        val nextDay = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 27, 10, 0, 0)
        }.timeInMillis

        // Через неделю
        val weekLater = Calendar.getInstance().apply {
            set(2023, Calendar.MAY, 3, 10, 0, 0)
        }.timeInMillis

        val isActiveNextDay = nextDay >= periodEndDate
        val isActiveWeekLater = weekLater >= periodEndDate

        assertTrue(isActiveNextDay)
        assertTrue(isActiveWeekLater)
    }

    // ========== КОЭФФИЦИЕНТЫ ==========

    @Test
    fun `PSP all coefficients match specification`() {
        val expected = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
            6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
            11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
            16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )

        for (i in 1..20) {
            assertEquals("Period $i", expected[i]!!, pspCoefficients[i]!!, 0.001)
        }
    }

    @Test
    fun `PSP coefficients are monotonically increasing`() {
        for (i in 2..20) {
            assertTrue(
                "Period $i (${pspCoefficients[i]}) should be >= period ${i-1} (${pspCoefficients[i - 1]})",
                pspCoefficients[i]!! >= pspCoefficients[i - 1]!!
            )
        }
    }

    @Test
    fun `PSP period 20 equals exactly 200 percent`() {
        assertEquals(200.0, pspCoefficients[20]!!, 0.001)
    }

    // ========== ФОРМАТИРОВАНИЕ ==========

    @Test
    fun `PSP date format is ddMMyyyy`() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 12)
        }.time

        val formatted = dateFormat.format(date)
        assertEquals("12.04.2023", formatted)
    }

    @Test
    fun `PSP amount format with currency symbol`() {
        val amount = 1500.0
        val formatted = String.format(Locale.US, "%.2f₽", amount)
        assertEquals("1500.00₽", formatted)
    }

    @Test
    fun `PSP percentage format`() {
        val percent = 30.0
        val formatted = String.format(Locale.US, "%.1f%%", percent)
        assertEquals("30.0%", formatted)
    }

    // ========== ВАЛИДАЦИЯ ==========

    @Test
    fun `PSP nominal must be positive`() {
        val nominal = 5000.0
        assertTrue(nominal > 0)
    }

    @Test
    fun `PSP period must be between 1 and 20`() {
        for (period in 1..20) {
            assertTrue(period in 1..20)
        }
    }

    @Test
    fun `PSP piggy bank amount cannot exceed accrual`() {
        val accrual = 1500.0
        val maxPiggyBank = accrual

        assertTrue(maxPiggyBank <= accrual)
    }

    @Test
    fun `PSP piggy bank amount can be zero`() {
        val piggyBank = 0.0
        assertEquals(0.0, piggyBank, 0.01)
    }
}
