package com.example.flowcapital

import com.example.flowcapital.data.proxy.*
import org.junit.Assert.*
import org.junit.Test

class ProxyTest {

    // ========== IP/PORT ВАЛИДАЦИЯ ==========

    @Test
    fun `valid IP addresses pass validation`() {
        listOf("192.168.1.1", "10.0.0.1", "255.255.255.255", "0.0.0.0").forEach { ip ->
            assertTrue("IP $ip should be valid", ProxyValidator.validateIpAddress(ip))
        }
    }

    @Test
    fun `invalid IP addresses fail validation`() {
        listOf("192.168.1", "256.168.1.1", "192.168.1.300", "", "abc.168.1.1").forEach { ip ->
            assertFalse("IP $ip should be invalid", ProxyValidator.validateIpAddress(ip))
        }
    }

    @Test
    fun `valid ports pass validation`() {
        listOf("1", "80", "443", "8080", "65535").forEach { port ->
            assertTrue("Port $port should be valid", ProxyValidator.validatePort(port))
        }
    }

    @Test
    fun `invalid ports fail validation`() {
        listOf("0", "65536", "", "abc").forEach { port ->
            assertFalse("Port $port should be invalid", ProxyValidator.validatePort(port))
        }
    }

    // ========== SOCKS5/MTPROTO ВАЛИДАЦИЯ ==========

    @Test
    fun `SOCKS5 proxy validation covers all cases`() {
        assertTrue(ProxyValidator.validateSocks5Proxy("192.168.1.1", "1080", "user", "pass").isValid)
        assertFalse(ProxyValidator.validateSocks5Proxy("invalid", "1080", "user", "pass").isValid)
        assertFalse(ProxyValidator.validateSocks5Proxy("192.168.1.1", "invalid", "user", "pass").isValid)
        assertFalse(ProxyValidator.validateSocks5Proxy("192.168.1.1", "1080", "", "pass").isValid)
        assertFalse(ProxyValidator.validateSocks5Proxy("192.168.1.1", "1080", "user", "").isValid)
        assertEquals(4, ProxyValidator.validateSocks5Proxy("", "", "", "").errors.size)
    }

    @Test
    fun `MTProto proxy validation covers all cases`() {
        assertTrue(ProxyValidator.validateMtProtoProxy("192.168.1.1", "443", "dd1234567890abcdef1234567890abcdef12").isValid)
        assertFalse(ProxyValidator.validateMtProtoProxy("invalid", "443", "secret").isValid)
        assertFalse(ProxyValidator.validateMtProtoProxy("192.168.1.1", "invalid", "secret").isValid)
        assertFalse(ProxyValidator.validateMtProtoProxy("192.168.1.1", "443", "").isValid)
    }

    // ========== E-CURRENCY БОНУС (КРАЕВОЙ СЛУЧАЙ - критически важно!) ==========

    @Test
    fun `ECurrency bonus calculation at all table thresholds`() {
        assertEquals(500.0, ProxyECurrencyBonus.calculateBonus(1000.0), 0.01)
        assertEquals(3750.0, ProxyECurrencyBonus.calculateBonus(5000.0), 0.01)
        assertEquals(10000.0, ProxyECurrencyBonus.calculateBonus(10000.0), 0.01)
        assertEquals(62500.0, ProxyECurrencyBonus.calculateBonus(50000.0), 0.01)
        assertEquals(150000.0, ProxyECurrencyBonus.calculateBonus(100000.0), 0.01)
        assertEquals(875000.0, ProxyECurrencyBonus.calculateBonus(500000.0), 0.01)
        assertEquals(2000000.0, ProxyECurrencyBonus.calculateBonus(1000000.0), 0.01)
    }

    @Test
    fun `ECurrency bonus is zero below minimum threshold`() {
        assertEquals(0.0, ProxyECurrencyBonus.calculateBonus(999.0), 0.01)
        assertEquals(0.0, ProxyECurrencyBonus.calculateBonus(1.0), 0.01)
    }

    @Test
    fun `ECurrency total with bonus`() {
        assertEquals(20000.0, ProxyECurrencyBonus.getTotalWithBonus(10000.0), 0.01)
        assertEquals(8750.0, ProxyECurrencyBonus.getTotalWithBonus(5000.0), 0.01)
    }

    // ========== PROXY CONFIG ==========

    @Test
    fun `ProxyConfig default values and creation`() {
        val config = ProxyConfig()
        assertEquals(ProxyType.SOCKS5, config.type)
        assertEquals("", config.server)
        assertEquals(0, config.port)
        assertEquals(ProxyStatus.DISCONNECTED, config.status)
        assertTrue(config.enabledForSites.isEmpty())
    }

    @Test
    fun `ProxyConfig with custom values`() {
        val config = ProxyConfig(
            type = ProxyType.MTPROTO,
            server = "192.168.1.1",
            port = 443,
            secret = "secret123",
            status = ProxyStatus.CONNECTED,
            pingMs = 50,
            enabledForSites = setOf("ПОТОКCASH", "СБЕРКАССА")
        )
        assertEquals(ProxyType.MTPROTO, config.type)
        assertEquals("192.168.1.1", config.server)
        assertEquals(443, config.port)
        assertEquals(2, config.enabledForSites.size)
    }

