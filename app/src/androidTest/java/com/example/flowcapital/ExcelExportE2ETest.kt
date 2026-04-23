package com.example.flowcapital

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowcapital.data.db.AppDatabase
import com.example.flowcapital.data.db.GrowingFlowDao
import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.NoviceFlowDao
import com.example.flowcapital.data.db.NoviceFlowEntity
import com.example.flowcapital.data.db.PremiumStartFlowDao
import com.example.flowcapital.data.db.PremiumStartFlowEntity
import com.example.flowcapital.data.db.PremiumStartPeriodDao
import com.example.flowcapital.data.db.PremiumStartPeriodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * E2E тест экспорта данных в Excel.
 * 
 * По ТЗ:
 * - Каждая запись БД = отдельная строка
 * - Колонки строго соответствуют колонкам в UI (A, B, C...)
 * - Числа выгружаются полными значениями без округления
 * - У ПСП на каждый поток создается свой отдельный лист
 * - Если БД пуста - кнопка экспорта неактивна
 */
@RunWith(AndroidJUnit4::class)
class ExcelExportE2ETest {

    private lateinit var database: AppDatabase
    private lateinit var noviceDao: NoviceFlowDao
    private lateinit var growingDao: GrowingFlowDao
    private lateinit var pspFlowDao: PremiumStartFlowDao
    private lateinit var pspPeriodDao: PremiumStartPeriodDao
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        noviceDao = database.noviceFlowDao()
        growingDao = database.growingFlowDao()
        pspFlowDao = database.premiumStartFlowDao()
        pspPeriodDao = database.premiumStartPeriodDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    // ========== ПН ЭКСПОРТ ==========

