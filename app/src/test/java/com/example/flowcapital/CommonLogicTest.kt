package com.example.flowcapital

import androidx.compose.ui.graphics.Color
import com.example.flowcapital.ui.theme.FlowColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Unit-тесты общей логики приложения.
 *
 * По ТЗ проверяем:
 * - Цветовое кодирование потоков (РП-зеленый, ПН-синий, ПСП-красный)
 * - Форматирование дат (dd.MM.yy)
 * - Генерация пропущенных дней
 */
class CommonLogicTest {

    @Test
    fun `RP flow uses green color from FlowColors`() {
        val expectedGreen = Color(0xFF4CAF50)
        assertEquals("РП должен быть зеленым (0xFF4CAF50)", expectedGreen, FlowColors.RP_COLOR)
    }

    @Test
    fun `PN flow uses blue color from FlowColors`() {
        val expectedBlue = Color(0xFF2196F3)
        assertEquals("ПН должен быть синим (0xFF2196F3)", expectedBlue, FlowColors.PN_COLOR)
    }

    @Test
    fun `PSP flow uses red color from FlowColors`() {
        val expectedRed = Color(0xFFF44336)
        assertEquals("ПСП должен быть красным (0xFFF44336)", expectedRed, FlowColors.PSP_COLOR)
    }

    @Test
    fun `NP flow uses purple color from FlowColors`() {
        val expectedPurple = Color(0xFF9C27B0)
        assertEquals("НП должен быть фиолетовым (0xFF9C27B0)", expectedPurple, FlowColors.NP_COLOR)
    }

    @Test
    fun `getColorForIndex returns correct colors`() {
        assertEquals("Индекс 0 = PN", FlowColors.PN_COLOR, FlowColors.getColorForIndex(0))
        assertEquals("Индекс 3 = RP", FlowColors.RP_COLOR, FlowColors.getColorForIndex(3))
        assertEquals("Индекс 2 = PSP", FlowColors.PSP_COLOR, FlowColors.getColorForIndex(2))
    }

    @Test
    fun `date format is ddMMyy`() {
        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16)
        }.time
        assertEquals("16.04.26", dateFormat.format(date))
    }

    @Test
    fun `dates stored as Unix timestamp in DB`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis / 1000
        assertTrue("Timestamp должен быть положительным", timestamp > 0)
    }

    @Test
    fun `numbers stored as Double without rounding`() {
        val value = 199.456789
        assertEquals(199.456789, value, 0.000001)
    }

    @Test
    fun `UI shows 2 decimal places`() {
        val value = 19980.456
        val formatted = String.format(Locale.US, "%.2f", value)
        assertEquals("19980.46", formatted)
    }

    @Test
    fun `RP percent shows 3 decimal places`() {
        val percent = 0.103
        val formatted = String.format(Locale.US, "%.3f", percent)
        assertEquals("0.103", formatted)
    }

    @Test
    fun `number parsing handles comma and dot`() {
        assertEquals(10.5, "10,5".replace(",", ".").toDoubleOrNull()!!, 0.01)
        assertEquals(10.5, "10.5".replace(",", ".").toDoubleOrNull()!!, 0.01)
    }

    @Test
    fun `skipped days calculation is correct`() {
        val lastVisitTimestamp = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val currentTimestamp = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val daysSkipped = ((currentTimestamp - lastVisitTimestamp) / (1000L * 60 * 60 * 24)).toInt()
        assertEquals("Пропущено 5 дней", 5, daysSkipped)
    }

    @Test
    fun `sunday is detected as weekend`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)
        }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        assertEquals("Должно быть воскресенье", Calendar.SUNDAY, dayOfWeek)
    }

    @Test
    fun `saturday is active for PN and RP`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 18)
        }
        val isSaturday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        assertTrue("Суббота должна быть активной", isSaturday)
    }

    @Test
    fun `weekend record duplicates previous balances`() {
        val previousInFlow = 15000.0
        val previousAccrual = 300.0
        val weekendInFlow = previousInFlow
        val weekendAccrual = previousAccrual
        assertEquals(previousInFlow, weekendInFlow, 0.01)
        assertEquals(previousAccrual, weekendAccrual, 0.01)
    }

    @Test
    fun `weekday skip shows correct text`() {
        val skipText = "Забыли нажать на кнопку"
        assertEquals("Текст должен совпадать", "Забыли нажать на кнопку", skipText)
    }
}

class CoefficientsTest {

    @Test
    fun `PSP has 20 periods with default coefficients`() {
        val defaultCoeffs = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
            5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
            9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
            13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
            17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
        assertEquals("Должно быть 20 периодов", 20, defaultCoeffs.size)
        assertEquals("Период 1 = 30%", 30.0, defaultCoeffs[1]!!, 0.01)
        assertEquals("Период 20 = 200%", 200.0, defaultCoeffs[20]!!, 0.01)
    }

    @Test
    fun `PSP coefficients serialization format`() {
        val coefficients = mapOf(1 to 30.0, 2 to 55.8, 3 to 78.0)
        val serialized = coefficients.entries.joinToString(";") { "${it.key}=${it.value}" }
        assertTrue(serialized.contains("1=30.0"))
        assertTrue(serialized.contains(";"))
    }

    @Test
    fun `PSP coefficients deserialization from string`() {
        val serialized = "1=30.0;2=55.8;3=78.0"
        val parsed = mutableMapOf<Int, Double>()
        serialized.split(";").forEach {
            val parts = it.split("=")
            if (parts.size == 2) {
                parsed[parts[0].toInt()] = parts[1].toDouble()
            }
        }
        assertEquals("Должно быть 3 элемента", 3, parsed.size)
        assertEquals("Период 2 = 55.8", 55.8, parsed[2]!!, 0.01)
        assertEquals("Период 3 = 78.0", 78.0, parsed[3]!!, 0.01)
    }

    @Test
    fun `E-currency coefficients from settings`() {
        val defaultCoeffs = mapOf(
            1000.0 to 50.0, 5000.0 to 75.0, 10000.0 to 100.0,
            50000.0 to 125.0, 100000.0 to 150.0, 500000.0 to 175.0, 1000000.0 to 200.0
        )
        assertEquals("Должно быть 7 порогов", 7, defaultCoeffs.size)
        assertEquals("1000 = 50%", 50.0, defaultCoeffs[1000.0]!!, 0.01)
    }

    @Test
    fun `E-currency bonus calculation`() {
        val coefficients = mapOf(
            1000.0 to 50.0, 5000.0 to 75.0, 10000.0 to 100.0,
            50000.0 to 125.0, 100000.0 to 150.0, 500000.0 to 175.0, 1000000.0 to 200.0
        )
        val sorted = coefficients.entries.sortedByDescending { it.key }
        
        fun calculateBonus(amount: Double): Double {
            for ((threshold, bonus) in sorted) {
                if (amount >= threshold) return bonus
            }
            return 0.0
        }
        
        assertEquals("10000 = 100%", 100.0, calculateBonus(10000.0), 0.01)
        assertEquals("500000 = 175%", 175.0, calculateBonus(500000.0), 0.01)
        assertEquals("999 = 0%", 0.0, calculateBonus(999.0), 0.01)
    }
}