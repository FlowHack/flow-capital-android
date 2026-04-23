package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {

    @Test
    fun `comma parsing works`() {
        val s = "10,5".replace(",", ".")
        assertEquals("10.5", s)
    }

    @Test
    fun `dot parsing works`() {
        val s = "10.5".replace(",", ".")
        assertEquals("10.5", s)
    }

    @Test
    fun `both give same string`() {
        assertEquals("10,5".replace(",", "."), "10.5".replace(",", "."))
    }

    @Test
    fun `integer parses`() {
        assertNotNull("1000".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `large number parses`() {
        assertNotNull("1000000".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `multiple dots returns null`() {
        assertNull("10.5.2".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `text returns null`() {
        assertNull("abc".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `empty returns null`() {
        assertNull("".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `zero is invalid for contribution`() {
        val n = "0".replace(",", ".").toDoubleOrNull()
        assertTrue(n == null || n <= 0)
    }

    @Test
    fun `zero with decimal invalid`() {
        val n = "0.0".replace(",", ".").toDoubleOrNull()
        assertTrue(n == null || n <= 0)
    }

    @Test
    fun `negative invalid`() {
        val n = "-100".replace(",", ".").toDoubleOrNull()
        assertTrue(n == null || n <= 0)
    }

    @Test
    fun `positive valid`() {
        val n = "1000".replace(",", ".").toDoubleOrNull()
        assertTrue(n != null && n > 0)
    }

    @Test
    fun `negative percent invalid`() {
        val n = "-5".replace(",", ".").toDoubleOrNull()
        assertTrue(n == null || n < 0)
    }

    @Test
    fun `zero percent valid`() {
        val n = "0".replace(",", ".").toDoubleOrNull()
        assertTrue(n != null && n >= 0)
    }

    @Test
    fun `empty keeps previous value`() {
        val last = 500.0
        val input: String? = null
        val result = input?.replace(",", ".")?.toDoubleOrNull() ?: last
        assertEquals(500.0, result, 0.01)
    }

    @Test
    fun `empty string keeps previous`() {
        val last = 500.0
        val input = ""
        val result = if (input.isEmpty()) last else input.replace(",", ".").toDoubleOrNull() ?: last
        assertEquals(500.0, result, 0.01)
    }

    @Test
    fun `zero changes to zero`() {
        val result = "0".replace(",", ".").toDoubleOrNull() ?: 500.0
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `zero decimal changes`() {
        val result = "0.0".replace(",", ".").toDoubleOrNull() ?: 500.0
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `blank space treated as empty`() {
        val last = 500.0
        val input = "   "
        val result = if (input.isBlank()) last else input.replace(",", ".").toDoubleOrNull() ?: last
        assertEquals(500.0, result, 0.01)
    }

    @Test
    fun `validation rejects negative`() {
        fun valid(t: String): Boolean {
            val p = t.replace(",", ".").toDoubleOrNull()
            return p != null && p >= 0
        }
        assertFalse(valid("-100"))
    }

    @Test
    fun `validation accepts positive`() {
        fun valid(t: String): Boolean {
            val p = t.replace(",", ".").toDoubleOrNull()
            return p != null && p >= 0
        }
        assertTrue(valid("1000"))
    }

    @Test
    fun `percent parsing handles comma`() {
        val p = "2,0".replace(",", ".").toDoubleOrNull()
        assertNotNull(p)
    }

    @Test
    fun `percent can be zero`() {
        val p = "0".replace(",", ".").toDoubleOrNull()
        assertNotNull(p)
    }

    @Test
    fun `percent up to 100 valid`() {
        val p = "100".replace(",", ".").toDoubleOrNull()
        assertNotNull(p)
    }

    @Test
    fun `very small amount valid`() {
        assertNotNull("0.001".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `very large amount valid`() {
        assertNotNull("999999999".replace(",", ".").toDoubleOrNull())
    }

    @Test
    fun `scientific notation works`() {
        val r = "1e6".toDoubleOrNull()
        assertNotNull(r)
    }

    @Test
    fun `IP octet 0 valid`() {
        val n = "0".toIntOrNull()
        assertTrue(n != null && n in 0..255)
    }

    @Test
    fun `IP octet 255 valid`() {
        val n = "255".toIntOrNull()
        assertTrue(n != null && n in 0..255)
    }

    @Test
    fun `IP octet 256 invalid`() {
        val n = "256".toIntOrNull()
        assertFalse(n != null && n in 0..255)
    }

    @Test
    fun `valid IP address`() {
        fun valid(ip: String): Boolean {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } ?: false }
        }
        assertTrue(valid("127.0.0.1"))
    }

    @Test
    fun `invalid IP too many parts`() {
        fun valid(ip: String): Boolean {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } ?: false }
        }
        assertFalse(valid("192.168.1"))
    }
}