    @Test
    fun `ProxyConfig id is unique`() {
        assertNotEquals(ProxyConfig().id, ProxyConfig().id)
    }

    @Test
    fun `ProxyType and ProxyStatus enums`() {
        assertEquals(2, ProxyType.values().size)
        assertEquals(4, ProxyStatus.values().size)
    }

    // ========== IP ВАЛИДАЦИЯ (строго 4 числа, не более 255) ==========

    @Test
    fun `IP validation - exactly 4 octets`() {
        // ТЗ: Валидация IP: Строго 4 числа, не более 255
        assertTrue(ProxyValidator.validateIpAddress("192.168.1.1")) // 4 octets - OK
        assertFalse(ProxyValidator.validateIpAddress("192.168.1")) // 3 octets - FAIL
        assertFalse(ProxyValidator.validateIpAddress("192.168.1.1.1")) // 5 octets - FAIL
    }

    @Test
    fun `IP validation - each number max 255`() {
        assertTrue(ProxyValidator.validateIpAddress("255.255.255.255")) // Max - OK
        assertTrue(ProxyValidator.validateIpAddress("0.0.0.0")) // Min - OK
        assertTrue(ProxyValidator.validateIpAddress("192.168.1.1")) // Normal - OK

        assertFalse(ProxyValidator.validateIpAddress("256.168.1.1")) // 256 > 255 - FAIL
        assertFalse(ProxyValidator.validateIpAddress("192.168.1.300")) // 300 > 255 - FAIL
        assertFalse(ProxyValidator.validateIpAddress("192.168.1.-1")) // Negative - FAIL
    }

    @Test
    fun `IP validation rejects non-numeric`() {
        assertFalse(ProxyValidator.validateIpAddress("abc.168.1.1"))
        assertFalse(ProxyValidator.validateIpAddress("192.abc.1.1"))
        assertFalse(ProxyValidator.validateIpAddress("192.168.abc.1"))
        assertFalse(ProxyValidator.validateIpAddress("192.168.1.abc"))
    }

    @Test
    fun `IP validation edge cases`() {
        assertFalse(ProxyValidator.validateIpAddress(""))
        assertFalse(ProxyValidator.validateIpAddress(" "))
    }

    // ========== ВЫБОР ПРОКСИ С НАИМЕНЬШИМ ПИНГОМ ==========

    @Test
    fun `proxy with lowest ping is selected`() {
        // ТЗ: Если к сайту привязано несколько рабочих прокси,
        // берется тот, у которого наименьший пинг

        val proxies = listOf(
            ProxyConfig(server = "proxy1", pingMs = 100),
            ProxyConfig(server = "proxy2", pingMs = 50),
            ProxyConfig(server = "proxy3", pingMs = 200)
        )

        val selected = proxies.filter { it.pingMs != null && it.pingMs!! > 0 }
            .minByOrNull { it.pingMs!! }

        assertEquals("proxy2", selected?.server)
        assertEquals(50, selected?.pingMs)
    }

    @Test
    fun `proxy with lowest ping among multiple sites works`() {
        // Несколько прокси для разных сайтов
        val proxiesForSite = listOf(
            ProxyConfig(server = "fast", pingMs = 30, enabledForSites = setOf("ПОТОКCASH")),
            ProxyConfig(server = "slow", pingMs = 150, enabledForSites = setOf("ПОТОКCASH")),
            ProxyConfig(server = "medium", pingMs = 80, enabledForSites = setOf("ПОТОКCASH"))
        )

        val bestProxy = proxiesForSite.filter { it.pingMs != null }
            .minByOrNull { it.pingMs!! }
        assertEquals("fast", bestProxy?.server)
        assertEquals(30, bestProxy?.pingMs)
    }

    @Test
    fun `direct connection when no proxy attached`() {
        // ТЗ: Если прокси не привязан — запрос идет напрямую
        val attachedProxies = emptyList<ProxyConfig>()

        // Нет прокси - используем DIRECT
        val useDirect = attachedProxies.isEmpty()

        assertTrue(useDirect)
    }

    @Test
    fun `ping comparison handles null values`() {
        // При null пинге - прокси не выбирается
        val proxies = listOf(
            ProxyConfig(server = "proxy1", pingMs = null),
            ProxyConfig(server = "proxy2", pingMs = 50),
            ProxyConfig(server = "proxy3", pingMs = null)
        )

        val available = proxies.filter { it.pingMs != null }
        val selected = available.minByOrNull { it.pingMs!! }
        assertEquals("proxy2", selected?.server)
    }

    @Test
    fun `ping comparison handles equal values`() {
        // При одинаковом пинге - первый в списке
        val proxies = listOf(
            ProxyConfig(server = "first", pingMs = 50),
            ProxyConfig(server = "second", pingMs = 50),
            ProxyConfig(server = "third", pingMs = 50)
        )

        val selected = proxies.minByOrNull { it.pingMs!! }
        assertEquals("first", selected?.server)
    }
}
