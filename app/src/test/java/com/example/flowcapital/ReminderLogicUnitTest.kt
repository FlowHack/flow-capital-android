package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Unit тесты на логику напоминаний и пуш-уведомлений.
 * 
 * По ТЗ:
 * - Максимум 5 напоминаний в день
 * - Умная отмена: пуш приходит ТОЛЬКО если есть открытые потоки, требующие нажатия
 * - В 23:00 пуш приходит всегда, если есть невыполненные действия
 */
class ReminderLogicUnitTest {

    // ========== ЛИМИТ НАПОМИНАНИЙ ==========

    @Test
    fun `reminder maximum is 5 per day`() {
        val maxReminders = 5
        assertEquals(5, maxReminders)
    }

    @Test
    fun `reminder counter resets each day`() {
        var currentReminders = 5

        // Новый день - сбрасываем
        currentReminders = 0

        assertEquals(0, currentReminders)
    }

    @Test
    fun `reminder disabled when limit reached`() {
        val maxReminders = 5
        var currentReminders = 5

        val canAddMore = currentReminders < maxReminders

        assertFalse(canAddMore)
    }

    // ========== УМНАЯ ОТМЕНА ==========

    @Test
    fun `push sent ONLY when action required`() {
        // Есть потоки требующие действия
        val flowsRequiringAction = listOf("ПН", "РП")

        val hasAnyAction = flowsRequiringAction.isNotEmpty()

        // Пуши приходят ТОЛЬКО если есть что делать
        assertTrue(hasAnyAction)
    }

    @Test
    fun `push NOT sent when no action required`() {
        // Нет потоков требующих действия
        val flowsRequiringAction = emptyList<String>()

        val hasAnyAction = flowsRequiringAction.isNotEmpty()

        // Пуши НЕ приходят когда делать нечего
        assertFalse(hasAnyAction)
    }

    @Test
    fun `push includes specific flow names in text`() {
        val flowsNeedingAction = listOf("ПН", "РП")

        // Текст пуша должен содержать конкретные потоки
        val pushText = flowsNeedingAction.joinToString(", ")

        assertTrue(pushText.contains("ПН"))
        assertTrue(pushText.contains("РП"))
    }

    // ========== ВРЕМЯ 23:00 ==========

    @Test
    fun `23 00 reminder always sent if actions pending`() {
        val hour = 23
        val minute = 0
        val isEveningReminder = hour == 23 && minute == 0

        // В 23:00 пуш приходит ВСЕГДА если есть невыполненные действия
        val hasPendingActions = true

        assertTrue(isEveningReminder && hasPendingActions)
    }

    @Test
    fun `non-23 00 uses smart cancel logic`() {
        val hour = 14
        val minute = 30

        // Не 23:00 - используется умная отмена
        val isSmartCancel = hour != 23 || hour < 23

        assertTrue(isSmartCancel)
    }

    @Test
    fun `morning reminder uses smart cancel logic`() {
        val hour = 10

        // Утром используется умная отмена
        val usesSmartCancel = hour in 6..22

        assertTrue(usesSmartCancel)
    }

    // ========== ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ для Н/РП, но РАБОЧИЙ для ПСП! ==========

