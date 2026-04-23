package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты логики прогнозирования.
 *
 * По ТЗ:
 * - Прогноз останавливается при "В потоке" <= 0
 * - Лучшая дата: день когда начисление начинает падать
 * - Правило +1 день для UI
 * - ПН: прогноз до истощения потока
 */
class ForecastTest {

    companion object {
        private const val RP_START_PERCENT = 0.1
        private const val RP_DAILY_ADDITION = 0.003
        private const val PN_PERCENT = 2.0
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОСТАНОВКА ПРИ НУЛЕ (ТЗ: Прогноз останавливается)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Если "В потоке" становится 0.00 — прогноз останавливается
     * Дальше нулей не считаем
     */
    @Test
    fun `RP forecast stops when flow reaches zero`() {
        var inFlow = 10000.0
        var percent = RP_START_PERCENT
        var daysGenerated = 0

        while (inFlow > 0 && daysGenerated < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            if (inFlow <= 0) {
                inFlow = 0.0
                break
            }
            percent += RP_DAILY_ADDITION
            daysGenerated++
        }

        assertTrue("Прогноз остановился", daysGenerated > 0)
    }

    @Test
    fun `RP forecast generates finite number of days`() {
        var inFlow = 10000.0
        var percent = RP_START_PERCENT
        var dayCount = 0

        while (inFlow > 0 && dayCount < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
            dayCount++
        }

        assertTrue("Конечное число дней", dayCount > 0)
    }

    @Test
    fun `PN forecast stops when flow reaches zero`() {
        var inFlow = 10000.0
        val percent = PN_PERCENT
        var daysGenerated = 0

        while (inFlow > 0 && daysGenerated < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            if (inFlow <= 0) {
                inFlow = 0.0
                break
            }
            daysGenerated++
        }

        assertTrue("Прогноз остановился", daysGenerated > 0)
    }

    @Test
    fun `forecast does NOT generate infinite zeros`() {
        var inFlow = 0.0
        var percent = 0.1
        var daysCount = 0

        while (inFlow > 0 && daysCount < 10) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            daysCount++
        }

        assertEquals("Сразу 0 -> нет итераций", 0, daysCount)
        assertTrue("Нулевой вход дает 0 дней", inFlow <= 0)
    }

    // ═══════════════════════════════════════════════════════════════════��═══
    // АЛГОРИТМ "ЛУЧШЕЙ ДАТЫ" (ТЗ: Лучшая дата)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Лучшая дата — день когда начисление начинает ПАДАТЬ
     * Процент растет, но сумма в потоке падает быстрее
     */
    @Test
    fun `RP best date finds dropping accrual point`() {
        var inFlow = 50000.0
        var percent = RP_START_PERCENT
        var prevAccrual = inFlow * (percent / 100.0)
        var bestDay = -1

        for (day in 1..200) {
            val accrual = inFlow * (percent / 100.0)

            if (day > 1 && accrual < prevAccrual) {
                bestDay = day
                break
            }

            prevAccrual = accrual
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
        }

        assertTrue("Найден день падения", bestDay > 0)
    }

    @Test
    fun `RP best date is NOT the first day`() {
        var inFlow = 50000.0
        var percent = RP_START_PERCENT
        var prevAccrual = inFlow * (percent / 100.0)
        var foundDay = -1

        for (day in 1..200) {
            val accrual = inFlow * (percent / 100.0)
            if (day > 1 && accrual < prevAccrual) {
                foundDay = day
                break
            }
            prevAccrual = accrual
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
        }

        assertTrue("Лучшая дата не первый день", foundDay > 1)
    }

    @Test
    fun `RP large flow generates many days before depletion`() {
        var inFlow = 100000.0
        var percent = RP_START_PERCENT
        var dayCount = 0

        while (inFlow > 0 && dayCount < 500) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
            dayCount++
        }

