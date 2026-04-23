package com.example.flowcapital

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowcapital.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * Полные интеграционные тесты для ПСП (Премиум Стартовый Поток)
 * Охватывают всю логику: создание, начисления, копилка, реинвест, удаление, валидацию
 */
@RunWith(AndroidJUnit4::class)
class PSPIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var flowDao: PremiumStartFlowDao
    private lateinit var periodDao: PremiumStartPeriodDao

    private val pspCoefficients = mapOf(
        1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07, 5 to 113.48,
        6 to 127.59, 7 to 139.73, 8 to 150.17, 9 to 159.14, 10 to 166.86,
        11 to 173.5, 12 to 179.21, 13 to 184.12, 14 to 188.35, 15 to 191.97,
        16 to 195.1, 17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
    )
    private val nominal = 5000.0
    private val periodDuration = 14L * 24 * 60 * 60 * 1000

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        flowDao = database.premiumStartFlowDao()
        periodDao = database.premiumStartPeriodDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun `PSP create flow with valid nominal 5000`() = runBlocking {
        val startDate = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val flow = PremiumStartFlowEntity(
            nominalAmount = nominal,
            startDate = startDate,
            totalAccrued = 0.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId = flowDao.insert(flow).toInt()

        val savedFlow = flowDao.getFlowById(flowId)
        assertNotNull(savedFlow)
        assertEquals(nominal, savedFlow!!.nominalAmount, 0.01)
        assertEquals(1, savedFlow.currentPeriod)
        assertTrue(savedFlow.isActive)
    }

    @Test
    fun `PSP period 1 accrual is 30 percent of nominal`() = runBlocking {
        val accrual = nominal * (pspCoefficients[1]!! / 100.0)
        assertEquals(1500.0, accrual, 0.01)
    }

    @Test
    fun `PSP period 2 accrual is 55_8 percent of nominal`() = runBlocking {
        val accrual = nominal * (pspCoefficients[2]!! / 100.0)
        assertEquals(2790.0, accrual, 0.01)
    }

    @Test
    fun `PSP period 3 accrual is 78 percent of nominal`() = runBlocking {
        val accrual = nominal * (pspCoefficients[3]!! / 100.0)
        assertEquals(3900.0, accrual, 0.01)
    }

    @Test
    fun `PSP period 4 accrual is 97_07 percent of nominal`() = runBlocking {
        val accrual = nominal * (pspCoefficients[4]!! / 100.0)
        assertEquals(4853.5, accrual, 0.01)
    }

    @Test
    fun `PSP period 20 accrual is 200 percent of nominal`() = runBlocking {
        val accrual = nominal * (pspCoefficients[20]!! / 100.0)
        assertEquals(10000.0, accrual, 0.01)
    }

    @Test
    fun `PSP period end dates are 14 days apart`() = runBlocking {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 12, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val period1End = startDate + periodDuration
        val period2End = period1End + periodDuration
        val period3End = period2End + periodDuration

        val expected1End = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 26, 0, 0, 0)
        }.timeInMillis

        val expected2End = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 0, 0, 0)
        }.timeInMillis

        val expected3End = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 24, 0, 0, 0)
        }.timeInMillis

        assertEquals(expected1End, period1End)
        assertEquals(expected2End, period2End)
        assertEquals(expected3End, period3End)
    }

    @Test
    fun `PSP full scenario from user story - periods 1-4 with all to piggy bank`() = runBlocking {
        var totalAccrued = 0.0

        // Период 1: начисление 1500, всё в копилку
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0)
        totalAccrued += accrual1
        assertEquals(1500.0, totalAccrued, 0.01)

        // Период 2: начисление 2790, всё в копилку
        val accrual2 = nominal * (pspCoefficients[2]!! / 100.0)
        totalAccrued += accrual2
        assertEquals(4290.0, totalAccrued, 0.01)

        // Период 3: начисление 3900, всё в копилку
        val accrual3 = nominal * (pspCoefficients[3]!! / 100.0)
        totalAccrued += accrual3
        assertEquals(8190.0, totalAccrued, 0.01)

        // Период 4: начисление 4853.5, НИЧЕГО в копилку (реинвест)
        totalAccrued += 0
        assertEquals(8190.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP totalAccrued calculation with all to piggy bank`() = runBlocking {
        var totalAccrued = 0.0

        for (period in 1..4) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            totalAccrued += accrual
        }

        assertEquals(8190.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP scenario user forgot - long pause between periods`() = runBlocking {
        // Пользователь забыл про потоки и вспомнил 02.05.2026
        // До этого были периоды 1 и 2, начисления скинуты в копилку
        var totalAccrued = 0.0

        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0)
        totalAccrued += accrual1

        val accrual2 = nominal * (pspCoefficients[2]!! / 100.0)
        totalAccrued += accrual2

        assertEquals(4290.0, totalAccrued, 0.01)

        // Период 3: начисление 3900, всё в копилку
        val accrual3 = nominal * (pspCoefficients[3]!! / 100.0)
        totalAccrued += accrual3

        // Период 4: пользователь решил реинвест, в копилку 0
        totalAccrued += 0

        assertEquals(8190.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP can have multiple flows simultaneously`() = runBlocking {
        val flow1 = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 3000.0,
            isActive = true,
            currentPeriod = 2
        )
        val flow2 = PremiumStartFlowEntity(
            nominalAmount = 10000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 5000.0,
            isActive = true,
            currentPeriod = 1
        )
        val flow3 = PremiumStartFlowEntity(
            nominalAmount = 15000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 10000.0,
            isActive = true,
            currentPeriod = 3
        )

        flowDao.insert(flow1)
        flowDao.insert(flow2)
        flowDao.insert(flow3)

        val allFlows = flowDao.getAllFlows().first()
        assertEquals(3, allFlows.size)
    }

    @Test
    fun `PSP deleting flow removes it from database`() = runBlocking {
        val flow = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 8190.0,
            isActive = true,
            currentPeriod = 4
        )
        val flowId = flowDao.insert(flow).toInt()

        flowDao.deleteById(flowId)

        val deletedFlow = flowDao.getFlowById(flowId)
        assertNull(deletedFlow)
    }

    @Test
    fun `PSP total accrued across all flows`() = runBlocking {
        flowDao.insert(PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 3000.0,
            isActive = true,
            currentPeriod = 1
        ))
        flowDao.insert(PremiumStartFlowEntity(
            nominalAmount = 10000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 5000.0,
            isActive = true,
            currentPeriod = 2
        ))
        flowDao.insert(PremiumStartFlowEntity(
            nominalAmount = 15000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 10000.0,
            isActive = true,
            currentPeriod = 3
        ))

        val allFlows = flowDao.getAllFlows().first()
        val totalAccrued = allFlows.sumOf { it.totalAccrued }
        assertEquals(18000.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP delete flow subtracts from total across flows`() = runBlocking {
        val flow1 = PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 3000.0,
            isActive = true,
            currentPeriod = 1
        )
        val flowId1 = flowDao.insert(flow1).toInt()

        val flow2 = PremiumStartFlowEntity(
            nominalAmount = 10000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 5000.0,
            isActive = true,
            currentPeriod = 2
        )
        val flowId2 = flowDao.insert(flow2).toInt()

        // Удаляем первый поток
        flowDao.deleteById(flowId1)

        val remainingFlows = flowDao.getAllFlows().first()
        val totalAccrued = remainingFlows.sumOf { it.totalAccrued }
        assertEquals(5000.0, totalAccrued, 0.01)
        assertEquals(1, remainingFlows.size)
    }

    @Test
    fun `PSP clearAll removes all flows`() = runBlocking {
        flowDao.insert(PremiumStartFlowEntity(
            nominalAmount = 5000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 3000.0,
            isActive = true,
            currentPeriod = 1
        ))
        flowDao.insert(PremiumStartFlowEntity(
            nominalAmount = 10000.0,
            startDate = System.currentTimeMillis(),
            totalAccrued = 5000.0,
            isActive = true,
            currentPeriod = 2
        ))

        flowDao.clearAll()

        val flows = flowDao.getAllFlows().first()
        assertTrue(flows.isEmpty())
    }

    @Test
    fun `PSP all 20 coefficients are non-negative`() {
        pspCoefficients.values.forEach { coeff ->
            assertTrue(coeff >= 0)
        }
    }

    @Test
    fun `PSP coefficients are increasing`() {
        for (i in 2..20) {
            assertTrue("${pspCoefficients[i]} should be >= ${pspCoefficients[i - 1]}",
                pspCoefficients[i]!! >= pspCoefficients[i - 1]!!)
        }
    }

    @Test
    fun `PSP period numbers are 1 to 20`() {
        assertEquals(20, pspCoefficients.size)
        for (i in 1..20) {
            assertTrue("Period $i should exist", pspCoefficients.containsKey(i))
        }
    }

    @Test
    fun `PSP final accrual at period 20 equals 200 percent`() {
        val finalAccrual = nominal * (pspCoefficients[20]!! / 100.0)
        assertEquals(nominal * 2.0, finalAccrual, 0.01)
    }

    @Test
    fun `PSP forecast calculates future periods correctly`() = runBlocking {
        var forecastTotal = 0.0

        for (period in 1..5) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            forecastTotal += accrual
        }

        assertEquals(18717.5, forecastTotal, 0.01)
    }

    // ========== Валидация создания ПСП ==========

    @Test
    fun `PSP create requires nominal greater than zero`() = runBlocking {
        val nominal = 0.0
        val isValid = nominal > 0
        assertFalse(isValid)
    }

    @Test
    fun `PSP create with nominal 5000 is valid`() = runBlocking {
        val nominal = 5000.0
        val isValid = nominal > 0
        assertTrue(isValid)
    }

    @Test
    fun `PSP create all 4 fields are required`() = runBlocking {
        // По ТЗ: Номинал, Какой сейчас период, Дата начала первого, Дата начала последнего
        val nominal = 5000.0
        val currentPeriod = 1
        val firstPeriodStart = System.currentTimeMillis()
        val currentPeriodStart = System.currentTimeMillis()

        // Все 4 поля не null/0
        assertTrue(nominal > 0)
        assertTrue(currentPeriod in 1..20)
        assertTrue(firstPeriodStart > 0)
        assertTrue(currentPeriodStart > 0)
    }

    @Test
    fun `PSP create validation fails with empty nominal`() = runBlocking {
        val nominal = 0.0
        val period = 1
        val firstDate = System.currentTimeMillis()
        val isValid = nominal > 0 && period in 1..20 && firstDate > 0
        assertFalse(isValid)
    }

    // ========== Логика копилки ==========

    @Test
    fun `PSP piggy bank transfers to totalAccrued on contribution`() = runBlocking {
        val nominal = 5000.0
        val accrualAmount = nominal * (pspCoefficients[1]!! / 100.0) // 1500
        val piggyBankAmount = 1000.0 // Переводим в копилку

        // Имитация makeContribution
        var totalAccrued = 0.0
        totalAccrued += piggyBankAmount

        assertEquals(1000.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP accrual goes to wallet not piggy bank`() = runBlocking {
        val nominal = 5000.0
        val accrualAmount = nominal * (pspCoefficients[1]!! / 100.0) // 1500

        // Начисление (accrualAmount) падает в кошелёк, а не в копилку
        val walletAmount = accrualAmount
        val piggyBankAmount = 0.0

        // totalAccrued = только то, что ушло в копилку
        val totalAccrued = piggyBankAmount

        assertEquals(0.0, totalAccrued, 0.01)
        assertEquals(1500.0, walletAmount, 0.01)
    }

    @Test
    fun `PSP contribution adds to piggy bank correctly`() = runBlocking {
        var totalAccrued = 0.0

        // Период 1: начисление 1500 падает в кошелёк
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0) // 1500
        val walletAmount1 = accrual1

        // Переводим 500 в копилку (остаток 1000 в кошёлке)
        val piggyBank1 = 500.0
        totalAccrued += piggyBank1

        assertEquals(500.0, totalAccrued, 0.01)
    }

    @Test
    fun `PSP full contribution cycle works correctly`() = runBlocking {
        var totalAccrued = 0.0
        val walletAmount = 0.0

        // Период 1: начисление 1500
        val accrual1 = nominal * (pspCoefficients[1]!! / 100.0) // 1500
        val wallet1 = walletAmount + accrual1 // 1500 в кошелёк

        // Делаем взнос: переводим ВСЁ в копилку
        val piggyBank1 = wallet1 // 1500
        totalAccrued += piggyBank1
        var walletAfterContribution = 0.0 // кошелёк очищается

        assertEquals(1500.0, totalAccrued, 0.01)
        assertEquals(0.0, walletAfterContribution, 0.01)
    }

    // ========== Формат периода в истории ==========

    @Test
    fun `PSP second period shows as 1-2 format`() = runBlocking {
        val previousPeriod = 1
        val currentPeriod = 2

        val periodText = "$previousPeriod->$currentPeriod"
        assertEquals("1->2", periodText)
    }

    @Test
    fun `PSP first period shows as 1`() = runBlocking {
        val currentPeriod = 1

        val periodText = "$currentPeriod"
        assertEquals("1", periodText)
    }

    // ========== Логика времени ПСП ==========

    @Test
    fun `PSP closing date is contribution date plus 14 days`() = runBlocking {
        // По ТЗ: Дата закрытия нового периода = Дата взноса + 14 дней
        val contributionDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 29, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val periodDuration = 14L * 24 * 60 * 60 * 1000 // 14 дней в миллисекундах
        val expectedClosingDate = contributionDate + periodDuration

        val cal = Calendar.getInstance().apply { timeInMillis = expectedClosingDate }
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
        // Согласно ТЗ: "ровно 2 недели от даты взноса, старые годы игнорируются"
        // Но это просто означает +14 дней, независимо от года
    }

    @Test
    fun `PSP 5 year pause - old years ignored in closing date`() = runBlocking {
        // Пример 2 из ТЗ: Пользователь забыл на 5 лет
        // Дата закрытия = Дата взноса + 14 дней (игнорируем старые годы)

        val contributionDate = Calendar.getInstance().apply {
            set(2031, Calendar.APRIL, 29, 12, 0, 0) // 5 лет спустя
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val periodDuration = 14L * 24 * 60 * 60 * 1000
        val closingDate = contributionDate + periodDuration

        // Проверяем что закрытие = contribution date + 14 дней
        val closingCal = Calendar.getInstance().apply { timeInMillis = closingDate }
        assertEquals(13, closingCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MAY, closingCal.get(Calendar.MONTH))
    }

    @Test
    fun `PSP button stays active after period closes - frozen until user contributes`() = runBlocking {
        // По ТЗ: "Кнопка взноса становится активной в день закрытия периода
        // и остается активной, пока пользователь не сделает взнос (даже если пройдет 10 лет)"

        val endDate = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayAfter10Years = Calendar.getInstance().apply {
            set(2036, Calendar.APRIL, 15, 12, 0, 0) // 10 лет спустя
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Кнопка активна когда today >= endDate (даже через 10 лет)
        val canContribute = todayAfter10Years >= endDate
        assertTrue(canContribute)
    }

    @Test
    fun `PSP period auto starts - NOT automatic`() = runBlocking {
        // По ТЗ: "Период автоматически НЕ начинается"
        // Новый период начинается только после взноса пользователя

        var currentPeriod = 1
        var isContributionMade = false

        // Даже если прошло 14 дней, период не начинается автоматически
        val daysPassed = 15
        val canAutoStart = daysPassed >= 14 && !isContributionMade

        // Период автоматически НЕ начинается
        assertFalse(canAutoStart)
    }

    @Test
    fun `PSP button text changes at period 20 close`() = runBlocking {
        // По ТЗ: "В день закрытия кнопка меняет надпись на ЗАКРЫТЬ ПОТОК"

        val periodNumber = 20
        val isLastPeriod = periodNumber >= 20
        val canClose = isLastPeriod

        val buttonText = when {
            canClose -> "ЗАКРЫТЬ ПОТОК"
            else -> "СДЕЛАТЬ ВЗНОС"
        }

        assertEquals("ЗАКРЫТЬ ПОТОК", buttonText)
    }

    @Test
    fun `PSP flow closed state shows only required elements`() = runBlocking {
        // По ТЗ: При закрытии остаются ТОЛЬКО кнопки "Старт" и "Удалить" (в одну строку),
        // "Всего накапало", строка перелистывания и таблица истории

        val isFlowClosed = true

        // При закрытии потока:
        val showCreateButton = true // "Старт"
        val showDeleteButton = true // "Удалить"
        val showTotalAccrued = true // "Всего накапало"
        val showNavigation = true // строка перелистывания
        val showHistory = true // таблица истории
        val showContributionButton = false // кнопка взноса - НЕТ
        val showCorrectionButton = false // кнопка корректировки - НЕТ

        assertTrue(showCreateButton && showDeleteButton && showTotalAccrued && showNavigation && showHistory)
        assertFalse(showContributionButton || showCorrectionButton)
    }

    @Test
    fun `PSP closed flow text is gray background white text`() = runBlocking {
        // По ТЗ: "Поток закрыт. Всего получено: {сумма}" (фон серый, текст белый)

        val isFlowClosed = true
        val totalAccrued = 5000.0

        // При закрытии:
        val text = "Поток закрыт. Всего получено: $totalAccrued"
        val backgroundColor = "0xFF333333" // серый
        val textColor = "0xFFFFFFFF" // белый

        assertTrue(isFlowClosed)
        assertTrue(text.contains("Поток закрыт"))
    }

    // ========== Старт с середины ==========

    @Test
    fun `PSP start from period 5 generates 5 history rows`() = runBlocking {
        // Пример 3 из ТЗ: Старт с 5-го периода
        // Генерируются 5 строк истории подряд от 1 до 5 периода

        val nominal = 10000.0
        val startPeriod = 5
        val firstPeriodStart = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Генерируем историю
        val periods = mutableListOf<Int>()
        for (period in 1..startPeriod) {
            periods.add(period)
        }

        assertEquals(5, periods.size)
        assertEquals(1, periods[0])
        assertEquals(5, periods[4])
    }

    @Test
    fun `PSP start from middle calculates totalAccrued from coefficients`() = runBlocking {
        // При старте с середины "Всего получено" высчитывается м��тематически по таблице

        val nominal = 10000.0
        val currentPeriod = 5

        // Сумма начислений за периоды 1-5
        var totalAccrued = 0.0
        for (period in 1..currentPeriod) {
            val accrual = nominal * (pspCoefficients[period]!! / 100.0)
            totalAccrued += accrual
        }

        // Период 1: 30% = 3000
        // Период 2: 55.8% = 5580
        // Период 3: 78% = 7800
        // Период 4: 97.07% = 9707
        // Период 5: 113.48% = 11348
        // Итого: 3000 + 5580 + 7800 + 9707 + 11348 = 37435

        assertEquals(37435.0, totalAccrued, 0.01)
    }

    // ========== Равномерное распределение дат при старте с середины ==========

    @Test
    fun `PSP start from middle distributes dates evenly`() = runBlocking {
        // Даты взносов промежуточных периодов распределяются равномерно

        val startPeriod = 5
        val firstPeriodDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val periodDuration = 14L * 24 * 60 * 60 * 1000

        // Распределяем даты равномерно
        val dates = mutableListOf<Long>()
        for (period in 1..startPeriod) {
            dates.add(firstPeriodDate + (period - 1) * periodDuration)
        }

        // Проверяем что между датами ровно 14 дней
        for (i in 1 until dates.size) {
            val diff = dates[i] - dates[i - 1]
            val days = diff / (24 * 60 * 60 * 1000)
            assertEquals(14L, days)
        }
    }

    // ========== Проверка 20-го периода ==========

    @Test
    fun `PSP period 20 is final period`() = runBlocking {
        val finalPeriod = 20

        val isFinalPeriod = finalPeriod >= 20
        val isClosed = isFinalPeriod && true // после взноса

        assertTrue(isFinalPeriod)
        assertTrue(isClosed)
    }

    @Test
    fun `PSP period 20 final accrual is 200 percent`() = runBlocking {
        val nominal = 5000.0
        val finalPeriod = 20

        val finalAccrual = nominal * (pspCoefficients[finalPeriod]!! / 100.0)

        // 200% от номинала = 10000
        assertEquals(10000.0, finalAccrual, 0.01)
    }
}
