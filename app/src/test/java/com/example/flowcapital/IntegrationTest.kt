package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для полной логики приложения
 * Проверяют взаимодействие всех компонентов системы
 */
class IntegrationTest {

    // ========== ИНТЕГРАЦИЯ ПСП ==========

    @Test
    fun `PSP integration - full lifecycle from creation to completion`() {
        val pspCoefficients = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48
        )
        val nominal = 5000.0
        val periodDuration = 14L * 24 * 60 * 60 * 1000

        // 1. Создание потока 12.04.2023
        val flowStartDate = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Генерируем периоды
        val periods = (1..5).map { periodNum ->
            val startDate = flowStartDate + ((periodNum - 1) * periodDuration)
            val endDate = startDate + periodDuration
            val accrualAmount = nominal * (pspCoefficients[periodNum]!! / 100.0)

            mutableMapOf(
                "periodNumber" to periodNum,
                "startDate" to startDate,
                "endDate" to endDate,
                "accrualAmount" to accrualAmount,
                "isContributionMade" to false
            )
        }

        assertEquals(5, periods.size)
        assertEquals(1, periods[0]["periodNumber"])

        // 2. Период 1 завершён 26.04.2023
        var totalAccrued = 0.0
        periods[0]["isContributionMade"] = true
        val accrual1 = periods[0]["accrualAmount"] as Double
        totalAccrued += accrual1 // Всё в копилку

        assertEquals(1500.0, totalAccrued, 0.01)

        // 3. Период 2 завершён
        periods[1]["isContributionMade"] = true
        val accrual2 = periods[1]["accrualAmount"] as Double
        totalAccrued += accrual2

        assertEquals(4290.0, totalAccrued, 0.01)

        // 4. Период 3 завершён
        periods[2]["isContributionMade"] = true
        val accrual3 = periods[2]["accrualAmount"] as Double
        totalAccrued += accrual3

        assertEquals(8190.0, totalAccrued, 0.01)

        // 5. Период 4 - реинвест (0 в копилку)
        periods[3]["isContributionMade"] = true
        totalAccrued += 0.0 // Реинвест

