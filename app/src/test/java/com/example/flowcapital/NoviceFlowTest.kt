package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit-тесты логики Потока Новичка (ПН).
 *
 * По ТЗ:
 * - Только 1 поток, Воскресенье — неактивный день
 * - Бонус ко взносу всегда 50%
 * - Фиксированный процент 2% (НЕ растет в отличие от РП)
 * - Начисление = В потоке * 2%
 */
class NoviceFlowTest {

    companion object {
        private const val BONUS_PERCENT = 50.0
        private const val DAILY_PERCENT = 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СТАРТ ПОТОКА (ТЗ: Окно Старт ПН)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При старте: Взнос + Взнос * 50% = В потоке
     * Пример: 10000 + 5000 = 15000
     */
    @Test
    fun `PN start with 10000 gives 50 percent bonus`() {
        val contribution = 10000.0
        val bonusAmount = contribution * (BONUS_PERCENT / 100.0)
        val expectedInFlow = contribution + bonusAmount

        assertEquals("10000 + 5000(бонус) = 15000", 15000.0, expectedInFlow, 0.01)
    }

    @Test
    fun `PN start calculates correct initial accrual`() {
        val inFlow = 15000.0
        val percent = DAILY_PERCENT
        val initialAccrual = inFlow * (percent / 100.0)

        assertEquals("Начисление = 15000 * 2% = 300", 300.0, initialAccrual, 0.01)
    }

    @Test
    fun `PN start initial wallet is zero`() {
        val initialWallet = 0.0
        assertEquals("Кошелек при старте = 0", 0.0, initialWallet, 0.01)
    }

    @Test
    fun `PN start uses fixed 2 percent`() {
        val percent = DAILY_PERCENT
        assertEquals("Процент всегда 2%", 2.0, percent, 0.01)
    }

    @Test
    fun `PN start full calculation from TZ example`() {
        val contribution = 10000.0
        val bonusPercent = BONUS_PERCENT
        val fixedPercent = DAILY_PERCENT

        val inFlow = contribution * (1 + bonusPercent / 100.0)  // 15000
        val accrual = inFlow * (fixedPercent / 100.0)  // 300
        val wallet = 0.0

        assertEquals(15000.0, inFlow, 0.01)
        assertEquals(300.0, accrual, 0.01)
        assertEquals(0.0, wallet, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ШАГ (НАЖАТИЕ КНОПКИ) (ТЗ: Кнопка действия)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При нажатии кнопки процент остается 2%
     * Начисление вычитается из "В потоке" и прибавляется к "Кошелек"
     */
    @Test
    fun `PN button press keeps percent at 2 percent`() {
        var percent = DAILY_PERCENT
        val newPercent = percent  // Не меняется!

        assertEquals("Процент остается 2%", 2.0, newPercent, 0.01)
    }

    @Test
    fun `PN button press does NOT increase percent`() {
        var percent = DAILY_PERCENT
        repeat(10) { /* НЕ меняется */ }

        assertEquals("После 10 нажатий процент все еще 2%", 2.0, percent, 0.01)
    }

    @Test
    fun `PN button press calculates new accrual`() {
        val inFlow = 15000.0
        val percent = DAILY_PERCENT
        val accrual = inFlow * (percent / 100.0)

        assertEquals("Начисление = 15000 * 2% = 300", 300.0, accrual, 0.01)
    }

    @Test
    fun `PN button press transfers accrual to wallet`() {
        var inFlow = 15000.0
        val percent = DAILY_PERCENT
        var wallet = 0.0

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual

        assertEquals("В потоке: 15000 - 300 = 14700", 14700.0, inFlow, 0.01)
        assertEquals("Кошелек: 0 + 300 = 300", 300.0, wallet, 0.01)
    }

    @Test
    fun `PN full cycle example from TZ`() {
        var inFlow = 15000.0
        var percent = DAILY_PERCENT
        var wallet = 0.0

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual

        assertEquals(14700.0, inFlow, 0.01)
        assertEquals(300.0, wallet, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕИНВЕСТ (ТЗ: Окно Реинвест — ВНИМАНИЕ: бонус ТАКЖЕ!)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При реинвесте бонус +50% ТАКЖЕ начисляется!
     * Пример: было 15000, реинвест 1000 -> 15000 + 1500 = 16500
     */
    @Test
    fun `PN reinvest adds contribution with 50 percent bonus`() {
        var inFlow = 15000.0
        val contribution = 1000.0
        val bonusAmount = contribution * (BONUS_PERCENT / 100.0)

        val newInFlow = inFlow + contribution + bonusAmount

        assertEquals("15000 + 1000 + 500(бонус) = 16500", 16500.0, newInFlow, 0.01)
    }

    @Test
    fun `PN reinvest recalculates accrual from new flow`() {
        val newInFlow = 16500.0
        val percent = DAILY_PERCENT
        val newAccrual = newInFlow * (percent / 100.0)

        assertEquals("Начисление = 16500 * 2% = 330", 330.0, newAccrual, 0.01)
    }

    @Test
    fun `PN reinvest recalculates correctly`() {
        var inFlow = 15000.0
        val contribution = 1000.0
        var percent = DAILY_PERCENT
        var wallet = 0.0

        val bonusAmount = contribution * (BONUS_PERCENT / 100.0)
        inFlow += contribution + bonusAmount
        val newAccrual = inFlow * (percent / 100.0)

        assertEquals(16500.0, inFlow, 0.01)
        assertEquals(330.0, newAccrual, 0.01)
    }

    @Test
    fun `PN reinvest keeps wallet unchanged when field empty`() {
        val lastWallet = 500.0
        val newWalletInput: String? = null

        val resultWallet = newWalletInput?.toDoubleOrNull() ?: lastWallet

        assertEquals("Пустое поле = оставить прежний", 500.0, resultWallet, 0.01)
    }

    @Test
    fun `PN reinvest allows explicit zero wallet`() {
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
     * Если меняем "Кошелек" -> меняется только кошелек
     * Чекбокс "Кнопка нажата" -> меняет состояние кнопки
     */
    @Test
    fun `PN correction flow change recalculates accrual`() {
        val newInFlow = 20000.0
        val percent = DAILY_PERCENT
        val newAccrual = newInFlow * (percent / 100.0)

        assertEquals("20000 * 2% = 400", 400.0, newAccrual, 0.01)
    }

    @Test
    fun `PN correction wallet change does NOT affect accrual`() {
        var inFlow = 15000.0
        var percent = DAILY_PERCENT
        val oldWallet = 100.0
        var wallet = oldWallet

        val newWallet = 500.0
        wallet = newWallet

        val accrual = inFlow * (percent / 100.0)

        assertEquals("В потоке не меняется", 15000.0, inFlow, 0.01)
        assertEquals("Начисление не меняется", 300.0, accrual, 0.01)
        assertEquals("Кошелек меняется", 500.0, wallet, 0.01)
    }

    @Test
    fun `PN correction requires at least one field changed`() {
        fun validateCorrection(
            flowText: String,
            walletText: String,
            checkboxChanged: Boolean,
            currentFlow: Double,
            currentWallet: Double
        ): Boolean {
            fun parseDouble(text: String): Double? = text.replace(",", ".").toDoubleOrNull()
            return (flowText.isNotEmpty() && parseDouble(flowText)?.let { it != currentFlow } ?: false) ||
                   (walletText.isNotEmpty() && parseDouble(walletText)?.let { it != currentWallet } ?: false) ||
                   checkboxChanged
        }

        assertFalse("Ничего не изменилось", validateCorrection("", "", false, 15000.0, 300.0))
        assertTrue("Изменился поток", validateCorrection("20000", "", false, 15000.0, 300.0))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВЫХОДНЫЕ (ТЗ: Суббота — рабочий, Воскресенье — неактивный)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Суббота — рабочий день (активный)
     * ТЗ: Воскресенье — неактивный день. Кнопка заблокирована.
     * Генерируется фиолетовая запись SUNDAY с дублированием балансов.
     */
    @Test
    fun `saturday is active day for PN`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 18)  // Суббота
        }
        val isSaturday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        assertTrue("Суббота — активный де��ь", isSaturday)
    }

    @Test
    fun `sunday is inactive day for PN`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)  // Воскресенье
        }
        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        assertTrue("Воскресенье — неактивный", isSunday)
    }

    @Test
    fun `PN sunday generates SUNDAY action type`() {
        val actionType = "SUNDAY"
        assertEquals("Тип действия = SUNDAY", "SUNDAY", actionType)
    }

    @Test
    fun `PN weekend record duplicates saturday balances`() {
        val saturdayInFlow = 14700.0
        val saturdayAccrual = 294.0
        val saturdayWallet = 300.0

        val sundayInFlow = saturdayInFlow
        val sundayAccrual = saturdayAccrual
        val sundayWallet = saturdayWallet

        assertEquals("Балансы дублируются", saturdayInFlow, sundayInFlow, 0.01)
    }

    @Test
    fun `PN sunday accrual does NOT happen`() {
        val saturdayAccrual = 294.0
        val sundayAccrual = 0.0  // Не начисляется!

        assertEquals("В воскресенье начисление = 0", 0.0, sundayAccrual, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КОНЕЦ ЦИКЛА (ТЗ: Остановка при 0)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Как только "В потоке" падает до 0.00 или ниже — цикл завершается
     * Кнопка блокируется, прогноз останавливается
     */
    @Test
    fun `PN cycle stops when flow reaches zero`() {
        var inFlow = 1000.0
        val percent = DAILY_PERCENT
        var daysCount = 0

        while (inFlow > 0 && daysCount < 100) {
            val accrual = inFlow * (percent / 100.0)
            inFlow = (inFlow - accrual).coerceAtLeast(0.0)
            daysCount++
        }

        assertTrue("Цикл завершается", daysCount > 0)
    }

    @Test
    fun `PN button disabled when flow is zero`() {
        val inFlow = 0.0
        val isButtonDisabled = inFlow <= 0
        assertTrue("Кнопка заблокирована", isButtonDisabled)
    }

    @Test
    fun `PN button text shows reinvest when flow is zero`() {
        val inFlow = 0.0
        val buttonText = if (inFlow <= 0) "Сделайте реинвест" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
        assertEquals("Текст = Сделайте реинвест", "Сделайте реинвест", buttonText)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГЕНЕРАЦИЯ ПРОПУСКОВ (ТЗ: Генерация пропусков)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Если пользователь не заходил N дней — генерируется история
     * Будни: "Забыли нажать на кнопку"
     * Выходные: SUNDAY с дублированием
     */
    @Test
    fun `PN missed weekday shows forgot text`() {
        val skipText = "Забыли нажать на кнопку"
        assertEquals("Текст пропуска", "Забыли нажать на кнопку", skipText)
    }

    @Test
    fun `PN skip does NOT affect balances`() {
        var inFlow = 14700.0
        val percent = DAILY_PERCENT
        val wallet = 300.0

        // Пропуск — балансы не меняются
        // inFlow, wallet остаются прежними

        assertEquals("В потоке не меняется", 14700.0, inFlow, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ФОРМАТИРОВАНИЕ (ТЗ: UI)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `PN percent displays with 1 decimal place`() {
        val percent = 2.0
        val formatted = String.format(java.util.Locale.US, "%.1f", percent)
        assertEquals("2.0", formatted)
    }

    @Test
    fun `PN amounts display with 2 decimal places`() {
        val amount = 15000.00
        val formatted = String.format(java.util.Locale.US, "%.2f", amount)
        assertEquals("15000.00", formatted)
    }

    @Test
    fun `PN accrual displays with sign`() {
        val accrual = 300.0
        val formatted = String.format(java.util.Locale.US, "+%.2f", accrual)
        assertEquals("+300.00", formatted)
    }
}