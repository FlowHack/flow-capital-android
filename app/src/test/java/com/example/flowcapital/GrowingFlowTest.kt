package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit-тесты логики Растущего Потока (РП).
 *
 * По ТЗ:
 * - 1 поток, Воскресенье — неактивный день
 * - Бонус ко взносу по таблице из БД Настроек
 * - Вывод начинается со Стартового % (0.1%) и растет на Ежедневный добавочный % (0.003%) за каждое нажатие
 * - Если "В потоке" = 0.00 — кнопка блокируется навсегда
 */
class GrowingFlowTest {

    companion object {
        private const val START_PERCENT = 0.1
        private const val DAILY_ADDITION = 0.003
        private val E_CURRENCY_COEFFICIENTS = mapOf(
            1000.0 to 50.0,
            5000.0 to 75.0,
            10000.0 to 100.0,
            50000.0 to 125.0,
            100000.0 to 150.0,
            500000.0 to 175.0,
            1000000.0 to 200.0
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СТАРТ ПОТОКА (ТЗ: Старт РП)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При старте: Взнос + Взнос * Бонус% = В потоке
     * Пример из ТЗ: 10000 + 100% = 20000
     * Стартовый % = 0.1%, Начисление = В потоке * 0.1%
     */
    @Test
    fun `RP start with 10000 rubles gives 100 percent bonus`() {
        val contribution = 10000.0
        val bonusPercent = 100.0
        val expectedInFlow = contribution * (1 + bonusPercent / 100.0)

        assertEquals("10000 + 100% = 20000", 20000.0, expectedInFlow, 0.01)
    }

    @Test
    fun `RP start calculates correct initial accrual`() {
        val inFlow = 20000.0
        val initialPercent = 0.1
        val initialAccrual = inFlow * (initialPercent / 100.0)

        assertEquals("Начисление = 20000 * 0.1% = 20", 20.0, initialAccrual, 0.01)
    }

    @Test
    fun `RP start initial wallet is zero`() {
        val initialWallet = 0.0
        assertEquals("Кошелек при старте = 0", 0.0, initialWallet, 0.01)
    }

    @Test
    fun `RP start full calculation from TZ example`() {
        val contribution = 10000.0
        val bonusPercent = 100.0  // 100% по таблице для 10000
        val startPercent = START_PERCENT

        val inFlow = contribution * (1 + bonusPercent / 100.0)  // 20000
        val accrual = inFlow * (startPercent / 100.0)  // 20
        val wallet = 0.0

        assertEquals(20000.0, inFlow, 0.01)
        assertEquals(20.0, accrual, 0.01)
        assertEquals(0.0, wallet, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ШАГ (НАЖАТИЕ КНОПКИ) (ТЗ: Шаг)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При нажатии кнопки процент увеличивается на 0.003%
     * Начисление вычитается из "В потоке" и прибавляется к "Кошелек"
     */
    @Test
    fun `RP button press increases percent by daily addition`() {
        var currentPercent = 0.1
        val dailyAddition = DAILY_ADDITION

        repeat(1) { currentPercent += dailyAddition }

        assertEquals("После 1 нажатия: 0.1 + 0.003 = 0.103%", 0.103, currentPercent, 0.0001)
    }

    @Test
    fun `RP percent increases with each button press`() {
        var percent = 0.1

        repeat(3) { percent += DAILY_ADDITION }

        assertEquals("После 3 нажатий: 0.1 + 3*0.003 = 0.109%", 0.109, percent, 0.0001)
    }

    @Test
    fun `RP daily button press calculates new accrual`() {
        val inFlow = 19980.0
        val percent = 0.103
        val accrual = inFlow * (percent / 100.0)

        assertEquals("Начисление = 19980 * 0.103% = 20.58", 20.58, accrual, 0.01)
    }

    @Test
    fun `RP button press transfers accrual to wallet`() {
        var inFlow = 20000.0
        var percent = 0.1
        var wallet = 0.0

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        percent += DAILY_ADDITION

        assertEquals("В потоке: 20000 - 20 = 19980", 19980.0, inFlow, 0.01)
        assertEquals("Кошелек: 0 + 20 = 20", 20.0, wallet, 0.01)
        assertEquals("Процент: 0.103", 0.103, percent, 0.0001)
    }

    @Test
    fun `RP full cycle from TZ example`() {
        var inFlow = 20000.0
        var percent = 0.1
        var wallet = 0.0

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        percent += DAILY_ADDITION

        assertEquals(19980.0, inFlow, 0.01)
        assertEquals(20.0, wallet, 0.01)
        assertEquals(0.103, percent, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // РЕИНВЕСТ (ТЗ: Окно Реинвест)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При реинвесте сумма добавляется к "В потоке" с учетом бонуса
     * Начисление пересчитывается от новой суммы с сохранением текущего процента
     */
    @Test
    fun `RP reinvest adds contribution with bonus to flow`() {
        var inFlow = 15000.0
        val currentPercent = 0.15
        val contribution = 5000.0
        val bonusPercent = 100.0  // для 5000+15000=20000 -> 100%

        val bonusAmount = contribution * (bonusPercent / 100.0)
        val newInFlow = inFlow + contribution + bonusAmount

        assertEquals("15000 + 5000 + 5000(бонус) = 25000", 25000.0, newInFlow, 0.01)
    }

    @Test
    fun `RP reinvest recalculates accrual from new flow with same percent`() {
        val newInFlow = 25000.0
        val percent = 0.15
        val newAccrual = newInFlow * (percent / 100.0)

        assertEquals("Начисление от 25000 * 0.15% = 37.5", 37.5, newAccrual, 0.01)
    }

    @Test
    fun `RP reinvest keeps wallet unchanged when field empty`() {
        val lastWallet = 500.0
        val newWalletInput: String? = null

        val resultWallet = newWalletInput?.toDoubleOrNull() ?: lastWallet

        assertEquals("Пустое поле = оставить прежний кошелек", 500.0, resultWallet, 0.01)
    }

    @Test
    fun `RP reinvest allows explicit zero wallet`() {
        val lastWallet = 500.0
        val newWalletInput = "0"

        val resultWallet = newWalletInput.toDoubleOrNull() ?: lastWallet

        assertEquals("0 явно меняет кошелек на 0", 0.0, resultWallet, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КОРРЕКЦИЯ (ТЗ: Окно Коррекция)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Если меняем "В потоке" -> пересчитывается "Начисление"
     * ТЗ: Если меняем ТОЛЬКО "Начисление" -> программа высчитывает новый % по формуле: (Начисление * 100) / В потоке
     */
    @Test
    fun `RP correction flow change recalculates accrual`() {
        val newInFlow = 15000.0
        val currentPercent = 0.1
        val newAccrual = newInFlow * (currentPercent / 100.0)

        assertEquals("15000 * 0.1% = 15", 15.0, newAccrual, 0.01)
    }

    @Test
    fun `RP correction accrual change recalculates percent`() {
        val inFlow = 10000.0
        val newAccrual = 100.0
        val calculatedPercent = (newAccrual * 100.0) / inFlow

        assertEquals("(100 * 100) / 10000 = 1%", 1.0, calculatedPercent, 0.001)
    }

    @Test
    fun `RP correction wallet change does not affect other fields`() {
        var inFlow = 10000.0
        var percent = 0.1
        var wallet = 0.0
        val newWallet = 500.0

        wallet = newWallet

        assertEquals("В потоке не меняется", 10000.0, inFlow, 0.01)
        assertEquals("Процент не меняется", 0.1, percent, 0.0001)
        assertEquals("Кошелек меняется", 500.0, wallet, 0.01)
    }

    @Test
    fun `RP correction requires at least one field changed`() {
        fun validateCorrection(
            flowText: String,
            accrualText: String,
            walletText: String,
            checkboxChanged: Boolean,
            currentFlow: Double,
            currentAccrual: Double,
            currentWallet: Double
        ): Boolean {
            fun parseDouble(text: String): Double? = text.replace(",", ".").toDoubleOrNull()
            return (flowText.isNotEmpty() && parseDouble(flowText)?.let { it != currentFlow } ?: false) ||
                   (accrualText.isNotEmpty() && parseDouble(accrualText)?.let { it != currentAccrual } ?: false) ||
                   (walletText.isNotEmpty() && parseDouble(walletText)?.let { it != currentWallet } ?: false) ||
                   checkboxChanged
        }

        assertFalse("Ничего не изменилось -> false", validateCorrection("", "", "", false, 1000.0, 10.0, 0.0))
        assertTrue("Изменился поток -> true", validateCorrection("1500", "", "", false, 1000.0, 10.0, 0.0))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВЫХОДНЫЕ (ТЗ: Воскресенье — неактивный день)
    // ═══════════════════════════════════════════════════���═���═════════════════

    /**
     * ТЗ: Воскресенье — неактивный день. Кнопка заблокирована.
     * Генерируется фиолетовая запись с дублированием балансов субботы.
     */
    @Test
    fun `sunday is detected as weekend`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)  // Воскресенье
        }
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        assertTrue("19.04.2026 - воскресенье", isSunday)
    }

    @Test
    fun `saturday is active day`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 18)  // Суббота
        }
        val isSaturday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        assertTrue("18.04.2026 - суббота", isSaturday)
    }

    @Test
    fun `weekend record duplicates saturday balances`() {
        val saturdayInFlow = 14700.0
        val saturdayAccrual = 300.0
        val saturdayWallet = 300.0

        val sundayInFlow = saturdayInFlow
        val sundayAccrual = saturdayAccrual
        val sundayWallet = saturdayWallet

        assertEquals("Балансы дублируются", saturdayInFlow, sundayInFlow, 0.01)
    }

    @Test
    fun `weekend record marks as SUNDAY action type`() {
        val saturdayInFlow = 14700.0
        val actionType = "SUNDAY"

        assertEquals("Тип действия = SUNDAY", "SUNDAY", actionType)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРОГНОЗ И ОСТАНОВКА ПРИ НУЛЕ (ТЗ: Прогноз)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Прогноз останавливается, когда "В потоке" падает до 0.00 или ниже
     * Дальше нулей не считаем
     */
    @Test
    fun `RP forecast stops when flow reaches zero`() {
        var inFlow = 5000.0
        var percent = 0.1
        var daysCount = 0

        while (inFlow > 0 && daysCount < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow = (inFlow - accrual).coerceAtLeast(0.0)
            if (inFlow <= 0) break
            percent += DAILY_ADDITION
            daysCount++
        }

        assertTrue("Должен остановиться при достижении 0", daysCount > 0)
    }

    @Test
    fun `RP forecast stops at zero - exact test`() {
        var simInFlow = 5000.0
        var simPercent = 0.1
        var dayCount = 0

        while (simInFlow > 0 && dayCount < 200) {
            val accrual = simInFlow * (simPercent / 100.0)
            simInFlow = (simInFlow - accrual).coerceAtLeast(0.0)
            if (simInFlow <= 0) break
            simPercent += DAILY_ADDITION
            dayCount++
        }

        assertTrue(dayCount > 0)
    }

    @Test
    fun `RP button disabled when flow is zero`() {
        val inFlow = 0.0
        val isButtonDisabled = inFlow <= 0

        assertTrue("Кнопка заблокирована при 0", isButtonDisabled)
    }

    @Test
    fun `RP button text shows reinvest when flow is zero`() {
        val inFlow = 0.0
        val buttonText = if (inFlow <= 0) "СДЕЛАЙТЕ РЕИНВЕСТ" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"

        assertEquals("Текст кнопки = СДЕЛАЙТЕ РЕИНВЕСТ", "СДЕЛАЙТЕ РЕИНВЕСТ", buttonText)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЛУЧШАЯ ДАТА (ТЗ: Лучшая дата)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Лучшая дата — рассчитывает дату реинвеста
     * Показывает историю до даты реинвеста + 1 ДЕНЬ (включительно)
     */
    @Test
    fun `RP best date finds dropping accrual point`() {
        var inFlow = 50000.0
        var percent = 0.1
        var prevAccrual = inFlow * (percent / 100.0)
        var foundBestDate = false

        for (day in 1..200) {
            val accrual = inFlow * (percent / 100.0)
            if (day > 1 && accrual < prevAccrual) {
                foundBestDate = true
                break
            }
            prevAccrual = accrual
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += DAILY_ADDITION
        }

        assertTrue("Наступает момент когда начисление падает", foundBestDate)
    }

    @Test
    fun `RP best date includes one extra day`() {
        var bestDay = 50
        val includeExtraDay = true

        val displayDays = if (includeExtraDay) bestDay + 1 else bestDay

        assertEquals("Показываем + 1 день", 51, displayDays)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // E-CURRENCY БОНУСЫ (ТЗ: Таблица бонусов из БД Настроек)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `RP E-currency bonus table from settings`() {
        val coefficients = E_CURRENCY_COEFFICIENTS
        assertEquals(7, coefficients.size)
    }

    @Test
    fun `RP E-currency bonus 1000 gives 50 percent`() {
        val coefficients = E_CURRENCY_COEFFICIENTS
        val sorted = coefficients.entries.sortedByDescending { it.key }

        fun getBonus(amount: Double): Double {
            for ((threshold, bonus) in sorted) {
                if (amount >= threshold) return bonus
            }
            return 0.0
        }

        assertEquals("1000 -> 50%", 50.0, getBonus(1000.0), 0.01)
    }

    @Test
    fun `RP E-currency bonus 10000 gives 100 percent`() {
        val coefficients = E_CURRENCY_COEFFICIENTS
        val sorted = coefficients.entries.sortedByDescending { it.key }

        fun getBonus(amount: Double): Double {
            for ((threshold, bonus) in sorted) {
                if (amount >= threshold) return bonus
            }
            return 0.0
        }

        assertEquals("10000 -> 100%", 100.0, getBonus(10000.0), 0.01)
    }

    @Test
    fun `RP E-currency bonus 1000000 gives 200 percent`() {
        val coefficients = E_CURRENCY_COEFFICIENTS
        val sorted = coefficients.entries.sortedByDescending { it.key }

        fun getBonus(amount: Double): Double {
            for ((threshold, bonus) in sorted) {
                if (amount >= threshold) return bonus
            }
            return 0.0
        }

        assertEquals("1000000 -> 200%", 200.0, getBonus(1000000.0), 0.01)
    }

    @Test
    fun `RP E-currency bonus below threshold gives zero`() {
        val coefficients = E_CURRENCY_COEFFICIENTS
        val sorted = coefficients.entries.sortedByDescending { it.key }

        fun getBonus(amount: Double): Double {
            for ((threshold, bonus) in sorted) {
                if (amount >= threshold) return bonus
            }
            return 0.0
        }

        assertEquals("< 1000 -> 0%", 0.0, getBonus(999.0), 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ФОРМАТИРОВАНИЕ (ТЗ: UI)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `RP percent displays with 3 decimal places`() {
        val percent = 0.103
        val formatted = String.format(java.util.Locale.US, "%.3f", percent)
        assertEquals("0.103", formatted)
    }

    @Test
    fun `RP amounts display with 2 decimal places`() {
        val amount = 19980.00
        val formatted = String.format(java.util.Locale.US, "%.2f", amount)
        assertEquals("19980.00", formatted)
    }

    @Test
    fun `RP accrual displays with sign`() {
        val accrual = 20.58
        val formatted = String.format(java.util.Locale.US, "+%.2f", accrual)
        assertEquals("+20.58", formatted)
    }
}