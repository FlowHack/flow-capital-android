package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.PremiumStartFlowEntity
import com.flowhack.flowcapital.data.db.PremiumStartPeriodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Премиум Стартового Потока (ПСП).
 * Проверяют реальную работу с БД (Room) согласно ТЗ.
 */
class PremiumStartIntegrationTest : BaseIntegrationTest() {

    /**
     * Т3.17: Создание ПСП - проверка записи потока в БД.
     * Согласно ТЗ: "Старт ПСП: Номинал, Дата начала, Период (1/20)"
     */
    @Test
    fun createPspFlow_createsFlowInDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)

        // Act
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )

        // Assert
        val flows = premiumStartFlowDao.getAllFlows().first()
        assertEquals("Должен быть 1 поток", 1, flows.size)

        val flow = premiumStartFlowDao.getFlowById(flowId.toInt())!!
        assertEquals("Номинал должен быть 5000", 5000.0, flow.nominalAmount, 0.01)
        assertEquals("Текущий период должен быть 1", 1, flow.currentPeriod)
        assertEquals("Всего получено должно быть 0", 0.0, flow.totalAccrued, 0.01)
    }

    /**
     * Т3.18: Создание периодов ПСП - проверка генерации истории.
     * Согласно ТЗ: "Жизненный цикл: 20 периодов по 14 дней"
     */
    @Test
    fun createPspPeriods_generatesCorrectPeriods() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        // Act - создаем первый период (как при взносе)
        val periodEndDate = startDate + (14 * 24 * 60 * 60 * 1000) // +14 дней
        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = periodEndDate,
                accrualAmount = 1500.0, // 30% от номинала
                isCompleted = false
            )
        )

        // Assert
        val periods = premiumStartPeriodDao.getPeriodsByFlowId(flowId).first()
        assertEquals("Должен быть 1 период", 1, periods.size)

        val period = periods[0]
        assertEquals("Номер периода должен быть 1", 1, period.periodNumber)
        assertEquals("Процент должен быть 30%", 30.0, period.percent, 0.01)
        assertEquals("Начисление должно быть 1500", 1500.0, period.accrualAmount, 0.01)
        assertFalse("Период не должен быть завершен", period.isCompleted)
    }

    /**
     * Т3.19: Взнос номинала - переход к следующему периоду.
     * Согласно ТЗ: "После взноса дата закрытия нового периода = Дата взноса + 14 дней"
     */
    @Test
    fun makeDeposit_movesToNextPeriod() = runBlocking {
        // Arrange - создаем поток и первый период
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        val firstPeriodEnd = startDate + (14 * 24 * 60 * 60 * 1000)
        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 30.0,
                startDate = startDate,
                endDate = firstPeriodEnd,
                accrualAmount = 1500.0,
                isCompleted = false
            )
        )

        // Act - делаем взнос во второй период (15.04)
        val depositDate2 = createDateMillis(2026, Calendar.APRIL, 15)
        val newClosingDate = depositDate2 + (14 * 24 * 60 * 60 * 1000) // 29.04

        // Завершаем первый период
        val firstPeriod = premiumStartPeriodDao.getCurrentPeriod(flowId)!!
        premiumStartPeriodDao.update(firstPeriod.copy(isCompleted = true))

        // Создаем второй период
        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 2,
                percent = 55.8,
                startDate = depositDate2,
                endDate = newClosingDate,
                accrualAmount = 2790.0, // 55.8% от 5000
                isCompleted = false
            )
        )

        // Обновляем поток
        val flow = premiumStartFlowDao.getFlowById(flowId)!!
        premiumStartFlowDao.update(
            flow.copy(
                currentPeriod = 2,
                totalAccrued = flow.totalAccrued + 1500.0 // Добавляем начисление первого периода
            )
        )

        // Assert
        val updatedFlow = premiumStartFlowDao.getFlowById(flowId)!!
        assertEquals("Текущий период должен быть 2", 2, updatedFlow.currentPeriod)
        assertEquals("Всего получено должно быть 1500", 1500.0, updatedFlow.totalAccrued, 0.01)

        val periods = premiumStartPeriodDao.getPeriodsByFlowId(flowId).first()
        assertEquals("Должно быть 2 периода", 2, periods.size)

        val currentPeriod = premiumStartPeriodDao.getCurrentPeriod(flowId)!!
        assertEquals("Текущий период должен быть 2", 2, currentPeriod.periodNumber)
        assertEquals("Дата закрытия нового периода = взнос + 14 дней", newClosingDate, currentPeriod.endDate)
    }

    /**
     * Т3.22: Удаление потока ПСП.
     * Согласно ТЗ: "Удалить (удаляет текущий поток из БД и вычитает его из Всего накапало)"
     */
    @Test
    fun deletePspFlow_removesFromDb() = runBlocking {
        // Arrange - создаем два потока
        val flowId1 = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = createDateMillis(2026, Calendar.APRIL, 1),
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )

        val flowId2 = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = createDateMillis(2026, Calendar.APRIL, 5),
                nominalAmount = 10000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        )

        // Act - удаляем первый поток
        premiumStartPeriodDao.deleteByFlowId(flowId1.toInt())
        premiumStartFlowDao.deleteById(flowId1.toInt())

        // Assert
        val flows = premiumStartFlowDao.getAllFlows().first()
        assertEquals("Должен остаться 1 поток", 1, flows.size)
        assertEquals("Должен остаться второй поток", flowId2.toInt(), flows[0].id)
    }

    /**
     * Т3.23: Проверка даты закрытия после взноса.
     * Согласно ТЗ: "После взноса дата закрытия нового периода = Дата взноса + 14 дней"
     */
    @Test
    fun depositDate_closingDateIs14DaysLater() = runBlocking {
        // Arrange
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = createDateMillis(2026, Calendar.APRIL, 1),
                nominalAmount = 5000.0,
                currentPeriod = 1,
                totalAccrued = 0.0
            )
        ).toInt()

        // Act - взнос 15.04 (после 14 дней)
        val depositDate = createDateMillis(2026, Calendar.APRIL, 15)
        val expectedClosingDate = depositDate + (14 * 24 * 60 * 60 * 1000) // 29.04

        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 1,
                percent = 55.8,
                startDate = depositDate,
                endDate = expectedClosingDate,
                accrualAmount = 2790.0,
                isCompleted = false
            )
        )

        // Assert
        val period = premiumStartPeriodDao.getCurrentPeriod(flowId)!!
        assertEquals("Дата закрытия должна быть 29.04.2026",
            expectedClosingDate, period.endDate)

        // Проверяем что разница 14 дней
        val cal1 = Calendar.getInstance().apply { timeInMillis = depositDate }
        val cal2 = Calendar.getInstance().apply { timeInMillis = period.endDate }
        val daysDiff = ((cal2.timeInMillis - cal1.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
        assertEquals("Разница должна быть 14 дней", 14, daysDiff)
    }

    /**
     * Т3.20: Старт с середины (текущий период не 1-й).
     * Согласно ТЗ: "Если период не 1-й: Номинал, Текущий период, Дата начала 1-го, Дата начала последнего"
     */
    @Test
    fun startFromMiddle_generatesHistoryCorrectly() = runBlocking {
        // Arrange - старт с 5-го периода
        val firstStartDate = createDateMillis(2026, Calendar.JANUARY, 1)
        val lastStartDate = createDateMillis(2026, Calendar.JUNE, 1) // 5-й период начался 01.06
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = firstStartDate,
                nominalAmount = 10000.0,
                currentPeriod = 5,
                totalAccrued = 0.0 // Будет рассчитано
            )
        ).toInt()

        // Act - генерируем историю периодов 1-5
        val periods = mutableListOf<PremiumStartPeriodEntity>()
        var totalReceived = 0.0

        for (periodNum in 1..5) {
            val depositDate = if (periodNum == 1) firstStartDate else {
                // Равномерное распределение дат между первым и последним
                val daysBetween = (lastStartDate - firstStartDate) / (24 * 60 * 60 * 1000)
                firstStartDate + ((daysBetween / 4) * (periodNum - 1)) * 24 * 60 * 60 * 1000
            }

            // Получаем процент для периода (из таблицы)
            val percent = getPspPercentage(periodNum)
            val accrual = 10000.0 * percent / 100
            totalReceived += accrual

            val closingDate = depositDate + (14 * 24 * 60 * 60 * 1000)

            periods.add(
                PremiumStartPeriodEntity(
                    flowId = flowId,
                    periodNumber = periodNum,
                    percent = percent,
                    startDate = depositDate,
                    endDate = closingDate,
                    accrualAmount = accrual,
                    isCompleted = periodNum < 5 // 5-й период еще не завершен
                )
            )
        }

        premiumStartPeriodDao.insertAll(periods)

        // Обновляем поток
        premiumStartFlowDao.update(
            premiumStartFlowDao.getFlowById(flowId)!!.copy(
                totalAccrued = totalReceived - periods[4].accrualAmount // Без начисления текущего периода
            )
        )

        // Assert
        val dbPeriods = premiumStartPeriodDao.getPeriodsByFlowId(flowId).first()
        assertEquals("Должно быть 5 периодов", 5, dbPeriods.size)

        val flow = premiumStartFlowDao.getFlowById(flowId)!!
        assertEquals("Текущий период должен быть 5", 5, flow.currentPeriod)
        assertTrue("Всего получено должно быть > 0", flow.totalAccrued > 0)
    }

    /**
     * Т3.21: Закрытие 20-го периода - завершение потока.
     * Согласно ТЗ: "В день закрытия (20-й период): кнопка меняет надпись на ЗАКРЫТЬ ПОТОК"
     */
    @Test
    fun close20thPeriod_flowBecomesInactive() = runBlocking {
        // Arrange - создаем поток с 20-м периодом
        val startDate = createDateMillis(2026, Calendar.APRIL, 1)
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 20,
                totalAccrued = 15000.0 // Накоплено за предыдущие периоды
            )
        ).toInt()

        // Создаем 20-й период
        val period20End = startDate + (14 * 24 * 60 * 60 * 1000)
        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 20,
                percent = 78.0,
                startDate = startDate,
                endDate = period20End,
                accrualAmount = 3900.0, // 78% для 20-го периода
                isCompleted = false
            )
        )

        // Act - закрытие потока (как при нажатии ЗАКРЫТЬ ПОТОК)
        val flow = premiumStartFlowDao.getFlowById(flowId)!!
        val period20 = premiumStartPeriodDao.getCurrentPeriod(flowId)!!

        premiumStartPeriodDao.update(period20.copy(isCompleted = true))
        premiumStartFlowDao.update(
            flow.copy(
                totalAccrued = flow.totalAccrued + period20.accrualAmount
            )
        )

        // Assert
        val updatedFlow = premiumStartFlowDao.getFlowById(flowId)!!
        assertEquals("Всего получено должно включать последнее начисление",
            15000.0 + 3900.0, updatedFlow.totalAccrued, 0.01)

        val periods = premiumStartPeriodDao.getPeriodsByFlowId(flowId).first()
        val lastPeriod = periods.find { it.periodNumber == 20 }!!
        assertTrue("20-й период должен быть завершен", lastPeriod.isCompleted)
    }

    /**
     * Т3.24: Проверка граничного случая - взнос через 5 лет.
     * Согласно ТЗ: "Пользователь забыл про приложение на 5 лет... Дата закрытия = ровно 2 недели от даты взноса"
     */
    @Test
    fun longPause_depositStillSets14DaysClosing() = runBlocking {
        // Arrange - старт 01.01.2026
        val startDate = createDateMillis(2026, Calendar.JANUARY, 1)
        val flowId = premiumStartFlowDao.insert(
            PremiumStartFlowEntity(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPeriod = 2, // Второй период был закрыт 29.01
                totalAccrued = 1500.0
            )
        ).toInt()

        // Act - взнос 01.01.2031 (через 5 лет)
        val depositDate2031 = createDateMillis(2031, Calendar.JANUARY, 1)
        val expectedClosingDate = depositDate2031 + (14 * 24 * 60 * 60 * 1000) // 15.01.2031

        premiumStartPeriodDao.insert(
            PremiumStartPeriodEntity(
                flowId = flowId,
                periodNumber = 3,
                percent = 78.0,
                startDate = depositDate2031,
                endDate = expectedClosingDate,
                accrualAmount = 3900.0,
                isCompleted = false
            )
        )

        // Assert
        val period = premiumStartPeriodDao.getPeriodByNumber(flowId, 3)!!
        assertEquals("Дата закрытия должна быть 15.01.2031", expectedClosingDate, period.endDate)

        val cal = Calendar.getInstance().apply { timeInMillis = period.endDate }
        assertEquals("Год должен быть 2031", 2031, cal.get(Calendar.YEAR))
        assertEquals("Месяц должен быть январь", Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals("День должен быть 15", 15, cal.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * Вспомогательная функция для получения процента ПСП по номеру периода.
     */
    private fun getPspPercentage(period: Int): Double {
        val percentages = mapOf(
            1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
            5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
            9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
            13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
            17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
        )
        return percentages[period] ?: 200.0
    }

    /**
     * Вспомогательная функция для создания timestamp.
     */
    private fun createDateMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
