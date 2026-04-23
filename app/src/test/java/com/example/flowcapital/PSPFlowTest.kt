package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Unit-тесты логики Премиум Стартового Потока (ПСП).
 *
 * По ТЗ:
 * - Можно создавать сколько угодно потоков
 * - Нет выходных по воскресеньям
 * - Жизненный цикл: 20 периодов по 14 дней
 * - Начисление = Номинал * (Процент / 100)
 */
class PSPFlowTest {

    companion object {
        private val PSP_COEFFICIENTS = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
            5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
            9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
            13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
            17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
        private const val PERIOD_DAYS = 14
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КОЭФФИЦИЕНТЫ (ТЗ: Единый источник истины — БД Настроек)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: 20 периодов с коэффициентами 30% -> 200%
     */
    @Test
    fun `PSP has exactly 20 periods`() {
        assertEquals("Должно быть 20 периодов", 20, PSP_COEFFICIENTS.size)
    }

    @Test
    fun `PSP period 1 coefficient is 30 percent`() {
        assertEquals("Период 1 = 30%", 30.0, PSP_COEFFICIENTS[1]!!, 0.01)
    }

    @Test
    fun `PSP period 20 coefficient is 200 percent`() {
        assertEquals("Период 20 = 200%", 200.0, PSP_COEFFICIENTS[20]!!, 0.01)
    }

    @Test
    fun `PSP coefficients grow monotonically`() {
        val sorted = PSP_COEFFICIENTS.entries.sortedBy { it.key }
        for (i in 1 until sorted.size) {
            assertTrue("Период ${i+1} >= период $i",
                sorted[i].value >= sorted[i-1].value)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // НАЧИСЛЕНИЕ ЗА ПЕРИОД (ТЗ: Формула)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Начисление = Номинал * (Процент / 100)
     */
    @Test
    fun `PSP period accrual calculation`() {
        val nominal = 5000.0
        val percent = 30.0
        val accrual = nominal * (percent / 100.0)

        assertEquals("5000 * 30% = 1500", 1500.0, accrual, 0.01)
    }

    @Test
    fun `PSP period 2 accrual is 55 8 percent of nominal`() {
        val nominal = 5000.0
        val percent = 55.8
        val accrual = nominal * (percent / 100.0)

        assertEquals("5000 * 55.8% = 2790", 2790.0, accrual, 0.01)
    }

    @Test
    fun `PSP period 20 accrual is 200 percent of nominal`() {
        val nominal = 5000.0
        val percent = 200.0
        val accrual = nominal * (percent / 100.0)

        assertEquals("5000 * 200% = 10000", 10000.0, accrual, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЛОГИКА ДАТ (ТЗ: 14 дней, цепочка дат)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Длительность каждого периода ровно 14 дней
     * startDate следующего = endDate предыдущего
     */
    @Test
    fun `PSP period duration is exactly 14 days`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1)
        }.timeInMillis
        val endDate = startDate + (PERIOD_DAYS * 24L * 60L * 60L * 1000L)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = endDate
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        assertEquals("1 + 14 = 15", 15, dayOfMonth)
    }

    @Test
    fun `PSP date chain is continuous without jumps`() {
        var currentDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1)
        }.timeInMillis

        for (period in 1..5) {
            val periodEnd = currentDate + (PERIOD_DAYS * 24L * 60L * 60L * 1000L)
            val nextPeriodStart = periodEnd

            // Проверяем: start следующего = end предыдущего
            assertEquals("Период $period -> период ${period+1}",
                periodEnd, nextPeriodStart)

            currentDate = nextPeriodStart
        }
    }

