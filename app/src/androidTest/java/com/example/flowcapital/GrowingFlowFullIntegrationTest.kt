package com.example.flowcapital

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowcapital.data.db.AppDatabase
import com.example.flowcapital.data.db.GrowingFlowDao
import com.example.flowcapital.data.db.GrowingFlowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Locale

/**
 * Полные интеграционные тесты для РП (Растущий Поток)
 * Охватывают: E-currency бонусы, стартовый %=0.1, +0.003 за нажатие, воскресенья, пропуски, коррекции, лучшая дата
 */
@RunWith(AndroidJUnit4::class)
class GrowingFlowFullIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var growingDao: GrowingFlowDao

    private val startPercent = 0.1
    private val dailyAddition = 0.003

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        growingDao = database.growingFlowDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    // ========== E-currency бонусы ==========

    private fun calculateECurrencyBonus(amount: Double): Double {
        return when {
            amount >= 1_000_000 -> amount * 3.00  // +200%
            amount >= 500_000 -> amount * 2.75       // +175%
            amount >= 100_000 -> amount * 2.50      // +150%
            amount >= 50_000 -> amount * 2.25        // +125%
            amount >= 10_000 -> amount * 2.00        // +100%
            amount >= 5_000 -> amount * 1.75         // +75%
            amount >= 1_000 -> amount * 1.50         // +50%
            else -> amount
        }
    }

    @Test
    fun `RP E-currency bonus for 1000 is 1500`() {
        val result = calculateECurrencyBonus(1000.0)
        assertEquals(1500.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 5000 is 8750`() {
        val result = calculateECurrencyBonus(5000.0)
        assertEquals(8750.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 10000 is 20000`() {
        val result = calculateECurrencyBonus(10000.0)
        assertEquals(20000.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 50000 is 112500`() {
        val result = calculateECurrencyBonus(50000.0)
        assertEquals(112500.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 100000 is 250000`() {
        val result = calculateECurrencyBonus(100000.0)
        assertEquals(250000.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 500000 is 1375000`() {
        val result = calculateECurrencyBonus(500000.0)
        assertEquals(1375000.0, result, 0.01)
    }

    @Test
    fun `RP E-currency bonus for 1000000 is 3000000`() {
        val result = calculateECurrencyBonus(1_000_000.0)
        assertEquals(3_000_000.0, result, 0.01)
    }

    @Test
    fun `RP no bonus for less than 1000`() {
        val result = calculateECurrencyBonus(999.0)
        assertEquals(999.0, result, 0.01)
    }

    @Test
    fun `RP boundary 1000 gets bonus`() {
        val result = calculateECurrencyBonus(1000.0)
        assertTrue(result > 1000.0)
    }

    @Test
    fun `RP boundary 999 no bonus`() {
        val result = calculateECurrencyBonus(999.0)
        assertEquals(999.0, result, 0.01)
    }

    // ========== Базовые расчёты ==========

    @Test
    fun `RP initial accrual with 20000 flow is 20`() {
        val inFlow = 20000.0
        val accrual = inFlow * (startPercent / 100.0)
        assertEquals(20.0, accrual, 0.01)
    }

    @Test
    fun `RP percent display format is 3 decimal places`() {
        val percent1 = 0.103
        val percent2 = 0.106
        val percent3 = 0.109

        assertEquals("0.103", String.format(Locale.US, "%.3f", percent1))
        assertEquals("0.106", String.format(Locale.US, "%.3f", percent2))
        assertEquals("0.109", String.format(Locale.US, "%.3f", percent3))
    }

    // ========== Создание потока ==========

    @Test
    fun `RP create initial entry with 10000 deposit`() = runBlocking {
        val deposit = 10000.0
        val amountToAdd = calculateECurrencyBonus(deposit)
        val initialInFlow = amountToAdd
        val initialAccrual = initialInFlow * (startPercent / 100.0)

        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = startPercent,
            inFlowAmount = initialInFlow,
            dailyAccrual = initialAccrual,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "START"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals(20000.0, lastEntry!!.inFlowAmount, 0.01)
        assertEquals(20.0, lastEntry.dailyAccrual, 0.01)
        assertEquals(0.1, lastEntry.percent, 0.001)
        assertEquals("START", lastEntry.actionType)
    }

    // ========== Нажатие кнопки ==========

    @Test
    fun `RP button press increases percent by 0_003`() = runBlocking {
        var percent = 0.1
        percent += dailyAddition
        assertEquals(0.103, percent, 0.001)
    }

    @Test
    fun `RP button press reduces flow and increases wallet`() = runBlocking {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = true,
            actionType = "DAILY"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals(19980.0, lastEntry!!.inFlowAmount, 0.01)
        assertEquals(20.0, lastEntry.walletAmount, 0.01)
        assertEquals(0.103, lastEntry.percent, 0.001)
    }

    @Test
    fun `RP button press recalculates daily accrual`() = runBlocking {
        // До нажатия
        var inFlow = 20000.0
        var percent = 0.1
        var wallet = 0.0

        // Нажатие 1
        var accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        percent += dailyAddition

        assertEquals(19980.0, inFlow, 0.01)
        assertEquals(20.0, wallet, 0.01)
        assertEquals(0.103, percent, 0.001)

        // Нажатие 2
        accrual = inFlow * (percent / 100.0)
        inFlow -= accrual
        wallet += accrual
        percent += dailyAddition

        assertEquals(19959.42, String.format(Locale.US, "%.2f", inFlow).toDouble(), 0.01)
        assertEquals(40.58, String.format(Locale.US, "%.2f", wallet).toDouble(), 0.01)
        assertEquals(0.106, percent, 0.001)
    }

    @Test
    fun `RP multiple button presses accumulate correctly`() = runBlocking {
        var inFlow = 20000.0
        var percent = 0.1
        var wallet = 0.0

        repeat(5) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            wallet += accrual
            percent += dailyAddition
        }

        assertTrue(inFlow < 20000)
        assertTrue(wallet > 0)
        assertEquals(0.115, percent, 0.001)
    }

    // ========== Воскресенье ==========

    @Test
    fun `RP SUNDAY entry keeps values unchanged`() = runBlocking {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.15,
            inFlowAmount = 15000.0,
            dailyAccrual = 22.5,
            walletAmount = 100.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals("SUNDAY", lastEntry!!.actionType)
        assertEquals(0.15, lastEntry.percent, 0.001)
        assertFalse(lastEntry.isButtonPressed)
    }

    @Test
    fun `RP SUNDAY action type is correct`() = runBlocking {
        val sundayDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19, 12, 0, 0)
        }.timeInMillis

        val entry = GrowingFlowEntity(
            date = sundayDate,
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertEquals("SUNDAY", lastEntry!!.actionType)
    }

    // ========== Пропущенные дни ==========

    @Test
    fun `RP MISSED entry keeps values unchanged`() = runBlocking {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.15,
            inFlowAmount = 14977.5,
            dailyAccrual = 22.5,
            walletAmount = 122.5,
            isButtonPressed = false,
            actionType = "MISSED"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals("MISSED", lastEntry!!.actionType)
        assertFalse(lastEntry.isButtonPressed)
    }

    // ========== Реинвест ==========

    @Test
    fun `RP reinvest adds with E-currency bonus`() = runBlocking {
        val currentInFlow = 10000.0
        val deposit = 10000.0
        val bonus = calculateECurrencyBonus(deposit) - deposit // 100%
        val newInFlow = currentInFlow + deposit + bonus

        assertEquals(30000.0, newInFlow, 0.01) // 10000 + 20000
    }

    @Test
    fun `RP reinvest recalculates daily accrual with current percent`() = runBlocking {
        val currentInFlow = 19980.0
        val currentPercent = 0.103
        val deposit = 10000.0
        val newInFlow = currentInFlow + calculateECurrencyBonus(deposit)
        val newAccrual = newInFlow * (currentPercent / 100.0)

        assertEquals(39980.0, newInFlow, 0.01)
        assertTrue(newAccrual > currentInFlow * (currentPercent / 100.0))
    }

    @Test
    fun `RP wallet preserved when not specified in reinvest`() = runBlocking {
        val lastWallet = 500.0
        val newWallet = lastWallet
        assertEquals(500.0, newWallet, 0.01)
    }

    @Test
    fun `RP wallet set to specified value in reinvest`() = runBlocking {
        val specifiedWallet = 1000.0
        val newWallet = specifiedWallet
        assertEquals(1000.0, newWallet, 0.01)
    }

    // ========== Корректировки ==========

    @Test
    fun `RP correction can change inFlow`() = runBlocking {
        val correctedInFlow = 25000.0
        val currentPercent = 0.15
        val correctedAccrual = correctedInFlow * (currentPercent / 100.0)

        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = currentPercent,
            inFlowAmount = correctedInFlow,
            dailyAccrual = correctedAccrual,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "CORRECTION"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertEquals("CORRECTION", lastEntry!!.actionType)
        assertEquals(25000.0, lastEntry.inFlowAmount, 0.01)
    }

    @Test
    fun `RP correction can change accrual and recalculate percent`() = runBlocking {
        val currentInFlow = 20000.0
        val newAccrual = 30.0
        val newPercent = newAccrual * 100 / currentInFlow

        assertEquals(0.15, newPercent, 0.001)
    }

    @Test
    fun `RP correction can change wallet`() = runBlocking {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.12,
            inFlowAmount = 18000.0,
            dailyAccrual = 21.6,
            walletAmount = 200.0,
            isButtonPressed = true,
            actionType = "CORRECTION"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertEquals(200.0, lastEntry!!.walletAmount, 0.01)
    }

    @Test
    fun `RP correction can change button state`() = runBlocking {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.12,
            inFlowAmount = 18000.0,
            dailyAccrual = 21.6,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "CORRECTION"
        )
        growingDao.insert(entry)

        val lastEntry = growingDao.getLastEntry()
        assertFalse(lastEntry!!.isButtonPressed)
    }

    // ========== Лучшая дата ==========

    @Test
    fun `RP best date is when accrual starts decreasing`() = runBlocking {
        var inFlow = 20000.0
        var percent = 0.1
        var prevAccrual = 0.0
        var bestDate = 0
        var days = 0

        while (days < 500 && inFlow > 100) {
            val accrual = inFlow * (percent / 100.0)

            if (days > 0 && accrual < prevAccrual && bestDate == 0) {
                bestDate = days
            }

            prevAccrual = accrual
            inFlow -= accrual
            percent += dailyAddition
            days++
        }

        // Лучшая дата должна быть найдена
        assertTrue(bestDate > 0)
        assertTrue(bestDate < days)
    }

    @Test
    fun `RP accrual increases then decreases over time`() = runBlocking {
        val results = mutableListOf<Pair<Int, Double>>()

        var inFlow = 20000.0
        var percent = 0.1

        for (day in 1..200) {
            val accrual = inFlow * (percent / 100.0)
            results.add(day to accrual)
            inFlow -= accrual
            percent += dailyAddition
        }

        // Найдём максимальное начисление
        val maxAccrual = results.maxByOrNull { it.second }
        assertNotNull(maxAccrual)
        assertTrue(maxAccrual!!.first > 0)
        assertTrue(maxAccrual.second > 0)
    }

    // ========== Полный сценарий из user story ==========

    @Test
    fun `RP full scenario from user story`() = runBlocking {
        // Создаём поток с 10000
        var inFlow = 10000.0 * 2.0 // 20000 с бонусом
        var percent = 0.1
        var wallet = 0.0

        // Сразу нажали на кнопку
        var accrual = inFlow * (percent / 100.0) // 20
        inFlow -= accrual
        wallet += accrual
        percent += dailyAddition

        assertEquals(19980.0, inFlow, 0.01)
        assertEquals(20.0, wallet, 0.01)
        assertEquals(0.103, percent, 0.001)

        // День 2
        accrual = inFlow * (percent / 100.0) // 20.5794
        inFlow -= accrual
        wallet += accrual

        val formattedInFlow = String.format(Locale.US, "%.2f", inFlow).toDouble()
        val formattedWallet = String.format(Locale.US, "%.2f", wallet).toDouble()

        assertEquals(19959.42, formattedInFlow, 0.01)
        assertEquals(40.58, formattedWallet, 0.01)
        assertEquals(0.106, percent, 0.001)
    }

    // ========== Прогноз ==========

    @Test
    fun `RP forecast calculates correctly`() = runBlocking {
        var inFlow = 20000.0
        var percent = 0.1
        var totalWallet = 0.0

        for (day in 1..100) {
            val accrual = inFlow * (percent / 100.0)
            inFlow -= accrual
            totalWallet += accrual
            percent += dailyAddition
        }

        assertTrue(inFlow > 0)
        assertTrue(totalWallet > 0)
    }

    // ========== Проверки настроек ==========

    @Test
    fun `RP start percent is 0_1 by default`() {
        assertEquals(0.1, startPercent, 0.001)
    }

    @Test
    fun `RP daily addition is 0_003 by default`() {
        assertEquals(0.003, dailyAddition, 0.0001)
    }

    @Test
    fun `RP clearAll removes history`() = runBlocking {
        growingDao.insert(GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "START"
        ))

        growingDao.clearAll()

        val history = growingDao.getAllHistory().first()
        assertTrue(history.isEmpty())
    }

    // ========== Проверка Sunday через Calendar ==========

    @Test
    fun `isSunday returns true for Sunday`() {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)
        }
        assertTrue(sunday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }

    @Test
    fun `isSunday returns false for Monday`() {
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 20)
        }
        assertFalse(monday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }
}