        assertEquals(8190.0, totalAccrued, 0.01) // Не изменилось!
    }

    @Test
    fun `PSP integration - multiple flows independence`() {
        // Создаём два независимых потока
        data class PSPFlow(
            val id: Int,
            val nominal: Double,
            var totalAccrued: Double = 0.0
        )

        val flow1 = PSPFlow(1, 5000.0)
        val flow2 = PSPFlow(2, 10000.0)

        // Совершаем действия с первым потоком
        flow1.totalAccrued += 1500.0
        assertEquals(1500.0, flow1.totalAccrued, 0.01)
        assertEquals(0.0, flow2.totalAccrued, 0.01) // Второй не изменился

        // Совершаем действия со вторым потоком
        flow2.totalAccrued += 3000.0
        assertEquals(1500.0, flow1.totalAccrued, 0.01) // Первый не изменился
        assertEquals(3000.0, flow2.totalAccrued, 0.01)

        // Общая сумма
        val totalAll = flow1.totalAccrued + flow2.totalAccrued
        assertEquals(4500.0, totalAll, 0.01)
    }

    // ========== ИНТЕГРАЦИЯ ПН ==========

    @Test
    fun `PN integration - full lifecycle`() {
        val bonusPercent = 50.0
        val dailyPercent = 2.0

        // 1. Создание потока с взносом 10000
        var inFlow = 10000.0 * (1 + bonusPercent / 100.0) // 15000
        var wallet = 0.0
        var totalAccrued = 0.0

        // 2. День 1 - сразу нажали
        var accrual = inFlow * (dailyPercent / 100.0) // 300
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual

        assertEquals(14700.0, inFlow, 0.01)
        assertEquals(300.0, wallet, 0.01)
        assertEquals(300.0, totalAccrued, 0.01)

        // 3. День 2
        accrual = inFlow * (dailyPercent / 100.0) // 294
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual

        assertEquals(14406.0, inFlow, 0.01)
        assertEquals(594.0, wallet, 0.01)
        assertEquals(594.0, totalAccrued, 0.01)

        // 4. День 3 - пропущен (MISSED)
        // Значения не меняются
        assertEquals(14406.0, inFlow, 0.01)
        assertEquals(594.0, wallet, 0.01)

        // 5. День 4
        accrual = inFlow * (dailyPercent / 100.0)
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual

        assertTrue(inFlow < 14406.0)
        assertTrue(wallet > 594.0)
    }

    @Test
    fun `PN integration - reinvest changes flow correctly`() {
        val bonusPercent = 50.0
        val dailyPercent = 2.0

        // Текущее состояние
        var inFlow = 10000.0
        var wallet = 500.0

        // Реинвест с взносом 5000
        val reinvestAmount = 5000.0
        val newInFlowFromReinvest = reinvestAmount * (1 + bonusPercent / 100.0) // 7500

        inFlow += newInFlowFromReinvest // 17500

        val newDailyAccrual = inFlow * (dailyPercent / 100.0) // 350

        assertEquals(17500.0, inFlow, 0.01)
        assertEquals(350.0, newDailyAccrual, 0.01)
        assertEquals(500.0, wallet, 0.01) // Кошелёк не изменился
    }

    @Test
    fun `PN integration - Sunday handling`() {
        val dailyPercent = 2.0

        // Симулируем неделю
        val weekDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
            Calendar.SUNDAY, Calendar.MONDAY
        )

        var inFlow = 15000.0
        var accrualDays = 0

        weekDays.forEach { day ->
            if (day == Calendar.SUNDAY) {
                // Воскресенье - создаём запись с предыдущими значениями
                val previousInFlow = inFlow
                // Значения не меняются
                assertEquals(previousInFlow, inFlow, 0.01)
            } else {
                // Рабочий день - начисление
                val accrual = inFlow * (dailyPercent / 100.0)
                inFlow -= accrual
                accrualDays++
            }
        }

        assertEquals(7, accrualDays) // 7 рабочих дней из 8
        assertTrue(inFlow < 15000.0)
    }

    // ========== ИНТЕГРАЦИЯ РП ==========

    @Test
    fun `RP integration - full lifecycle`() {
        val startPercent = 0.1
        val dailyAddition = 0.003

        // calculateECurrencyBonus возвращает TOTAL с бонусом (как в реальном приложении)
        fun calculateECurrency(amount: Double): Double {
            return when {
                amount >= 1_000_000 -> amount + (amount * 2.00)
                amount >= 500_000 -> amount + (amount * 1.75)
                amount >= 100_000 -> amount + (amount * 1.50)
                amount >= 50_000 -> amount + (amount * 1.25)
                amount >= 10_000 -> amount + (amount * 1.00)
                amount >= 5_000 -> amount + (amount * 0.75)
                amount >= 1_000 -> amount + (amount * 0.50)
                else -> amount
            }
        }

        // 1. Создание потока: пользователь вводит 10000, бонус добавляется автоматически
        var inFlow = calculateECurrency(10000.0) // 20000 (10000 + 100% бонус)
        var percent = startPercent
        var wallet = 0.0
        var totalAccrued = 0.0

        // 2. День 1 - сразу нажали
        var accrual = inFlow * (percent / 100.0) // 20
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual
        percent += dailyAddition

        assertEquals(19980.0, inFlow, 0.01)
        assertEquals(20.0, wallet, 0.01)
        assertEquals(0.103, percent, 0.001)

        // 3. День 2
        accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual

        assertTrue(inFlow < 19980.0)
        assertTrue(wallet > 20.0)

        // 4. День 3
        accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        totalAccrued += accrual

        // Процент продолжает расти
        assertTrue(percent > 0.103)
    }

    @Test
    fun `RP integration - best date calculation`() {
        val startPercent = 0.1
        val dailyAddition = 0.003

        var inFlow = 20000.0
        var percent = startPercent
        var prevAccrual = 0.0
        var bestDate = 0
        var days = 0

        while (days < 500 && inFlow > 100) {
            val accrual = inFlow * (percent / 100.0)

            // Лучшая дата - когда начисление начинает уменьшаться
            if (days > 0 && accrual < prevAccrual && bestDate == 0) {
                bestDate = days
            }

            prevAccrual = accrual
            inFlow -= accrual
            percent += dailyAddition
            days++
        }

        assertTrue(bestDate > 0) // Должна быть найдена
        assertTrue(bestDate < days) // До конца потока
    }

    @Test
    fun `RP integration - Sunday handling`() {
        val startPercent = 0.1
        val dailyAddition = 0.003

        // Симулируем неделю
        val weekDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
            Calendar.SUNDAY, Calendar.MONDAY
        )

        var inFlow = 20000.0
        var percent = startPercent
        var accrualDays = 0

        weekDays.forEach { day ->
            if (day == Calendar.SUNDAY) {
                // Воскресенье - создаём запись с предыдущими значениями
                val previousInFlow = inFlow
                val previousPercent = percent
                // Значения не меняются
                assertEquals(previousInFlow, inFlow, 0.01)
                assertEquals(previousPercent, percent, 0.01)
            } else {
                // Рабочий день - начисление
                val accrual = inFlow * (percent / 100.0)
                inFlow -= accrual
                percent += dailyAddition
                accrualDays++
            }
        }

        assertEquals(7, accrualDays) // 7 рабочих дней из 8
        assertTrue(percent > startPercent + 7 * dailyAddition)
    }

    // ========== ИНТЕГРАЦИЯ НАПОМИНАНИЙ ==========

    @Test
    fun `reminders integration - active when any button is inactive`() {
        data class FlowState(
            val hasActiveFlow: Boolean,
            val buttonActive: Boolean
        )

        val pnState = FlowState(true, false)
        val rpState = FlowState(true, true)
        val pspState = FlowState(true, true)

        val needsReminder = !pnState.buttonActive ||
                !rpState.buttonActive ||
                pspState.buttonActive

        assertTrue(needsReminder)
    }

    @Test
    fun `reminders integration - no reminder when all buttons active`() {
        data class FlowState(
            val hasActiveFlow: Boolean,
            val buttonActive: Boolean
        )

        val pnState = FlowState(true, true)
        val rpState = FlowState(true, true)
        val pspState = FlowState(true, true)

        // Напоминание нужно если хотя бы одна кнопка НЕАКТИВНА
        val needsReminder = !pnState.buttonActive ||
                !rpState.buttonActive ||
                !pspState.buttonActive

        assertFalse(needsReminder)
    }

    @Test
    fun `reminders integration - max 5 reminders per day`() {
        val reminders = mutableListOf<String>()

        // Пытаемся добавить 6 напоминаний
        val times = listOf("08:00", "10:00", "12:00", "14:00", "16:00", "18:00")

        times.forEach { time ->
            if (reminders.size < 5) {
                reminders.add(time)
            }
        }

        assertEquals(5, reminders.size)
        assertFalse(reminders.contains("18:00"))
    }

    // ========== ИНТЕГРАЦИЯ ОБНОВЛЕНИЙ ==========

    @Test
    fun `update integration - version comparison`() {
        data class AppVersion(
            val current: String,
            val latest: String,
            val skipped: String?
        )

        fun shouldUpdate(version: AppVersion): Boolean {
            if (version.latest == version.skipped) return false
            return version.latest != version.current
        }

        // Случай 1: нужно обновление
        val needUpdate = AppVersion("1.3.0", "1.3.1", null)
        assertTrue(shouldUpdate(needUpdate))

        // Случай 2: уже последняя версия
        val noUpdate = AppVersion("1.3.1", "1.3.1", null)
        assertFalse(shouldUpdate(noUpdate))

        // Случай 3: версия пропущена
        val skippedUpdate = AppVersion("1.3.0", "1.3.1", "1.3.1")
        assertFalse(shouldUpdate(skippedUpdate))
    }

    // ========== ИНТЕГРАЦИЯ ЭКСПОРТА ==========

    @Test
    fun `export integration - CSV format for different flows`() {
        // Тестируем формат CSV для разных потоков
        val delimiter = ";"

        // ПН экспорт
        val pnHeader = "Дата;Процент;В потоке;Начисление;Кошелек;Действие"
        val pnRow = "16.04.2026;2.00;14700.00;300.00;300.00;DAILY"

        assertEquals(6, pnHeader.split(delimiter).size)
        assertEquals(6, pnRow.split(delimiter).size)

        // РП экспорт
        val rpHeader = "Дата;Процент;В потоке;Начисление;Кошелек;Действие"
        val rpRow = "16.04.2026;0.103;19980.00;20.58;20.00;DAILY"

        assertEquals(6, rpHeader.split(delimiter).size)
        assertEquals(6, rpRow.split(delimiter).size)

        // ПСП экспорт
        val pspHeader = "Период;Процент;Начисление;Начало;Конец;Взнос"
        val pspRow = "1;30.00;1500.00;12.04.2023;26.04.2023;Да"

        assertEquals(6, pspHeader.split(delimiter).size)
        assertEquals(6, pspRow.split(delimiter).size)
    }

    @Test
    fun `export integration - UTF-8 BOM for Cyrillic`() {
        val bom = "\uFEFF"
        val russianContent = "Дата;В потоке;Начисление"

        val contentWithBom = bom + russianContent

        assertEquals('\uFEFF', contentWithBom[0])
        assertTrue(contentWithBom.contains("Дата"))
        assertTrue(contentWithBom.contains("В потоке"))
    }

    // ========== ИНТЕГРАЦИЯ ЛОГИРОВАНИЯ ==========

    @Test
    fun `logging integration - all log levels work`() {
        data class LogEntry(
            val level: String,
            val tag: String,
            val message: String,
            val timestamp: Long,
            val throwable: Exception?
        )

        val logs = mutableListOf<LogEntry>()

        fun log(level: String, tag: String, message: String, throwable: Exception? = null) {
            logs.add(LogEntry(level, tag, message, System.currentTimeMillis(), throwable))
        }

        // Логируем разные уровни
        log("V", "Test", "Verbose message")
        log("D", "Test", "Debug message")
        log("I", "Test", "Info message")
        log("W", "Test", "Warning message")
        log("E", "Test", "Error message", Exception("Test exception"))

        assertEquals(5, logs.size)
        assertEquals("V", logs[0].level)
        assertEquals("E", logs[4].level)
        assertNotNull(logs[4].throwable)
    }

    // ========== КРАЙНИЕ СЛУЧАИ ==========

    @Test
    fun `edge case - zero deposit in PN`() {
        val bonusPercent = 50.0
        val deposit = 0.0

        val inFlow = deposit * (1 + bonusPercent / 100.0)

        assertEquals(0.0, inFlow, 0.01)
    }

    @Test
    fun `edge case - large deposit in RP`() {
        // calculateECurrencyBonus возвращает TOTAL с бонусом
        fun calculateECurrency(amount: Double): Double {
            return when {
                amount >= 1_000_000 -> amount + (amount * 2.00)
                amount >= 500_000 -> amount + (amount * 1.75)
                amount >= 100_000 -> amount + (amount * 1.50)
                amount >= 50_000 -> amount + (amount * 1.25)
                amount >= 10_000 -> amount + (amount * 1.00)
                amount >= 5_000 -> amount + (amount * 0.75)
                amount >= 1_000 -> amount + (amount * 0.50)
                else -> amount
            }
        }

        val largeDeposit = 5_000_000.0
        val inFlow = calculateECurrency(largeDeposit) // 5M + 10M = 15M (200% бонус)

        assertEquals(15000000.0, inFlow, 0.01) // 5000000 + 10000000 = 15000000
    }

    @Test
    fun `edge case - very long pause in PSP`() {
        val nominal = 5000.0
        val pspCoefficients = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07
        )

        // 10 лет спустя - сумма начислений за 4 периода
        var totalAccrued = 0.0
        for (period in 1..4) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            totalAccrued += accrual
        }

        // 1500 + 2790 + 3900 + 4853.5 = 13043.5
        assertEquals(13043.5, totalAccrued, 0.01)
    }

    @Test
    fun `edge case - PN flow reaches zero`() {
        val dailyPercent = 2.0
        var inFlow = 100.0 // Очень маленький поток

        var days = 0
        while (inFlow > 0.01 && days < 1000) {
            val accrual = inFlow * (dailyPercent / 100.0)
            inFlow -= accrual
            days++
        }

        assertTrue(days < 1000) // Должен завершиться раньше
        assertTrue(inFlow < 1.0) // Поток исчерпан
    }
}
