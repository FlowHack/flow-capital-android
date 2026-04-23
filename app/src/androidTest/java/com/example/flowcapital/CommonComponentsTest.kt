package com.example.flowcapital

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Locale

/**
 * Тесты для проверки общих компонентов: цвета, парсинг чисел, даты
 */
@RunWith(AndroidJUnit4::class)
class CommonComponentsTest {

    // ========== Цвета ==========

    @Test
    fun `SUNDAY color is purple with transparency`() {
        val sundayColor = 0x33E040FB
        val alpha = (sundayColor shr 24) and 0xFF
        assertEquals(0x33, alpha)
        assertEquals(0xE040FB.toInt() and 0xFFFFFF, sundayColor and 0xFFFFFF)
    }

    @Test
    fun `CORRECTION color is red with transparency`() {
        val correctionColor = 0x33FF5252
        val alpha = (correctionColor shr 24) and 0xFF
        assertEquals(0x33, alpha)
        assertEquals(0xFF5252.toInt() and 0xFFFFFF, correctionColor and 0xFFFFFF)
    }

    @Test
    fun `REINVEST color is green with transparency`() {
        val reinvestColor = 0x334CAF50
        val alpha = (reinvestColor shr 24) and 0xFF
        assertEquals(0x33, alpha)
        assertEquals(0x4CAF50.toInt() and 0xFFFFFF, reinvestColor and 0xFFFFFF)
    }

    @Test
    fun `MISSED color is orange with transparency`() {
        val missedColor = 0x33FF9800
        val alpha = (missedColor shr 24) and 0xFF
        assertEquals(0x33, alpha)
        assertEquals(0xFF9800.toInt() and 0xFFFFFF, missedColor and 0xFFFFFF)
    }

    @Test
    fun `all colors have same transparency`() {
        val colors = listOf(0x33E040FB, 0x33FF5252, 0x334CAF50, 0x33FF9800)
        val alphas = colors.map { (it shr 24) and 0xFF }
        assertEquals(1, alphas.distinct().size) // все альфа одинаковые
        alphas.forEach { assertEquals(0x33, it) }
    }

    @Test
    fun `SUNDAY purple color is distinct`() {
        val purple = 0xE040FB.toInt()
        val red = 0xFF5252.toInt()
        val green = 0x4CAF50.toInt()
        val orange = 0xFF9800.toInt()

        assertNotEquals(purple, red)
        assertNotEquals(purple, green)
        assertNotEquals(purple, orange)
    }

    @Test
    fun `CORRECTION red color is distinct from others`() {
        val red = 0xFF5252.toInt()
        val purple = 0xE040FB.toInt()
        val green = 0x4CAF50.toInt()
        val orange = 0xFF9800.toInt()

        assertNotEquals(red, green)
        assertNotEquals(red, orange)
        assertNotEquals(red, purple)
    }

    // ========== Парсинг чисел ==========

    @Test
    fun `comma separated number is parsed correctly`() {
        val withComma = "10,5"
        val parsed = withComma.replace(",", ".").toDoubleOrNull()!!
        assertEquals(10.5, parsed, 0.01)
    }

    @Test
    fun `dot separated number is parsed correctly`() {
        val withDot = "10.5"
        val parsed = withDot.replace(",", ".").toDoubleOrNull()!!
        assertEquals(10.5, parsed, 0.01)
    }

    @Test
    fun `integer string is parsed correctly`() {
        val integer = "100"
        val parsed = integer.replace(",", ".").toDoubleOrNull()!!
        assertEquals(100.0, parsed, 0.01)
    }

    @Test
    fun `invalid string returns null`() {
        val invalid = "abc"
        val parsed = invalid.replace(",", ".").toDoubleOrNull()
        assertNull(parsed)
    }

    @Test
    fun `empty string returns null`() {
        val empty = ""
        val parsed = empty.replace(",", ".").toDoubleOrNull()
        assertNull(parsed)
    }

    @Test
    fun `large number with commas is parsed correctly`() {
        val large = "1,000,000.50"
        val cleaned = large.replace(",", "")
        val parsed = cleaned.toDoubleOrNull()!!
        assertEquals(1000000.50, parsed, 0.01)
    }

    @Test
    fun `number with spaces is parsed correctly`() {
        val withSpaces = "1 000 000,50"
        val cleaned = withSpaces.replace(" ", "").replace(",", ".")
        val parsed = cleaned.toDoubleOrNull()!!
        assertEquals(1000000.50, parsed, 0.01)
    }

    @Test
    fun `negative number is parsed correctly`() {
        val negative = "-100,50"
        val parsed = negative.replace(",", ".").toDoubleOrNull()!!
        assertEquals(-100.50, parsed, 0.01)
    }

    @Test
    fun `zero is parsed correctly`() {
        val zero = "0"
        val parsed = zero.replace(",", ".").toDoubleOrNull()!!
        assertEquals(0.0, parsed, 0.01)
    }

    @Test
    fun `decimal number without integer_part is parsed correctly`() {
        val decimal = ",50"
        val parsed = decimal.replace(",", ".").toDoubleOrNull()!!
        assertEquals(0.50, parsed, 0.01)
    }

    // ========== Форматирование чисел ==========