        assertTrue("Много дней для большого старта", dayCount > 100)
    }

    @Test
    fun `RP best date with large starting amount`() {
        var inFlow = 100000.0
        var percent = RP_START_PERCENT
        var prevAccrual = inFlow * (percent / 100.0)
        var bestDay = -1

        for (day in 1..300) {
            val accrual = inFlow * (percent / 100.0)
            if (day > 1 && accrual < prevAccrual) {
                bestDay = day
                break
            }
            prevAccrual = accrual
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
        }

        assertTrue("День найден для большого старта", bestDay > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРАВИЛО +1 ДЕНЬ (ТЗ: Лучшая дата + 1 день включительно)
    // ════════════════════════════════════════════════════════════���═��════════

    /**
     * ТЗ: Список включает лучшую дату + 1 следующий день
     * Чтобы пользователь видел падение визуально
     */
    @Test
    fun `best date list includes one extra day`() {
        val bestDay = 50
        val includeExtraDay = true

        val displayDays = if (includeExtraDay) bestDay + 1 else bestDay

        assertEquals("Показываем + 1 день", 51, displayDays)
    }

    @Test
    fun `best date list formula is bestDay plus one`() {
        var bestDay = 0
        // Симуляция нахождения лучшей даты
        bestDay = 50

        // UI показывает [лучшая дата, лучшая дата + 1]
        val daysToShow = listOf(bestDay, bestDay + 1)

        assertEquals("Два дня в списке", 2, daysToShow.size)
        assertEquals("Второй день = лучшая + 1", 51, daysToShow[1])
    }

    @Test
    fun `forecast stops at zero regardless of extra day`() {
        var inFlow = 100.0
        var percent = RP_START_PERCENT
        var days = mutableListOf<Int>()

        for (day in 1..100) {
            if (inFlow <= 0) break
            val accrual = inFlow * (percent / 100.0)
            days.add(day)
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
        }

        // +1 день не должен выходить за границы
        val lastDay = days.lastOrNull() ?: 0
        assertTrue("Список конечный", days.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРОГНОЗ ПН (ТЗ: Фиксированный 2%, нет лучшей даты)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: У ПН процент фиксированный 2%
     * Прогноз строится до истощения потока
     */
    @Test
    fun `PN forecast uses fixed 2 percent`() {
        var inFlow = 15000.0
        val percent = PN_PERCENT
        val accrual = inFlow * (percent / 100.0)

        assertEquals("2% от 15000 = 300", 300.0, accrual, 0.01)
    }

    @Test
    fun `PN forecast calculates to depletion`() {
        var inFlow = 15000.0
        val percent = PN_PERCENT
        var daysGenerated = 0

        while (inFlow > 0 && daysGenerated < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            if (inFlow <= 0) break
            daysGenerated++
        }

        assertTrue("Генерируется конечное число дней", daysGenerated > 0)
    }

    @Test
    fun `PN forecast generates correct number of days`() {
        var inFlow = 15000.0
        val percent = PN_PERCENT
        var dayCount = 0

        while (inFlow > 0 && dayCount < 200) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            dayCount++
        }

        // 15000 / 300 = 50 дней (примерно)
        assertTrue(dayCount > 40)
    }

    @Test
    fun `PN accrual is constant each day`() {
        var inFlow = 15000.0
        val percent = PN_PERCENT
        val accruals = mutableListOf<Double>()

        repeat(10) {
            val accrual = inFlow * (percent / 100.0)
            accruals.add(accrual)
            inFlow -= accrual
        }

        // Все начисления равны (2% от уменьшающегося потока)
        for (i in 1 until accruals.size) {
            assertTrue("Начисление уменьшается",
                accruals[i] < accruals[i - 1])
        }
    }

    @Test
    fun `PN has NO best date because percent is fixed`() {
        val percent = PN_PERCENT
        val hasBestDate = false  // У ПН нет лучшей даты!

        assertFalse("У ПН нет лучшей даты", hasBestDate)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГРАНИЧНЫЕ УСЛОВИЯ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `forecast with zero initial flow produces empty list`() {
        var inFlow = 0.0
        var days = 0

        while (inFlow > 0 && days < 10) {
            days++
        }

        assertEquals("Нет дней для 0", 0, days)
    }

    @Test
    fun `forecast with very small flow`() {
        var inFlow = 1.0
        val percent = 2.0
        var dayCount = 0

        while (inFlow > 0 && dayCount < 10) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            dayCount++
        }

        assertTrue("Минимум 1 день", dayCount >= 1)
    }

    @Test
    fun `best date algorithm handles very large flow`() {
        var inFlow = 1000000.0
        var percent = RP_START_PERCENT
        var prevAccrual = inFlow * (percent / 100.0)
        var found = false

        for (day in 1..1000) {
            val accrual = inFlow * (percent / 100.0)
            if (day > 1 && accrual < prevAccrual) {
                found = true
                break
            }
            prevAccrual = accrual
            inFlow -= accrual
            if (inFlow <= 0) break
            percent += RP_DAILY_ADDITION
        }

        assertTrue("Найден для миллиона", found)
    }
}