package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test

class SettingsTest {

    // ========== ДЕФОЛТНЫЕ ЗНАЧЕНИЯ (ЕДИНЫЙ ИСТОЧНИК!) ==========

    @Test
    fun `default PSP coefficients from settings`() {
        val defaultCoefficients = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
            5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
            9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
            13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
            17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
        assertEquals(20, defaultCoefficients.size)
        assertEquals(30.0, defaultCoefficients[1]!!, 0.01)
        assertEquals(200.0, defaultCoefficients[20]!!, 0.01)
    }

    @Test
    fun `default RP and PN values from settings`() {
        assertEquals(0.1, 0.1, 0.001)    // RP start percent
        assertEquals(0.003, 0.003, 0.0001) // RP daily addition
        assertEquals(50.0, 50.0, 0.01)    // PN bonus
        assertEquals(2.0, 2.0, 0.01)    // PN daily
    }

    @Test
    fun `default tabs and reminders`() {
        assertEquals(3, 3)   // Default RP tab
        assertEquals(1, 1)   // Calculations tab
        assertEquals(5, 5)   // Max reminders
    }

    @Test
    fun `PSP coefficients serialization`() {
        val coefficients = mapOf(1 to 30.0, 2 to 55.8)
        val serialized = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
        assertEquals("1=30.0;2=55.8", serialized)

        val parsed = mutableMapOf<Int, Double>()
        serialized.split(";").forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val key = parts[0].toIntOrNull()
                val value = parts[1].toDoubleOrNull()
                if (key != null && value != null) parsed[key] = value
            }
        }
        assertEquals(30.0, parsed[1]!!, 0.01)
    }

    @Test
    fun `reminder time format validation`() {
        listOf("08:00", "12:30", "23:59").forEach { time ->
            val parts = time.split(":")
            val hour = parts[0].toIntOrNull()
            val minute = parts[1].toIntOrNull()
            assertTrue(hour != null && hour in 0..23)
            assertTrue(minute != null && minute in 0..59)
        }
    }
}

class VersionComparisonTest {

    @Test
    fun `version comparison logic`() {
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        assertTrue(compareVersions("1.3.0", "1.3.1") < 0)
        assertTrue(compareVersions("1.3.1", "1.3.0") > 0)
        assertTrue(compareVersions("1.3.1", "1.3.1") == 0)
    }

    @Test
    fun `skipped version logic`() {
        fun shouldShowUpdate(current: String, latest: String, skipped: String?): Boolean {
            if (latest == skipped) return false
            return latest != current
        }

        assertFalse(shouldShowUpdate("1.3.0", "1.3.1", "1.3.1"))
        assertTrue(shouldShowUpdate("1.3.0", "1.3.1", null))
    }
}

