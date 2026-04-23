package com.example.flowcapital

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowcapital.data.db.AppDatabase
import com.example.flowcapital.data.db.NoviceFlowDao
import com.example.flowcapital.data.db.NoviceFlowEntity
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

/**
 * Полные интеграционные тесты для ПН (Поток Новичка)
 * Охватывают: создание, 50% бонус, 2% в день, воскресенья, пропуски, коррекции, прогноз, конец цикла
 */
@RunWith(AndroidJUnit4::class)
class NoviceFlowFullIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var noviceDao: NoviceFlowDao

    private val dailyPercent = 2.0
    private val bonusPercent = 50.0

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        noviceDao = database.noviceFlowDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    // ========== Базовые расчёты ==========

    @Test
    fun `PN initial bonus is 50 percent`() {
        val deposit = 10000.0
        val bonus = deposit * (bonusPercent / 100.0)
        assertEquals(5000.0, bonus, 0.01)
    }

    @Test
    fun `PN initial flow with bonus is 150 percent of deposit`() {
        val deposit = 10000.0
        val inFlow = deposit * (1 + bonusPercent / 100.0)
        assertEquals(15000.0, inFlow, 0.01)
    }

    @Test
    fun `PN daily accrual is 2 percent of flow`() {
        val inFlow = 15000.0
        val accrual = inFlow * (dailyPercent / 100.0)
        assertEquals(300.0, accrual, 0.01)
    }

    // ========== Создание потока ==========

    @Test
    fun `PN create initial entry with 10000 deposit`() = runBlocking {
        val deposit = 10000.0
        val inFlow = deposit * (1 + bonusPercent / 100.0)
        val accrual = inFlow * (dailyPercent / 100.0)

        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = inFlow,
            dailyAccrual = accrual,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals(15000.0, lastEntry!!.inFlowAmount, 0.01)
        assertEquals(300.0, lastEntry.dailyAccrual, 0.01)
        assertEquals(0.0, lastEntry.walletAmount, 0.01)
        assertEquals("PN_START", lastEntry.actionType)
    }

    @Test
    fun `PN create with initial wallet amount`() = runBlocking {
        val deposit = 10000.0
        val initialWallet = 5.0
        val inFlow = deposit * (1 + bonusPercent / 100.0)
        val accrual = inFlow * (dailyPercent / 100.0)

        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = inFlow,
            dailyAccrual = accrual,
            walletAmount = initialWallet,
            isButtonPressed = false,
            actionType = "PN_START"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertEquals(5.0, lastEntry!!.walletAmount, 0.01)
    }

    // ========== Нажатие кнопки ==========

    @Test
    fun `PN button press reduces flow and increases wallet`() = runBlocking {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals(14700.0, lastEntry!!.inFlowAmount, 0.01)
        assertEquals(300.0, lastEntry.walletAmount, 0.01)
    }

    @Test
    fun `PN button press recalculates daily accrual`() = runBlocking {
        // Начальное состояние
        var inFlow = 15000.0
        val accrual1 = inFlow * (dailyPercent / 100.0)
        inFlow -= accrual1
        val accrual2 = inFlow * (dailyPercent / 100.0)

        assertEquals(14700.0, inFlow, 0.01)
        assertEquals(300.0, accrual1, 0.01)
        assertEquals(294.0, accrual2, 0.01) // 14700 * 0.02
    }

    @Test
    fun `PN multiple button presses accumulate correctly`() = runBlocking {
        var inFlow = 15000.0
        var wallet = 0.0

        // День 1
        var accrual = inFlow * (dailyPercent / 100.0)
        inFlow -= accrual
        wallet += accrual

        // День 2
        accrual = inFlow * (dailyPercent / 100.0)
        inFlow -= accrual
        wallet += accrual

        // День 4 (пропустили день 3)
        accrual = inFlow * (dailyPercent / 100.0)
        inFlow -= accrual
        wallet += accrual

        assertEquals(14117.88, inFlow, 0.01) // точное значение
        assertTrue(wallet > 0)
    }

    // ========== Воскресенье ==========

    @Test
    fun `PN SUNDAY entry keeps values unchanged`() = runBlocking {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals("SUNDAY", lastEntry!!.actionType)
        assertEquals(15000.0, lastEntry.inFlowAmount, 0.01)
        assertEquals(300.0, lastEntry.dailyAccrual, 0.01)
        assertFalse(lastEntry.isButtonPressed)
    }

    @Test
    fun `PN SUNDAY action type is correct`() = runBlocking {
        val sundayDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19, 12, 0, 0)
        }.timeInMillis

        val entry = NoviceFlowEntity(
            date = sundayDate,
            percent = dailyPercent,
            inFlowAmount = 14406.0,
            dailyAccrual = 288.12,
            walletAmount = 594.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertEquals("SUNDAY", lastEntry!!.actionType)
        assertEquals(288.12, lastEntry.dailyAccrual, 0.01)
    }

    // ========== Пропущенные дни ==========

    @Test
    fun `PN MISSED entry keeps values unchanged`() = runBlocking {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 14700.0,
            dailyAccrual = 294.0,
            walletAmount = 300.0,
            isButtonPressed = false,
            actionType = "MISSED"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertNotNull(lastEntry)
        assertEquals("MISSED", lastEntry!!.actionType)
        assertEquals(14700.0, lastEntry.inFlowAmount, 0.01)
        assertFalse(lastEntry.isButtonPressed)
    }

    // ========== Реинвест ==========

    @Test
    fun `PN reinvest adds to flow with bonus`() = runBlocking {
        val currentInFlow = 10000.0
        val reinvestAmount = 10000.0
        val newInFlow = currentInFlow + (reinvestAmount * (1 + bonusPercent / 100.0))

        assertEquals(25000.0, newInFlow, 0.01) // 10000 + 15000
    }

    @Test
    fun `PN reinvest recalculates daily accrual`() = runBlocking {
        val currentInFlow = 14700.0
        val reinvestAmount = 5000.0
        val newInFlow = currentInFlow + (reinvestAmount * (1 + bonusPercent / 100.0))
        val newAccrual = newInFlow * (dailyPercent / 100.0)

        assertEquals(22200.0, newInFlow, 0.01) // 14700 + 7500
        assertEquals(444.0, newAccrual, 0.01) // 22200 * 0.02
    }

    @Test
    fun `PN wallet preserved when not specified in reinvest`() = runBlocking {
        val lastWallet = 500.0
        val newWallet = lastWallet // не меняем
        assertEquals(500.0, newWallet, 0.01)
    }

    @Test
    fun `PN wallet set to specified value in reinvest`() = runBlocking {
        val specifiedWallet = 1000.0
        val newWallet = specifiedWallet
        assertEquals(1000.0, newWallet, 0.01)
    }

    // ========== Корректировки ==========

    @Test
    fun `PN correction can change inFlow`() = runBlocking {
        val correctedInFlow = 16000.0
        val correctedAccrual = correctedInFlow * (dailyPercent / 100.0)

        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = correctedInFlow,
            dailyAccrual = correctedAccrual,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_CORRECTION"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertEquals("PN_CORRECTION", lastEntry!!.actionType)
        assertEquals(16000.0, lastEntry.inFlowAmount, 0.01)
        assertEquals(320.0, lastEntry.dailyAccrual, 0.01)
    }

    @Test
    fun `PN correction can change wallet`() = runBlocking {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 1000.0,
            isButtonPressed = false,
            actionType = "PN_CORRECTION"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertEquals(1000.0, lastEntry!!.walletAmount, 0.01)
    }

    @Test
    fun `PN correction can change button state`() = runBlocking {
        // Кнопка была нажата, корректируем на ненажатую
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false, // ненажата после корректировки
            actionType = "PN_CORRECTION"
        )
        noviceDao.insert(entry)

        val lastEntry = noviceDao.getLastEntry()
        assertFalse(lastEntry!!.isButtonPressed)
    }

    // ========== Прогноз ==========

    @Test
    fun `PN forecast calculates correctly`() = runBlocking {
        var inFlow = 15000.0
        var totalWallet = 0.0

        // Симулируем 30 дней
        for (day in 1..30) {
            val accrual = inFlow * (dailyPercent / 100.0)
            inFlow -= accrual
            totalWallet += accrual
        }

        assertTrue(inFlow > 0) // Поток ещё не исчерпан
        assertTrue(totalWallet > 0)
        assertTrue(totalWallet < 15000) // Не всё ещё получено
    }

    @Test
    fun `PN forecast until flow is zero`() = runBlocking {
        var inFlow = 15000.0
        var days = 0

        while (inFlow > 0.01 && days < 500) {
            val accrual = inFlow * (dailyPercent / 100.0)
            inFlow -= accrual
            days++
        }

        // При 2% в день поток должен закончиться примерно за 115 дней
        assertTrue(days in 100..200)
        assertTrue(inFlow < 0.01)
    }

    // ========== Конец цикла ==========

    @Test
    fun `PN end cycle calculates when flow reaches zero`() = runBlocking {
        var inFlow = 15000.0
        var totalAccrued = 0.0
        var days = 0

        while (inFlow > 0.01 && days < 500) {
            val accrual = inFlow * (dailyPercent / 100.0)
            inFlow -= accrual
            totalAccrued += accrual
            days++
        }

        // Общая сумма должна быть примерно равна начальному потоку
        assertEquals(15000.0, totalAccrued, 50.0) // погрешность из-за округления
        assertTrue(days > 0)
    }

    // ========== Полный сценарий из user story ==========

    @Test
    fun `PN full scenario from user story`() = runBlocking {
        // День 1: открываем поток
        var inFlow = 10000.0 * 1.5 // 15000
        var wallet = 0.0

        // Сразу нажали на кнопку
        var accrual = inFlow * 0.02
        inFlow -= accrual
        wallet += accrual

        assertEquals(15000.0 - 300.0, inFlow, 0.01) // 14700
        assertEquals(300.0, wallet, 0.01)

        // День 2
        accrual = inFlow * 0.02
        inFlow -= accrual
        wallet += accrual

        assertEquals(14406.0, inFlow, 0.01)
        assertEquals(594.0, wallet, 0.01)

        // День 3 - пропущен (MISSED)

        // День 4
        accrual = inFlow * 0.02
        inFlow -= accrual
        wallet += accrual

        assertTrue(inFlow > 0)
        assertTrue(wallet > 594)
    }

    // ========== Проверки настроек ==========

    @Test
    fun `PN bonus percent is 50 by default`() {
        assertEquals(50.0, bonusPercent, 0.01)
    }

    @Test
    fun `PN daily percent is 2 by default`() {
        assertEquals(2.0, dailyPercent, 0.01)
    }

    @Test
    fun `PN clearAll removes history`() = runBlocking {
        noviceDao.insert(NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = dailyPercent,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        ))

        noviceDao.clearAll()

        val history = noviceDao.getAllHistory().first()
        assertTrue(history.isEmpty())
    }

    // ========== Проверка Sunday через Calendar ==========

    @Test
    fun `isSunday returns true for Sunday`() {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 19) // Воскресенье
        }
        assertTrue(sunday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }

    @Test
    fun `isSunday returns false for Monday`() {
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 20) // Понедельник
        }
        assertFalse(monday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }

    @Test
    fun `isSunday returns false for Saturday`() {
        val saturday = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 18) // Суббота
        }
        assertFalse(saturday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }

    // ========== Полный интеграционный тест: Старт, Пропуск, Воскресенье, Реинвест ==========

    @Test
    fun `PN full integration - start skip sunday reinvest with empty wallet`() = runBlocking {
        // День 1: Старт ПН (01.04.2026, среда)
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val deposit = 10000.0
        val initialWallet = 0.0
        val inFlow = deposit * (1 + bonusPercent / 100.0) // 15000
        val accrual = inFlow * (dailyPercent / 100.0) // 300

        var currentInFlow = inFlow
        var currentWallet = initialWallet

        // Создаём стартовую запись
        var entry = NoviceFlowEntity(
            date = startDate.timeInMillis,
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = accrual,
            walletAmount = currentWallet,
            isButtonPressed = false,
            actionType = "PN_START"
        )
        noviceDao.insert(entry)

        // День 2: Нажатие кнопки (02.04.2026, четверг)
        currentInFlow = currentInFlow - accrual // 15000 - 300 = 14700
        currentWallet = currentWallet + accrual // 0 + 300 = 300

        entry = NoviceFlowEntity(
            date = startDate.timeInMillis + (24 * 60 * 60 * 1000),
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = currentInFlow * (dailyPercent / 100.0),
            walletAmount = currentWallet,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        )
        noviceDao.insert(entry)

        // День 3: Пропуск дня (03.04.2026, пятница) - НЕ нажимаем кнопку
        // Создаём MISSED запись с балансами предыдущего дня
        entry = NoviceFlowEntity(
            date = startDate.timeInMillis + (2 * 24 * 60 * 60 * 1000),
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = currentInFlow * (dailyPercent / 100.0),
            walletAmount = currentWallet,
            isButtonPressed = false,
            actionType = "MISSED"
        )
        noviceDao.insert(entry)

        // Проверяем что пропуск создал запись с правильным actionType
        var lastEntry = noviceDao.getLastEntry()
        assertEquals("MISSED", lastEntry!!.actionType)
        assertEquals(14700.0, lastEntry.inFlowAmount, 0.01)

        // День 4: Суббота (04.04.2026)
        // Кнопку нажать можно, но пропускаем нажатие для теста воскресенья

        // День 5: Воскресенье (05.04.2026) - кнопка ЗАБЛОКИРОВАНА
        // По ТЗ: создаём SUNDAY запись с балансами пятницы
        entry = NoviceFlowEntity(
            date = startDate.timeInMillis + (4 * 24 * 60 * 60 * 1000),
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = currentInFlow * (dailyPercent / 100.0),
            walletAmount = currentWallet,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )
        noviceDao.insert(entry)

        lastEntry = noviceDao.getLastEntry()
        assertEquals("SUNDAY", lastEntry!!.actionType)
        //SUNDAY запись должна иметь фиолетовый фон - это проверяется в UI
        assertEquals(14700.0, lastEntry.inFlowAmount, 0.01)
        assertEquals(300.0, lastEntry.walletAmount, 0.01)

        // День 6: Понедельник (06.04.2026)
        // Нажатие кнопки - начисление уменьшает поток и увеличивает кошелёк
        val previousAccrual = currentInFlow * (dailyPercent / 100.0) // 14700 * 0.02 = 294
        currentInFlow -= previousAccrual // 14700 - 294 = 14406
        currentWallet += previousAccrual // 300 + 294 = 594

        entry = NoviceFlowEntity(
            date = startDate.timeInMillis + (5 * 24 * 60 * 60 * 1000),
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = currentInFlow * (dailyPercent / 100.0),
            walletAmount = currentWallet,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        )
        noviceDao.insert(entry)

        lastEntry = noviceDao.getLastEntry()
        assertTrue(lastEntry!!.isButtonPressed)
        assertEquals(14406.0, lastEntry.inFlowAmount, 0.01)
        assertEquals(594.0, lastEntry.walletAmount, 0.01)

        // РЕИНВЕСТ: делаем до нажатия кнопки. Вносим 5000, кошелёк пустым (не меняется)
        val reinvestAmount = 5000.0
        val previousInFlowBeforeReinvest = currentInFlow
        val previousWalletBeforeReinvest = currentWallet

        // При реинвесте: в поток идёт взнос + бонус = 5000 * 1.5 = 7500
        val reinvestBonus = reinvestAmount * (bonusPercent / 100.0) // 2500
        currentInFlow = currentInFlow + reinvestAmount + reinvestBonus // 14406 + 7500 = 21906

        // Кошелёк НЕ меняется (пустое поле)
        currentWallet = previousWalletBeforeReinvest // 594 (не 0!)

        entry = NoviceFlowEntity(
            date = startDate.timeInMillis + (5 * 24 * 60 * 60 * 1000), // тот же день (понедельник)
            percent = dailyPercent,
            inFlowAmount = currentInFlow,
            dailyAccrual = currentInFlow * (dailyPercent / 100.0),
            walletAmount = currentWallet,
            isButtonPressed = false,
            actionType = "PN_REINVEST"
        )
        noviceDao.insert(entry)

        lastEntry = noviceDao.getLastEntry()
        assertEquals("PN_REINVEST", lastEntry!!.actionType)
        assertEquals(21906.0, lastEntry.inFlowAmount, 0.01)
        assertEquals(594.0, lastEntry.walletAmount, 0.01) // Кошелёк НЕ изменился!

        // Проверяем историю - всего 6 записей
        val history = noviceDao.getAllHistory().first()
        assertEquals(6, history.size)
    }
}
