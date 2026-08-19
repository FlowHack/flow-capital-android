package com.flowhack.flowcapital.data.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юнит-тесты валидатора прокси [ProxyValidator].
 *
 * Проверяют реальную логику валидации IP-адресов, портов и прокси-конфигураций.
 */
class ProxyValidatorTest {

    @Test
    fun validateIpAddress_validIp_returnsTrue() {
        assertTrue(ProxyValidator.validateIpAddress("192.168.1.1"))
        assertTrue(ProxyValidator.validateIpAddress("0.0.0.0"))
        assertTrue(ProxyValidator.validateIpAddress("255.255.255.255"))
    }

    @Test
    fun validateIpAddress_invalidIp_returnsFalse() {
        assertFalse(ProxyValidator.validateIpAddress(""))
        assertFalse(ProxyValidator.validateIpAddress("256.1.1.1"))
        assertFalse(ProxyValidator.validateIpAddress("1.2.3"))
        assertFalse(ProxyValidator.validateIpAddress("1.2.3.4.5"))
        assertFalse(ProxyValidator.validateIpAddress("abc.def.ghi.jkl"))
        assertFalse(ProxyValidator.validateIpAddress("1.2.3.-1"))
    }

    @Test
    fun validatePort_validPort_returnsTrue() {
        assertTrue(ProxyValidator.validatePort("1"))
        assertTrue(ProxyValidator.validatePort("1080"))
        assertTrue(ProxyValidator.validatePort("65535"))
    }

    @Test
    fun validatePort_invalidPort_returnsFalse() {
        assertFalse(ProxyValidator.validatePort(""))
        assertFalse(ProxyValidator.validatePort("0"))
        assertFalse(ProxyValidator.validatePort("65536"))
        assertFalse(ProxyValidator.validatePort("abc"))
        assertFalse(ProxyValidator.validatePort("-1"))
    }

    @Test
    fun validateHttpProxy_withValidData_returnsValid() {
        val result = ProxyValidator.validateHttpProxy("1.2.3.4", "8080", null, null)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validateHttpProxy_withInvalidIp_returnsError() {
        val result = ProxyValidator.validateHttpProxy("999.1.1.1", "8080", null, null)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("IP") })
    }

    @Test
    fun validateHttpProxy_withInvalidPort_returnsError() {
        val result = ProxyValidator.validateHttpProxy("1.2.3.4", "70000", null, null)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Порт") })
    }
}

/**
 * Юнит-тесты расчёта бонусов eRub [ProxyERubBonus].
 *
 * Проверяют реальную шкалу бонусов в зависимости от суммы взноса.
 */
class ProxyERubBonusTest {

    @Test
    fun calculateBonus_amountAboveMillion_returnsDouble() {
        assertEquals(2_000_000.0, ProxyERubBonus.calculateBonus(1_000_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountAt500k_returns175Percent() {
        assertEquals(875_000.0, ProxyERubBonus.calculateBonus(500_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountAt100k_returns150Percent() {
        assertEquals(150_000.0, ProxyERubBonus.calculateBonus(100_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountAt10k_returns100Percent() {
        assertEquals(10_000.0, ProxyERubBonus.calculateBonus(10_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountAt5k_returns75Percent() {
        assertEquals(3_750.0, ProxyERubBonus.calculateBonus(5_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountAt1k_returns50Percent() {
        assertEquals(500.0, ProxyERubBonus.calculateBonus(1_000.0), 0.0001)
    }

    @Test
    fun calculateBonus_amountBelow1k_returnsZero() {
        assertEquals(0.0, ProxyERubBonus.calculateBonus(999.0), 0.0001)
    }

    @Test
    fun getTotalWithBonus_returnsAmountPlusBonus() {
        // 1000 + 500 (50%) = 1500
        assertEquals(1_500.0, ProxyERubBonus.getTotalWithBonus(1_000.0), 0.0001)
        // 5000 + 3750 (75%) = 8750
        assertEquals(8_750.0, ProxyERubBonus.getTotalWithBonus(5_000.0), 0.0001)
        // 10000 + 10000 (100%) = 20000
        assertEquals(20_000.0, ProxyERubBonus.getTotalWithBonus(10_000.0), 0.0001)
        // Ниже порога 1000 - бонус 0, сумма без изменений.
        assertEquals(999.0, ProxyERubBonus.getTotalWithBonus(999.0), 0.0001)
    }
}