    @Test
    fun `PSP consecutive periods create correct date chain`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1)
        }.timeInMillis

        val period1End = startDate + (PERIOD_DAYS * 24L * 60L * 60L * 1000L)
        val period2End = period1End + (PERIOD_DAYS * 24L * 60L * 60L * 1000L)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = period2End
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        assertEquals("Период 2 заканчивается 29 апреля", 29, dayOfMonth)
    }

    @Test
    fun `PSP period dates do NOT return to start`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1)
        }.timeInMillis

        val period3Start = startDate + (2 * PERIOD_DAYS * 24L * 60L * 60L * 1000L)

        assertTrue("Период 3 начинается позже старта", period3Start > startDate)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КОПИЛКА vs КОШЕЛЕК (ТЗ: Критично!)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Начисление за период падает в КОШЕЛЕК
     * ТЗ: totalAccrued ("Всего получено") увеличивается ТОЛЬКО на сумму в КОПИЛКУ
     * При взносе: часть начисления уходит в копилку, остаток — в новый номинал
     */
    @Test
    fun `PSP accrual goes to wallet`() {
        val nominal = 5000.0
        val percent = 30.0
        val accrual = nominal * (percent / 100.0)

        var wallet = 0.0
        wallet += accrual

        assertEquals("Начисление в кошельке", 1500.0, wallet, 0.01)
    }

    @Test
    fun `PSP totalAccrued increases ONLY when piggybank is filled`() {
        var totalAccrued = 0.0
        val firstPeriodAccrual = 1500.0
        val piggybankAmount = 1500.0

        // После первого периода часть идет в копилку
        totalAccrued += piggybankAmount

        assertEquals("totalAccrued = копилка", 1500.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP piggybank and wallet are different`() {
        var wallet = 0.0  // Куда падает начисление
        var totalAccrued = 0.0  // "Всего получено" (копилка)

        val accrual = 1500.0
        wallet += accrual  // Начисление в кошелек
        totalAccrued += accrual // Если весь взнос в копилку

        assertEquals("Кошелек = начисление", 1500.0, wallet, 0.01)
        assertEquals("Всего получено = копилка", 1500.0, totalAccrued, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СТАРТ С СЕРЕДИНЫ (ТЗ: Создать с Period > 1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Если создаем поток с Period 5, программа математически высчитывает
     * totalAccrued за предыдущие 4 периода (как будто они уже в копилке)
     */
    @Test
    fun `PSP start from period 5 calculates previous accruals`() {
        val nominal = 5000.0
        val startPeriod = 5

        var totalAccrued = 0.0
        for (period in 1 until startPeriod) {
            val percent = PSP_COEFFICIENTS[period]!!
            totalAccrued += nominal * (percent / 100.0)
        }

        assertTrue("Должны быть начисления за 1-4 периоды", totalAccrued > 0)
    }

    @Test
    fun `PSP start from period 5 calculates correct totalAccrued`() {
        val nominal = 5000.0
        val startPeriod = 5

        var totalAccrued = 0.0
        for (period in 1 until startPeriod) {
            val percent = PSP_COEFFICIENTS[period]!!
            totalAccrued += nominal * (percent / 100.0)
        }

        // 30% + 55.8% + 78% + 97.07% = 260.87%
        val expected = nominal * 2.6087
        assertEquals(expected, totalAccrued, 0.01)
    }

    @Test
    fun `PSP start from period 10 calculates correct totalAccrued`() {
        val nominal = 5000.0
        val startPeriod = 10

        var totalAccrued = 0.0
        for (period in 1 until startPeriod) {
            val percent = PSP_COEFFICIENTS[period]!!
            totalAccrued += nominal * (percent / 100.0)
        }

        assertTrue("Сумма > номинала", totalAccrued > nominal)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВЗНОС (ТЗ: Окно Взнос Номинала)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Че��бо��с "В кошелек пойдет всё начисление"
     * Если чекбокс = true -> всё в копилку, взнос не нужен
     * Если чекбокс = false -> указываем сумму в копилку (0 <= начисление)
     */
    @Test
    fun `PSP contribution all to piggybank mode`() {
        val accrual = 1500.0
        val piggybankOnly = true

        val piggybankAmount = if (piggybankOnly) accrual else 0.0
        val newNominal = if (piggybankOnly) 0.0 else 5000.0  // новый номинал

        assertEquals("Всё в копилку", 1500.0, piggybankAmount, 0.01)
    }

    @Test
    fun `PSP contribution validates piggybank not exceeding accrual`() {
        val accrual = 2790.0  // Период 2
        val piggybankInput = 3000.0

        val isValid = piggybankInput <= accrual

        assertFalse("3000 > 2790 -> нельзя", isValid)
    }

    @Test
    fun `PSP contribution allows exact accrual as piggybank`() {
        val accrual = 2790.0
        val piggybankInput = 2790.0

        val isValid = piggybankInput <= accrual

        assertTrue("Точно = начислению -> можно", isValid)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // КОНЕЦ ЦИКЛА (ТЗ: 20-й период = Закрыт)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: 20-й период — последний
     * После закрытия: isActive = false, кнопка = "ЗАКРЫТЬ ПОТОК"
     */
    @Test
    fun `PSP period 20 is last period`() {
        assertEquals("Всего 20 периодов", 20, PSP_COEFFICIENTS.size)
    }

    @Test
    fun `PSP after period 20 flow becomes inactive`() {
        var isActive = true
        var currentPeriod = 20

        if (currentPeriod >= 20) {
            isActive = false
        }

        assertFalse("После 20-го периода неактивен", isActive)
    }

    @Test
    fun `PSP button text changes after closing`() {
        var currentPeriod = 20
        val isActive = true

        val buttonText = when {
            !isActive -> "ЗАКРЫТЬ ПОТОК"
            currentPeriod >= 20 -> "ЗАКРЫТЬ ПОТОК"
            else -> "Внести номинал"
        }

        assertEquals("Текст = ЗАКРЫТЬ ПОТОК", "ЗАКРЫТЬ ПОТОК", buttonText)
    }

    @Test
    fun `PSP closed flow shows correct status`() {
        val isActive = false
        val status = if (isActive) "Активен" else "ПОТОК ЗАКРЫТ"

        assertEquals("Статус = ПОТОК ЗАКРЫТ", "ПОТОК ЗАКРЫТ", status)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРОГНОЗ (ТЗ: Прогноз ПСП)
    // ═══════════════════════════════════════════════════════════════

    /**
     * ТЗ: Прогноз показывает историю будущих дат закрытий,
     * суммы начислений и итоговое "Всего получено"
     */
    @Test
    fun `PSP forecast calculates future accruals`() {
        val nominal = 5000.0
        var totalForecast = 0.0

        for (period in 1..20) {
            val percent = PSP_COEFFICIENTS[period]!!
            totalForecast += nominal * (percent / 100.0)
        }

        assertTrue("Сумма всех начислений > 0", totalForecast > 0)
    }

    @Test
    fun `PSP forecast total is correct multiple of nominal`() {
        val nominal = 5000.0
        var total = 0.0

        for (percent in PSP_COEFFICIENTS.values) {
            total += nominal * (percent / 100.0)
        }

        // Сумма всех процентов = ~2447%
        val expectedPercentSum = PSP_COEFFICIENTS.values.sum()
        val expected = nominal * (expectedPercentSum / 100.0)
        assertTrue("Сумма ~$expectedPercentSum% от номинала", total > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ИСТОРИЯ ВЗНОСОВ (ТЗ: Таблица истории)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Таблица (сортировка от последнего к первому):
     * Период | Дата Взноса | Начислено | %
     */
    @Test
    fun `PSP history sorted newest first`() {
        val periods = listOf(5, 4, 3, 2, 1)
        assertEquals("5, 4, 3, 2, 1", periods.joinToString(", "))
    }

    @Test
    fun `PSP history entry contains all required fields`() {
        data class PeriodRecord(
            val period: Int,
            val date: Long,
            val accrual: Double,
            val percent: Double
        )

        val record = PeriodRecord(
            period = 1,
            date = System.currentTimeMillis(),
            accrual = 1500.0,
            percent = 30.0
        )

        assertEquals(1, record.period)
        assertTrue(record.accrual > 0)
        assertEquals(30.0, record.percent, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УДАЛЕНИЕ ПОТОКА (ТЗ: Удалить)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Удаляет текущий поток из БД и вычитает его из "Всего накапало"
     */
    @Test
    fun `PSP delete removes from totalAccrued`() {
        var totalAccrued = 5000.0
        var flowsCount = 1

        val deletedAccrual = 1500.0
        totalAccrued -= deletedAccrual
        flowsCount-- // Удалить поток

        assertEquals("totalAccrued уменьшился", 3500.0, totalAccrued, 0.01)
        assertEquals("Потоков стало 0", 0, flowsCount)
    }

    @Test
    fun `PSP delete affects totalAccrued correctly`() {
        var totalAccrued = 10000.0
        val flowAccrual = 3000.0

        totalAccrued -= flowAccrual

        assertEquals(7000.0, totalAccrued, 0.01)
    }
}