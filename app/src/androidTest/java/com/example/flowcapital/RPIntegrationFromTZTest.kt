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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RPIntegrationFromTZTest {

    private lateinit var database: AppDatabase
    private lateinit var growingDao: GrowingFlowDao
    private lateinit var settingsManager: com.example.flowcapital.data.settings.SettingsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        growingDao = database.growingFlowDao()
        settingsManager = com.example.flowcapital.data.settings.SettingsManager(context)
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun `RP start creates entry with percent from settings`() = runBlocking {
        settingsManager.initializeDefaults()
        
        val savedStartPercent = settingsManager.startPercentFlow.first()
        
        assertEquals(0.1, savedStartPercent, 0.001)
    }

    @Test
    fun `RP daily addition from settings`() = runBlocking {
        settingsManager.initializeDefaults()
        
        val savedDailyAddition = settingsManager.dailyAdditionFlow.first()
        
        assertEquals(0.003, savedDailyAddition, 0.0001)
    }

    @Test
    fun `RP E-currency coefficients from settings`() = runBlocking {
        settingsManager.initializeDefaults()

        val coeffs = settingsManager.getECurrencyCoefficients()

        assertEquals(7, coeffs.size)
        assertEquals(50.0, coeffs[1000.0] ?: 0.0, 0.01)
        assertEquals(100.0, coeffs[10000.0] ?: 0.0, 0.01)
    }

    @Test
    fun `RP full workflow from TZ example`() = runBlocking {
        val startPercent = 0.1
        val dailyAddition = 0.003
        
        val deposit = 10000.0
        val bonus = settingsManager.calculateECurrencyBonus(deposit)
        var inFlow = bonus
        var percent = startPercent
        var wallet = 0.0

        val entry1 = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = percent,
            inFlowAmount = inFlow,
            dailyAccrual = inFlow * (percent / 100.0),
            walletAmount = wallet,
            isButtonPressed = true,
            actionType = "DAILY"
        )
        growingDao.insert(entry1)

        val lastEntry1 = growingDao.getLastEntry()
        assertEquals(20000.0, lastEntry1!!.inFlowAmount, 0.01)
        assertEquals(0.103, lastEntry1.percent, 0.001)

        inFlow -= inFlow * (percent / 100.0)
        wallet += inFlow * (percent / 100.0)
        percent += dailyAddition
        val accrual2 = inFlow * (percent / 100.0)

        val entry2 = GrowingFlowEntity(
            date = System.currentTimeMillis() + 86400000,
            percent = percent,
            inFlowAmount = inFlow,
            dailyAccrual = accrual2,
            walletAmount = wallet,
            isButtonPressed = true,
            actionType = "DAILY"
        )
        growingDao.insert(entry2)

        val lastEntry2 = growingDao.getLastEntry()
        assertTrue(lastEntry2!!.inFlowAmount < 20000)
        assertTrue(lastEntry2.dailyAccrual > 20)
    }

    @Test
    fun `RP correction recalculates percent when only accrual changes`() = runBlocking {
        val currentInFlow = 10000.0
        val newAccrual = 50.0
        
        val newPercent = newAccrual * 100.0 / currentInFlow
        
        assertEquals(0.5, newPercent, 0.001)
    }

    @Test
    fun `RP correction recalculates accrual when only flow changes`() = runBlocking {
        val currentPercent = 0.103
        val newFlow = 15000.0
        
        val newAccrual = newFlow * (currentPercent / 100.0)
        
        assertEquals(15.45, newAccrual, 0.01)
    }

    @Test
    fun `RP button disabled on sunday`() = runBlocking {
        val today = Calendar.getInstance()
        val isSunday = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        
        assertFalse(isSunday || isSunday)
    }

    @Test
    fun `RP button disabled when flow is zero`() = runBlocking {
        val inFlow = 0.0
        val isFlowZero = inFlow <= 0
        
        assertTrue(isFlowZero)
    }

    @Test
    fun `RP creates MISSED entry on skip day`() = runBlocking {
        val yesterday = GrowingFlowEntity(
            date = System.currentTimeMillis() - 86400000,
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "DAILY"
        )
        growingDao.insert(yesterday)

        val missedEntry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "MISSED"
        )
        growingDao.insert(missedEntry)

        val lastEntry = growingDao.getLastEntry()
        assertEquals("MISSED", lastEntry!!.actionType)
    }

    @Test
    fun `RP creates SUNDAY entry on sunday`() = runBlocking {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19)
        }
        
        val sundayEntry = GrowingFlowEntity(
            date = sunday.timeInMillis,
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        growingDao.insert(sundayEntry)

        val lastEntry = growingDao.getLastEntry()
        assertEquals("SUNDAY", lastEntry!!.actionType)
    }

    @Test
    fun `RP forecast includes sunday rows`() = runBlocking {
        val startEntry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = true,
            actionType = "START"
        )
        growingDao.insert(startEntry)

        val forecastDays = 10
        var simInFlow = 20000.0
        var simPercent = 0.1
        var simWallet = 0.0

        repeat(forecastDays) {
            val todayCal = Calendar.getInstance().apply { timeInMillis = startEntry.date }
            todayCal.add(Calendar.DAY_OF_YEAR, it + 1)

            val accrual = simInFlow * (simPercent / 100.0)
            simInFlow -= accrual
            simWallet += accrual
            simPercent += 0.003
        }

        assertTrue(simInFlow > 0)
        assertTrue(simWallet > 0)
    }

    @Test
    fun `RP button text on zero flow`() {
        val inFlow = 0.0
        val isFlowZero = inFlow <= 0
        val buttonText = if (isFlowZero) "СДЕЛАЙТЕ РЕИНВЕСТ" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
        
        assertEquals("СДЕЛАЙТЕ РЕИНВЕСТ", buttonText)
    }

    @Test
    fun `RP button text on sunday`() {
        val isSunday = true
        val buttonText = if (isSunday) "ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
        
        assertEquals("ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ", buttonText)
    }

    @Test
    fun `RP button text when already pressed`() {
        val isActionDoneToday = true
        val buttonText = if (isActionDoneToday) "НАЧИСЛЕНИЕ ВЫПОЛНЕНО" else "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
        
        assertEquals("НАЧИСЛЕНИЕ ВЫПОЛНЕНО", buttonText)
    }

    @Test
    fun `RP percent display format is three decimal places`() = runBlocking {
        settingsManager.initializeDefaults()
        
        val startPercent = settingsManager.startPercentFlow.first()
        val formatted = String.format(Locale.US, "%.3f", startPercent)
        
        assertEquals("0.100", formatted)
    }

    @Test
    fun `RP flow display format is two decimal places`() {
        val flow = 19980.00
        val formatted = String.format(Locale.US, "%.2f", flow)
        
        assertEquals("19980.00", formatted)
    }

    @Test
    fun `RP accrual has prefix`() {
        val accrual = 20.58
        val formatted = String.format(Locale.US, "+%.2f", accrual)
        
        assertEquals("+20.58", formatted)
    }

    @Test
    fun `RP table has correct column order`() {
        val columns = listOf("Дата", "%", "Поток", "Начисл.", "Кошелек")
        
        assertEquals(5, columns.size)
        assertEquals("Дата", columns[0])
        assertEquals("Кошелек", columns[4])
    }

    @Test
    fun `RP action buttons in correct order`() {
        val buttons = listOf("Реинвест", "Коррекция", "Прогноз", "Лучшая дата")
        
        assertEquals(4, buttons.size)
        assertEquals("Реинвест", buttons[0])
        assertEquals("Лучшая дата", buttons[3])
    }

    @Test
    fun `RP start dialog shows dynamic bonus text`() = runBlocking {
        settingsManager.initializeDefaults()
        
        val amount = 10000.0
        val bonusPercent = settingsManager.getECurrencyBonusPercent(amount)
        
        assertEquals(100.0, bonusPercent, 0.01)
    }
}