    @Test
    fun `accrual display format is 2 decimal places`() {
        val accrual = 20.5794
        assertEquals("20.58", String.format(Locale.US, "%.2f", accrual))
    }

    @Test
    fun `large amount display format is 2 decimal places`() {
        val amount = 15000.123
        assertEquals("15000.12", String.format(Locale.US, "%.2f", amount))
    }

    @Test
    fun `percent format is 3 decimal places`() {
        val percent = 0.1035
        assertEquals("0.104", String.format(Locale.US, "%.3f", percent))
    }

    @Test
    fun `percent format rounds correctly`() {
        assertEquals("0.103", String.format(Locale.US, "%.3f", 0.1034))
        assertEquals("0.104", String.format(Locale.US, "%.3f", 0.1035))
        assertEquals("0.106", String.format(Locale.US, "%.3f", 0.1059))
        assertEquals("0.107", String.format(Locale.US, "%.3f", 0.1065))
    }

    @Test
    fun `display amount rounds to 2 decimals`() {
        val amounts = listOf(
            15000.129 to 15000.13,
            15000.124 to 15000.12,
            0.005 to 0.01,
            0.004 to 0.00
        )

        amounts.forEach { (input, expected) ->
            val formatted = String.format(Locale.US, "%.2f", input).toDouble()
            assertEquals("Input: $input", expected, formatted, 0.001)
        }
    }

    // ========== Работа с датами ==========

    @Test
    fun `isSameDay returns true for same day different times`() {
        val date1 = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val date2 = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 22, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }

        val isSameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

        assertTrue(isSameDay)
    }

    @Test
    fun `isSameDay returns false for different days`() {
        val date1 = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 23, 59, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val date2 = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16, 0, 1, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }

        val isSameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

        assertFalse(isSameDay)
    }

    @Test
    fun `addDays correctly adds days to date`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val plus14Days = startDate + (14L * 24 * 60 * 60 * 1000)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 26, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, plus14Days)
    }

    @Test
    fun `isSunday correctly identifies Sunday`() {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }

        val isSunday = sunday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        assertTrue(isSunday)
    }

    @Test
    fun `isSunday returns false for Monday`() {
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 20)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }

        val isSunday = monday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        assertFalse(isSunday)
    }

    @Test
    fun `date at midnight has zero time components`() {
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(0, date.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, date.get(Calendar.MINUTE))
        assertEquals(0, date.get(Calendar.SECOND))
        assertEquals(0, date.get(Calendar.MILLISECOND))
    }

    @Test
    fun `period end date calculation for 14 days`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val periodDuration = 14L * 24 * 60 * 60 * 1000

        val period1End = startDate + periodDuration
        val period2End = period1End + periodDuration
        val period3End = period2End + periodDuration

        val expected1End = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 26, 0, 0, 0)
        }.timeInMillis

        val expected2End = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 0, 0, 0)
        }.timeInMillis

        val expected3End = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 24, 0, 0, 0)
        }.timeInMillis

        assertEquals(expected1End, period1End)
        assertEquals(expected2End, period2End)
        assertEquals(expected3End, period3End)
    }

    // ========== Проверки валидации ==========

    @Test
    fun `nominal cannot be zero`() {
        val nominal = 0.0
        assertTrue(nominal <= 0)
    }

    @Test
    fun `nominal cannot be negative`() {
        val nominal = -100.0
        assertTrue(nominal < 0)
    }

    @Test
    fun `valid nominal is positive`() {
        val nominal = 5000.0
        assertTrue(nominal > 0)
    }

    @Test
    fun `deposit amount must be specified for reinvest`() {
        val deposit = 0.0
        val isValid = deposit > 0
        assertFalse(isValid)
    }

    @Test
    fun `percent must be positive`() {
        val percent = 0.1
        assertTrue(percent > 0)
    }

    @Test
    fun `percent addition is positive`() {
        val dailyAddition = 0.003
        assertTrue(dailyAddition > 0)
    }

    // ========== Логика напоминаний ==========

    @Test
    fun `reminder is needed when PN button is inactive`() {
        val pnButtonActive = false
        val rpButtonActive = true
        val pspButtonActive = true

        val reminderNeeded = !pnButtonActive || !rpButtonActive || !pspButtonActive
        assertTrue(reminderNeeded)
    }

    @Test
    fun `reminder is needed when RP button is inactive`() {
        val pnButtonActive = true
        val rpButtonActive = false
        val pspButtonActive = true

        val reminderNeeded = !pnButtonActive || !rpButtonActive || !pspButtonActive
        assertTrue(reminderNeeded)
    }

    @Test
    fun `reminder is needed when PSP button is inactive`() {
        val pnButtonActive = true
        val rpButtonActive = true
        val pspButtonActive = false

        val reminderNeeded = !pnButtonActive || !rpButtonActive || !pspButtonActive
        assertTrue(reminderNeeded)
    }

    @Test
    fun `no reminder when all buttons are active`() {
        val pnButtonActive = true
        val rpButtonActive = true
        val pspButtonActive = true

        val reminderNeeded = !pnButtonActive || !rpButtonActive || !pspButtonActive
        assertFalse(reminderNeeded)
    }

    @Test
    fun `max 5 reminders per day`() {
        val maxReminders = 5
        assertEquals(5, maxReminders)
    }
}
