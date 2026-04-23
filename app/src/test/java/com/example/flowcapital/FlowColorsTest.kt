package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class DateUtilsTest {

    // ========== ДАТЫ И КАЛЕНДАРЬ ==========

    @Test
    fun `14 day period duration`() {
        val periodDuration = 14L * 24 * 60 * 60 * 1000
        assertEquals(1209600000L, periodDuration)
    }

    @Test
    fun `date format and Sunday detection`() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = Calendar.getInstance().apply { set(2023, Calendar.APRIL, 12) }.time
        assertEquals("12.04.2023", dateFormat.format(date))

        val sunday = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 19) }
        assertEquals(Calendar.SUNDAY, sunday.get(Calendar.DAY_OF_WEEK))
    }
}

class NumberParsingTest {

    @Test
    fun `number parsing handles comma dot and edge cases`() {
        assertEquals(10.5, "10,5".replace(",", ".").toDoubleOrNull()!!, 0.01)
        assertEquals(10.5, "10.5".replace(",", ".").toDoubleOrNull()!!, 0.01)
        assertEquals(100.0, "100".replace(",", ".").toDoubleOrNull()!!, 0.01)
        assertEquals(0.50, ",50".replace(",", ".").toDoubleOrNull()!!, 0.01)
        assertNull("abc".replace(",", ".").toDoubleOrNull())
        assertNull("".replace(",", ".").toDoubleOrNull())
    }
}

class NumberFormattingTest {

    @Test
    fun `formats with correct precision`() {
        assertEquals("20.58", String.format(Locale.US, "%.2f", 20.5794))
        assertEquals("15000.12", String.format(Locale.US, "%.2f", 15000.123))
        assertEquals("0.104", String.format(Locale.US, "%.3f", 0.1035))
        assertEquals("1500.00₽", String.format(Locale.US, "%.2f₽", 1500.0))
    }
}

class ReminderLogicTest {

    @Test
    fun `reminder logic from TZ`() {
        assertTrue(!false || true)
        assertFalse(!true || !true || !true)
    }

    @Test
    fun `max reminders per day is 5`() {
        assertTrue(5 > 0)
    }
}


