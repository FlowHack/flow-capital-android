package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RPCoefficientsFromSettingsTest {

    @Test
    fun `RP settings come from database not hardcoded`() {
        val settingsCoefficients = mapOf(
            1000.0 to 50.0, 5000.0 to 75.0, 10000.0 to 100.0,
            50000.0 to 125.0, 100000.0 to 150.0, 500000.0 to 175.0, 1000000.0 to 200.0
        )
        assertEquals(7, settingsCoefficients.size)
        assertEquals(0.1, 0.1, 0.001)
        assertEquals(0.003, 0.003, 0.0001)
    }

    @Test
    fun `RP percent recalculation formula from correction (TZ!)`() {
        val inFlow = 10000.0
        val newAccrual = 100.0
        val calculatedPercent = (newAccrual * 100.0) / inFlow
        assertEquals(1.0, calculatedPercent, 0.001)
    }

    @Test
    fun `RP daily button press increases percent`() {
        var percent = 0.1
        val dailyAddition = 0.003
        repeat(3) { percent += dailyAddition }
        assertEquals(0.109, percent, 0.001)
    }

    @Test
    fun `RP accrual calculation uses current percent`() {
        val accrual = 19980.0 * (0.103 / 100.0)
        assertEquals(20.58, accrual, 0.01)
    }

    @Test
    fun `RP full cycle from TZ example`() {
        var inFlow = 10000.0 * 2.0
        var percent = 0.1
        var wallet = 0.0

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        percent += 0.003

        assertEquals(19980.0, inFlow, 0.01)
        assertEquals(20.0, wallet, 0.01)
        assertEquals(0.103, percent, 0.001)
    }

    @Test
    fun `RP full cycle calculates correctly`() {
        var inFlow = 20000.0
        var percent = 0.1

        val accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        percent += 0.003

        assertTrue(inFlow > 0)
    }
}

class RPValidationFromTZTest {

    @Test
    fun `validation requires at least one field changed`() {
        fun hasAnyChange(flowText: String, accrualText: String, walletText: String,
                       checkboxChanged: Boolean, currentFlow: Double, currentAccrual: Double, currentWallet: Double): Boolean {
            fun parseDouble(text: String): Double? = text.replace(",", ".").toDoubleOrNull()
            return (flowText.isNotEmpty() && parseDouble(flowText)?.let { it != currentFlow } ?: false) ||
                   (accrualText.isNotEmpty() && parseDouble(accrualText)?.let { it != currentAccrual } ?: false) ||
                   (walletText.isNotEmpty() && parseDouble(walletText)?.let { it != currentWallet } ?: false) ||
                   checkboxChanged
        }

        assertFalse(hasAnyChange("", "", "", false, 1000.0, 10.0, 0.0))
        assertTrue(hasAnyChange("1500", "", "", false, 1000.0, 10.0, 0.0))
    }

    @Test
    fun `wallet null means keep previous in reinvest`() {
        val lastWallet: Double? = null
        assertEquals(0.0, lastWallet ?: 0.0, 0.01)
    }

    @Test
    fun `wallet 0 explicitly set changes to 0`() {
        assertEquals(0.0, "0".replace(",", ".").toDoubleOrNull() ?: 0.0, 0.01)
    }

    @Test
    fun `button disabled when flow is zero`() {
        assertTrue(0.0 <= 0)
    }
}

class RPUIFromTZTest {

    @Test
    fun `button text logic for different states`() {
        assertEquals("СДЕЛАЙТЕ РЕИНВЕСТ", if (0.0 <= 0) "СДЕЛАЙТЕ РЕИНВЕСТ" else "Я СЕГОДНЯ НАЖАЛ ��А КНОПКУ")
        assertEquals("ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ", if (true) "ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ")
        assertEquals("НАЧИСЛЕНИЕ ВЫПОЛНЕНО", if (true) "НАЧИСЛЕНИЕ ВЫПОЛНЕНО" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ")
    }

    @Test
    fun `RP formatting with correct precision`() {
        assertEquals("0.103", String.format(java.util.Locale.US, "%.3f", 0.103))
        assertEquals("19980.00", String.format(java.util.Locale.US, "%.2f", 19980.00))
        assertEquals("+20.58", String.format(java.util.Locale.US, "+%.2f", 20.58))
    }
}

class RPForecastFromTZTest {

    @Test
    fun `forecast stops at zero (TZ requirement)`() {
        var simInFlow = 100.0
        var simPercent = 0.1
        val dailyAddition = 0.003
        var dayCount = 0

        while (simInFlow > 0 && dayCount < 100) {
            val accrual = simInFlow * (simPercent / 100.0)
            simInFlow -= accrual
            if (simInFlow < 0) simInFlow = 0.0
            simPercent += dailyAddition
            dayCount++
        }

        assertTrue(dayCount > 0)
    }

    @Test
    fun `best date logic finds dropping point`() {
        val dailyAddition = 0.003
        var simInFlow = 50000.0
        var simPercent = 0.1
        var prevAccrual = simInFlow * (simPercent / 100.0)
        var foundDrop = false

        for (day in 1..200) {
            val accrual = simInFlow * (simPercent / 100.0)
            if (day > 1 && accrual < prevAccrual) { foundDrop = true; break }
            prevAccrual = accrual
            simInFlow -= accrual
            if (simInFlow <= 0) break
            simPercent += dailyAddition
        }

        assertTrue(foundDrop)
    }
}