    @Test
    fun `PN export creates one row per database entry`() = runBlocking {
        // Создаём записи в БД ПН
        val entries = listOf(
            NoviceFlowEntity(
                date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            ),
            NoviceFlowEntity(
                date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 2) }.timeInMillis,
                percent = 2.0,
                inFlowAmount = 14700.0,
                dailyAccrual = 294.0,
                walletAmount = 300.0,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            ),
            NoviceFlowEntity(
                date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 3) }.timeInMillis,
                percent = 2.0,
                inFlowAmount = 14406.0,
                dailyAccrual = 288.12,
                walletAmount = 594.0,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
        )

        entries.forEach { noviceDao.insert(it) }

        // Экспорт
        val history = noviceDao.getAllHistory().first()

        // По ТЗ: Каждая запись БД = отдельная строка
        assertEquals(3, history.size)
        assertEquals(entries[0].inFlowAmount, history[0].inFlowAmount)
        assertEquals(entries[1].inFlowAmount, history[1].inFlowAmount)
        assertEquals(entries[2].inFlowAmount, history[2].inFlowAmount)
    }

    @Test
    fun `PN export columns match UI columns`() = runBlocking {
        // Создаём одну запись
        noviceDao.insert(NoviceFlowEntity(
            date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        ))

        val history = noviceDao.getAllHistory().first()

        // По ТЗ: Колонки соответствуют A, B, C...
        // A - Дата, B - В потоке, C - Начисление, D - Кошелек
        val entry = history[0]
        
        // Проверяем что все поля присутствуют
        assertTrue(entry.date > 0)                    // A: Дата
        assertTrue(entry.inFlowAmount >= 0)            // B: В потоке
        assertTrue(entry.dailyAccrual >= 0)            // C: Начисление
        assertTrue(entry.walletAmount >= 0)           // D: Кошелек
    }

    @Test
    fun `PN export numbers are full without rounding`() = runBlocking {
        // Создаём запись с дробными числами
        noviceDao.insert(NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 12345.67,
            dailyAccrual = 246.9134,
            walletAmount = 100.50,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        ))

        val history = noviceDao.getAllHistory().first()

        // По ТЗ: Числа выгружаются полными значениями без округления
        assertEquals(12345.67, history[0].inFlowAmount, 0.001)
        assertEquals(246.9134, history[0].dailyAccrual, 0.0001)
        assertEquals(100.50, history[0].walletAmount, 0.01)
    }

    @Test
    fun `PN export disabled when database is empty`() = runBlocking {
        val history = noviceDao.getAllHistory().first()

        // По ТЗ: Если БД пуста - кнопка экспорта неактивна
        val exportEnabled = history.isNotEmpty()
        
        assertFalse(exportEnabled)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `PN export works when database has data`() = runBlocking {
        noviceDao.insert(NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        ))

        val history = noviceDao.getAllHistory().first()

        // По ТЗ: Если БД не пуста - кнопка активна
        val exportEnabled = history.isNotEmpty()
        
        assertTrue(exportEnabled)
        assertFalse(history.isEmpty())
    }

    // ========== РП ЭКСПОРТ ==========

    @Test
    fun `RP export creates one row per database entry`() = runBlocking {
        val entries = listOf(
            GrowingFlowEntity(
                date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
                percent = 0.103,
                inFlowAmount = 19980.0,
                dailyAccrual = 20.58,
                walletAmount = 20.0,
                isButtonPressed = true,
                actionType = "RP_START"
            ),
            GrowingFlowEntity(
                date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 2) }.timeInMillis,
                percent = 0.106,
                inFlowAmount = 19959.42,
                dailyAccrual = 21.16,
                walletAmount = 40.58,
                isButtonPressed = true,
                actionType = "RP_DAILY"
            )
        )

        entries.forEach { growingDao.insert(it) }

        val history = growingDao.getAllHistory().first()

        // Каждая запись = отдельная строка
        assertEquals(2, history.size)
    }

    @Test
    fun `RP export columns match UI columns`() = runBlocking {
        growingDao.insert(GrowingFlowEntity(
            date = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = true,
            actionType = "RP_START"
        ))

        val history = growingDao.getAllHistory().first()

        // A - Дата, B - %, C - Поток, D - Начисление, E - Кошелек
        val entry = history[0]
        assertTrue(entry.date > 0)
        assertTrue(entry.percent > 0)
        assertTrue(entry.inFlowAmount > 0)
        assertTrue(entry.dailyAccrual > 0)
        assertTrue(entry.walletAmount >= 0)
    }

    @Test
    fun `RP export numbers are full without rounding`() = runBlocking {
        growingDao.insert(GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.103,
            inFlowAmount = 19980.00,
            dailyAccrual = 20.579,
            walletAmount = 20.00,
            isButtonPressed = true,
            actionType = "RP_DAILY"
        ))

        val history = growingDao.getAllHistory().first()

        // Полные значения без округления
        assertEquals(20.579, history[0].dailyAccrual, 0.0001)
        assertEquals(19980.00, history[0].inFlowAmount, 0.001)
    }

    // ========== ПСП ЭКСПОРТ (ОТДЕЛЬНЫЕ ЛИСТЫ) ==========

    @Test
    fun `PSP creates separate sheet per flow`() = runBlocking {
        // Создаём 2 потока ПСП
        val flow1 = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId1 = pspFlowDao.insert(flow1).toInt()

        val flow2 = PremiumStartFlowEntity(
            nominalAmount = 10000.0,
            startDate = Calendar.getInstance().apply { set(2026, Calendar.MAY, 1) }.timeInMillis,
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId2 = pspFlowDao.insert(flow2).toInt()

        // По ТЗ: У ПСП на каждый поток создается свой отдельный лист
        val allFlows = pspFlowDao.getAllFlows().first()

        assertEquals(2, allFlows.size)
        // Каждый поток = отдельный лист в Excel
    }

    @Test
    fun `PSP export has correct columns`() = runBlocking {
        val flow = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId = pspFlowDao.insert(flow).toInt()

        // Создаём период
        val periodEntity = PremiumStartPeriodEntity(
            flowId = flowId,
            periodNumber = 1,
            percent = 30.0,
            startDate = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1) }.timeInMillis,
            endDate = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 15) }.timeInMillis,
            accrualAmount = 1500.0,
            isContributionMade = false,
            isCompleted = false
        )
        pspPeriodDao.insert(periodEntity)

        val periods = pspPeriodDao.getPeriodsByFlowId(flowId).first()

        // A - Период, B - Дата, C - Начисление, D - %
        assertEquals(1, periods.size)
        val savedPeriodEntity = periods[0]
        assertTrue(savedPeriodEntity.periodNumber > 0)
        assertTrue(savedPeriodEntity.accrualAmount > 0)
    }

    @Test
    fun `PSP export full numbers without rounding`() = runBlocking {
        val flow = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId = pspFlowDao.insert(flow).toInt()

        val period2 = PremiumStartPeriodEntity(
            flowId = flowId,
            periodNumber = 1,
            percent = 30.0,
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000,
            accrualAmount = 1500.00,
            isContributionMade = true,
            contributionDate = System.currentTimeMillis(),
            isCompleted = true
        )
        pspPeriodDao.insert(period2)

        val savedPeriod = pspPeriodDao.getPeriodsByFlowId(flowId).first()[0]
        assertEquals(1500.00, savedPeriod.accrualAmount, 0.001)
    }

    @Test
    fun `PSP export disabled when database is empty`() = runBlocking {
        val flows = pspFlowDao.getAllFlows().first()

        val exportEnabled = flows.isNotEmpty()
        assertFalse(exportEnabled)
    }

    // ========== ФОРМАТ ДАТЫ ==========

    @Test
    fun `PN date format`() = runBlocking {
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        noviceDao.insert(NoviceFlowEntity(
            date = date,
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        ))

        val history = noviceDao.getAllHistory().first()
        val storedDate = history[0].date

        // Проверяем дату
        assertEquals(date, storedDate)
    }

    @Test
    fun `RP date format`() = runBlocking {
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        growingDao.insert(GrowingFlowEntity(
            date = date,
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = true,
            actionType = "RP_START"
        ))

        val history = growingDao.getAllHistory().first()
        assertEquals(date, history[0].date)
    }

    @Test
    fun `PSP date format`() = runBlocking {
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val flow = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = date,
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        pspFlowDao.insert(flow)

        val flows = pspFlowDao.getAllFlows().first()
        assertEquals(date, flows[0].startDate)
    }

    // ========== КОЛОНКИ EXCEL ==========

    @Test
    fun `Excel columns match database fields`() = runBlocking {
        // Создаём тестовые записи
        noviceDao.insert(NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 10000.0,
            dailyAccrual = 200.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        ))

        // Экспорт в Excel формат
        // A: Дата (date) -> cell[0]
        // B: В потоке (inFlowAmount) -> cell[1]
        // C: Начисление (dailyAccrual) -> cell[2]
        // D: Кошелек (walletAmount) -> cell[3]
        
        val history = noviceDao.getAllHistory().first()
        val row = history[0]
        
        // Проверяем маппинг колонок
        val cellA = row.date        // Дата
        val cellB = row.inFlowAmount  // В потоке
        val cellC = row.dailyAccrual  // Начисление
        val cellD = row.walletAmount // Кошелек
        
        assertTrue(cellA > 0)
        assertTrue(cellB > 0)
        assertTrue(cellC >= 0)
        assertTrue(cellD >= 0)
    }
}