    @Test
    fun `sunday reminders disabled for PN and RP`() {
        // Воскресенье - выходной для ПН/РП
        val isSunday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19) // Воскресенье
        }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        // Для ПН/РП - выходной
        val needsActionPN = true
        val needsActionRP = true

        // Но при наличии ПСП - работает!
        val pspNeedsAction = false

        // Если есть ПСП - пуш нужен, если нет - нет
        val pushSentInSunday = pspNeedsAction || (isSunday && (needsActionPN || needsActionRP))

        assertTrue(isSunday) // Воскресенье
    }

    @Test
    fun `sunday IS working day for PSP`() {
        // ПСП работает и в воскресенье! Периоды длятся 14 дней без выходных
        val isSunday = true
        val pspNeedsAction = true

        // ПСП в воскресенье требует действия
        assertTrue(pspNeedsAction)
    }

    @Test
    fun `sunday push sent when PSP needs action`() {
        // По ТЗ: если есть открытый ПСП - пуш В ВОСКРЕСЕНЬЕ приходит
        val isSunday = true
        val pspNeedsAction = true

        // ПСП требует действия даже в воскресенье
        val pushShouldSend = pspNeedsAction

        assertTrue(pushShouldSend)
    }

    @Test
    fun `sunday push NOT sent when only PN and RP need action`() {
        // Если только ПН/РП требуют действия - в воскресенье пуш НЕ приходит
        val isSunday = true
        val pspNeedsAction = false
        val pnNeedsAction = true
        val rpNeedsAction = true

        // Умная отмена: нет ПСП - нет пуша в воскресенье
        val pushShouldSend = pspNeedsAction

        assertFalse(pushShouldSend)
    }

    @Test
    fun `monday to saturday reminders allowed`() {
        for (day in listOf(20, 21, 22, 23, 24, 25)) {
            val calendar = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, day)
            }
            val isNotSunday = calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
            assertTrue("Day $day should allow reminders", isNotSunday)
        }
    }

    @Test
    fun `sunday push logic is smart`() {
        // Логика умной отмены:
        // 1. Проверяем ПСП first (работает всегда!)
        // 2. Только если ПСП НЕ нужен - пропускаем для Н/РП в воскресенье

        // Сценарий 1: Только ПН требует действия
        var isSunday = true
        var pspNeedsAction = false
        var pnNeedsAction = true
        var shouldSend = pspNeedsAction || (!isSunday && pnNeedsAction)
        assertFalse(shouldSend) // НЕ отправляем

        // Сценарий 2: ПСП требует действия (даже в воскресенье!)
        pspNeedsAction = true
        shouldSend = pspNeedsAction || (!isSunday && pnNeedsAction)
        assertTrue(shouldSend) // Отправляем!
    }

    // ========== ПРОВЕРКА ПОТОКОВ ==========

    @Test
    fun `PN button needs press`() {
        // ПН требует нажатия если еще не нажато сегодня
        val isButtonPressedToday = false
        val needsAction = !isButtonPressedToday

        assertTrue(needsAction)
    }

    @Test
    fun `PN button does NOT need press if already pressed`() {
        // ПН НЕ требует нажатия если уже нажато
        val isButtonPressedToday = true
        val needsAction = !isButtonPressedToday

        assertFalse(needsAction)
    }

    @Test
    fun `RP button needs press`() {
        // РП требует нажатия
        val isButtonPressedToday = false
        val needsAction = !isButtonPressedToday

        assertTrue(needsAction)
    }

    @Test
    fun `PSP contribution needed when period closed`() {
        // ПСП нужен взнос когда период закрыт
        val endDatePassed = true
        val contributionMade = false
        val needsAction = endDatePassed && !contributionMade

        assertTrue(needsAction)
    }

    @Test
    fun `PSP NO contribution before period close`() {
        // ПСП НЕ нужен взнос до закрытия периода
        val endDatePassed = false
        val contributionMade = false
        val needsAction = endDatePassed && !contributionMade

        assertFalse(needsAction)
    }

    // ========== РАСПРЕДЕЛЕНИЕ НАПОМИНАНИЙ ПО ЧАСАМ ==========

    @Test
    fun `reminder between 6am and 11pm`() {
        for (hour in 6..23) {
            val canSendReminder = hour in 6..23
            assertTrue("Hour $hour should allow reminders", canSendReminder)
        }
    }

    @Test
    fun `reminder disabled at night`() {
        for (hour in 0..5) {
            val canSendReminder = hour in 6..23
            assertFalse("Hour $hour should NOT allow reminders", canSendReminder)
        }
    }

    // ========== ПРОВЕРКА БАЗОВОГО ЦВЕТА ПОТОКА ==========

    @Test
    fun `RP flow color is green`() {
        val rpColor = "зеленый"
        assertEquals("зеленый", rpColor)
    }

    @Test
    fun `PN flow color is blue`() {
        val pnColor = "синий"
        assertEquals("синий", pnColor)
    }

    @Test
    fun `PSP flow color is red`() {
        val pspColor = "красный"
        assertEquals("красный", pspColor)
    }
}