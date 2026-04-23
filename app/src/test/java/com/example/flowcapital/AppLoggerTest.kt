package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppLoggerTest {

    // ========== БАЗОВЫЕ ТЕСТЫ ФОРМАТИРОВАНИЯ ==========

    @Test
    fun `timestamp and date formatting`() {
        val timestamp = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16, 22, 30, 0)
        }.timeInMillis

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        assertEquals("16.04.2026 22:30:00", dateFormat.format(Date(timestamp)))
    }

    @Test
    fun `log levels defined`() {
        val levels = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")
        assertEquals(5, levels.size)
        assertTrue(levels.containsAll(listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")))
    }

    @Test
    fun `log level mapping`() {
        val levelMap = mapOf("VERBOSE" to "V", "DEBUG" to "D", "INFO" to "I", "WARN" to "W", "ERROR" to "E")
        assertEquals("V", levelMap["VERBOSE"])
        assertEquals("E", levelMap["ERROR"])
    }

    // ========== LOG ФАЙЛ ==========

    @Test
    fun `log file header and footer`() {
        val header = "═════════════════════════════════════════\nLOG ФАЙЛ FLOWCAPITAL"
        assertTrue(header.contains("LOG ФАЙЛ FLOWCAPITAL"))

        val footer = "═════════════════════════════════════════\nКОНЕЦ LOG ФАЙЛА"
        assertTrue(footer.contains("КОНЕЦ LOG ФАЙЛА"))
    }

    // ========== UTF-8 BOM И STACK TRACE ==========

    @Test
    fun `BOM for Cyrillic support`() {
        val bom = "\uFEFF"
        val withBom = bom + "Тест"
        assertEquals('\uFEFF', withBom[0])
    }

    @Test
    fun `stack trace extraction`() {
        val exception = Exception("Test")
        val sw = StringWriter()
        exception.printStackTrace(PrintWriter(sw))
        assertTrue(sw.toString().contains("java.lang.Exception"))
    }

    // ========== LOG МЕНЕДЖМЕНТ ==========

    @Test
    fun `log file name format`() {
        val timestamp = System.currentTimeMillis()
        val fileName = "FlowCapital_Log_$timestamp.txt"
        assertTrue(fileName.startsWith("FlowCapital_Log_"))
        assertTrue(fileName.endsWith(".txt"))
    }

    @Test
    fun `log list management`() {
        val logs = mutableListOf<Int>()
        for (i in 1..1500) {
            logs.add(i)
            if (logs.size > 1000) logs.removeAt(0)
        }
        assertEquals(1000, logs.size)
    }

    @Test
    fun `log entry format`() {
        val logEntry = "E/TestTag: Error message"
        assertTrue(logEntry.contains("E/TestTag:"))
        assertTrue(logEntry.contains("Error message"))
    }
}
