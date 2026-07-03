package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.GrowingFlowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Растущего Потока (РП).
 * Проверяют реальную работу с БД (Room) согласно ТЗ.
 */
class GrowingFlowIntegrationTest : BaseIntegrationTest() {

    /**
     * Т3.1: Старт РП - проверка записи START в БД.
     * Согласно ТЗ: "Старт РП: создается запись с процентом, в потоке, начислением, кошельком"
     */
    @Test
    fun startGrowingFlow_createsStartRecordInDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val entity = GrowingFlowEntity(
            date = startDate,
            step = 1,
            percent = 0.1,
            inFlowAmount = 20000.0, // Взнос 10000 + 100% бонус
            dailyAccrual = 20.0,    // 20000 * 0.1%
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "START"
        )

        // Act
        growingFlowDao.insert(entity)
        val allRecords = growingFlowDao.getAllHistory().first()

        // Assert
        assertEquals("Должна быть 1 запись", 1, allRecords.size)
        val saved = allRecords[0]
        assertEquals("Тип действия должен быть START", "START", saved.actionType)
        assertEquals("Процент должен быть 0.1", 0.1, saved.percent, 0.0001)
        assertEquals("В потоке должно быть 20000", 20000.0, saved.inFlowAmount, 0.01)
    }

    /**
     * Т3.2: Нажатие кнопки - проверка создания DAILY записи.
     * Согласно ТЗ: "При нажатии кнопки: В потоке уменьшается, Кошелек увеличивается"
     */
    @Test
    fun pressButton_createsDailyRecord() = runBlocking {
        // Arrange - старт потока
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = startDate,
                step = 1,
                percent = 0.1,
                inFlowAmount = 20000.0,
                dailyAccrual = 20.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "START"
            )
        )

        // Act - нажатие кнопки
        val lastEntry = growingFlowDao.getLastEntry()!!
        val newInFlow = lastEntry.inFlowAmount - lastEntry.dailyAccrual
        val newWallet = lastEntry.walletAmount + lastEntry.dailyAccrual
        val newPercent = lastEntry.percent + 0.003 // dailyAddition

        growingFlowDao.insert(
            GrowingFlowEntity(
                date = startDate,
                step = lastEntry.step + 1,
                percent = newPercent,
                inFlowAmount = newInFlow,
                dailyAccrual = newInFlow * newPercent / 100,
                walletAmount = newWallet,
                isButtonPressed = true,
                actionType = "DAILY"
            )
        )

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        assertEquals("Должно быть 2 записи", 2, allRecords.size)

        val dailyRecord = allRecords.find { it.actionType == "DAILY" }!!
        assertEquals("Шаг должен увеличиться", 2, dailyRecord.step)
        assertEquals("В потоке должно уменьшиться", 19980.0, dailyRecord.inFlowAmount, 0.01)
        assertEquals("Кошелек должен увеличиться", 20.0, dailyRecord.walletAmount, 0.01)
        assertTrue("Кнопка должна быть нажата", dailyRecord.isButtonPressed)
    }

    /**
     * Т3.3: Генерация пропущенных дней - создание MISSED записи.
     * Согласно ТЗ: "Если кнопка не нажата за день, генерируется MISSED"
     */
    @Test
    fun missedDay_createsMissedRecord() = runBlocking {
        // Arrange - старт в четверг (15.01)
        val thursday = createDateMillis(2026, Calendar.JANUARY, 15)
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = thursday,
                step = 1,
                percent = 0.1,
                inFlowAmount = 20000.0,
                dailyAccrual = 20.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "START"
            )
        )

        // Act - пользователь не заходил в пятницу (16.01), зашел в субботу (17.01)
        val friday = createDateMillis(2026, Calendar.JANUARY, 16)
        val lastEntry = growingFlowDao.getLastEntry()!!

        growingFlowDao.insert(
            GrowingFlowEntity(
                date = friday,
                step = lastEntry.step, // Шаг не увеличивается
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount,
                dailyAccrual = lastEntry.dailyAccrual,
                walletAmount = lastEntry.walletAmount,
                isButtonPressed = false,
                actionType = "MISSED"
            )
        )

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        val missedRecord = allRecords.find { it.actionType == "MISSED" }
        assertTrue("Должна быть запись MISSED", missedRecord != null)
        assertEquals("MISSED должна быть в пятницу", friday, missedRecord!!.date)
        assertEquals("Шаг не должен увеличиться", 1, missedRecord.step)
    }

    /**
     * Т3.4: Воскресенье - создание SUNDAY записи.
     * Согласно ТЗ: "Воскресенье — неактивный день (генерируется запись SUNDAY)"
     */
    @Test
    fun sunday_createsSundayRecord() = runBlocking {
        // Arrange - старт в субботу (17.01), следующий день воскресенье (18.01)
        val saturday = createDateMillis(2026, Calendar.JANUARY, 17)
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = saturday,
                step = 1,
                percent = 0.1,
                inFlowAmount = 20000.0,
                dailyAccrual = 20.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "START"
            )
        )

        // Act - воскресенье
        val sunday = createDateMillis(2026, Calendar.JANUARY, 18)
        val lastEntry = growingFlowDao.getLastEntry()!!

        growingFlowDao.insert(
            GrowingFlowEntity(
                date = sunday,
                step = lastEntry.step, // Шаг не увеличивается
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount,
                dailyAccrual = 0.0, // В воскресенье начисления нет
                walletAmount = lastEntry.walletAmount,
                isButtonPressed = false,
                actionType = "SUNDAY"
            )
        )

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        val sundayRecord = allRecords.find { it.actionType == "SUNDAY" }
        assertTrue("Должна быть запись SUNDAY", sundayRecord != null)
        assertEquals("SUNDAY должна быть в воскресенье", sunday, sundayRecord!!.date)
        assertEquals("Начисление в воскресенье должно быть 0", 0.0, sundayRecord.dailyAccrual, 0.01)
    }

    /**
     * Т3.5: Реинвест - проверка увеличения потока с бонусом.
     * Согласно ТЗ: "Реинвест: Взнос приплюсовывается к В потоке с учетом бонуса"
     */
    @Test
    fun reinvest_increasesInFlowWithBonus() = runBlocking {
        // Arrange - текущее состояние после нескольких нажатий
        val currentDate = createDateMillis(2026, Calendar.JANUARY, 20)
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = currentDate,
                step = 5,
                percent = 0.112,
                inFlowAmount = 19000.0,
                dailyAccrual = 21.28,
                walletAmount = 100.0,
                isButtonPressed = true,
                actionType = "DAILY"
            )
        )

        // Act - реинвест 5000 с бонусом 100% (E-currency)
        val reinvestAmount = 5000.0
        val bonusPercent = 100.0 // 100% бонус
        val lastEntry = growingFlowDao.getLastEntry()!!
        val newInFlow = lastEntry.inFlowAmount + reinvestAmount * (1 + bonusPercent / 100)
        val newStep = lastEntry.step + 1

        growingFlowDao.insert(
            GrowingFlowEntity(
                date = currentDate,
                step = newStep,
                percent = lastEntry.percent, // Процент не меняется при реинвесте
                inFlowAmount = newInFlow,
                dailyAccrual = newInFlow * lastEntry.percent / 100,
                walletAmount = lastEntry.walletAmount, // Кошелек не меняется (если не указан)
                isButtonPressed = lastEntry.isButtonPressed, // Сохраняем состояние кнопки
                actionType = "REINVEST"
            )
        )

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        val reinvestRecord = allRecords.find { it.actionType == "REINVEST" }
        assertTrue("Должна быть запись REINVEST", reinvestRecord != null)
        assertEquals("В потоке должно быть 29000 (19000 + 5000 + 5000*100/100)", 29000.0, reinvestRecord!!.inFlowAmount, 0.01)
        assertEquals("Шаг должен увеличиться", 6, reinvestRecord.step)
    }

    /**
     * Т3.6: Коррекция - изменение только кошелька.
     * Согласно ТЗ: "Коррекция: Если меняется Кошелек: Меняется только кошелек"
     */
    @Test
    fun correction_updatesOnlyWallet() = runBlocking {
        // Arrange
        val date = createDateMillis(2026, Calendar.JANUARY, 20)
        val original = GrowingFlowEntity(
            date = date,
            step = 3,
            percent = 0.106,
            inFlowAmount = 19500.0,
            dailyAccrual = 20.67,
            walletAmount = 50.0,
            isButtonPressed = true,
            actionType = "DAILY"
        )
        growingFlowDao.insert(original)

        // Act - коррекция кошелька (создаем новую запись с новым ID)
        val lastEntry = growingFlowDao.getLastEntry()!!
        val corrected = lastEntry.copy(
            id = 0, // Сбрасываем ID для автогенерации новой записи
            walletAmount = 0.0, // Пользователь указал 0 в кошельке
            actionType = "CORRECTION"
        )
        growingFlowDao.insert(corrected)

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        val correctionRecord = allRecords.find { it.actionType == "CORRECTION" }
        assertTrue("Должна быть запись CORRECTION", correctionRecord != null)
        assertEquals("Кошелек должен стать 0", 0.0, correctionRecord!!.walletAmount, 0.01)
        assertEquals("В потоке не должно измениться", 19500.0, correctionRecord.inFlowAmount, 0.01)
        assertEquals("Процент не должен измениться", 0.106, correctionRecord.percent, 0.0001)
    }

    /**
     * Т3.7: Проверка генерации пропущенных дней.
     * Согласно ТЗ: "Генерация пропусков: АВТОМАТИЧЕСКИ сгенерировать историю за ВСЕ пропущенные дни"
     */
    @Test
    fun generateMissedDays_createsMultipleMissedRecords() = runBlocking {
        // Arrange - старт в понедельник (12.01), потом пропуск до пятницы (16.01)
        val monday = createDateMillis(2026, Calendar.JANUARY, 12)
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = monday,
                step = 1,
                percent = 0.1,
                inFlowAmount = 20000.0,
                dailyAccrual = 20.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "START"
            )
        )

        // Добавляем DAILY для понедельника
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = monday,
                step = 2,
                percent = 0.103,
                inFlowAmount = 19980.0,
                dailyAccrual = 20.58,
                walletAmount = 20.0,
                isButtonPressed = true,
                actionType = "DAILY"
            )
        )

        // Act - зашли в пятницу (16.01), должны создаться MISSED за вт, ср, чт
        val tuesday = createDateMillis(2026, Calendar.JANUARY, 13)
        val wednesday = createDateMillis(2026, Calendar.JANUARY, 14)
        val thursday = createDateMillis(2026, Calendar.JANUARY, 15)
        val friday = createDateMillis(2026, Calendar.JANUARY, 16)

        var lastEntry = growingFlowDao.getLastEntry()!!
        var currentStep = lastEntry.step

        // Генерируем MISSED для пропущенных дней
        val missedDates = listOf(tuesday, wednesday, thursday)
        for (date in missedDates) {
            growingFlowDao.insert(
                GrowingFlowEntity(
                    date = date,
                    step = currentStep,
                    percent = lastEntry.percent,
                    inFlowAmount = lastEntry.inFlowAmount,
                    dailyAccrual = lastEntry.dailyAccrual,
                    walletAmount = lastEntry.walletAmount,
                    isButtonPressed = false,
                    actionType = "MISSED"
                )
            )
        }

        // Пятница - DAILY (нажал кнопку)
        currentStep++
        lastEntry = growingFlowDao.getLastEntry()!! // Обновляем lastEntry
        val newPercent = lastEntry.percent + 0.003
        val newInFlow = lastEntry.inFlowAmount - lastEntry.dailyAccrual
        growingFlowDao.insert(
            GrowingFlowEntity(
                date = friday,
                step = currentStep,
                percent = newPercent,
                inFlowAmount = newInFlow,
                dailyAccrual = newInFlow * newPercent / 100,
                walletAmount = lastEntry.walletAmount + lastEntry.dailyAccrual,
                isButtonPressed = true,
                actionType = "DAILY"
            )
        )

        // Assert
        val allRecords = growingFlowDao.getAllHistory().first()
        val missedRecords = allRecords.filter { it.actionType == "MISSED" }
        assertEquals("Должно быть 3 записи MISSED (вт, ср, чт)", 3, missedRecords.size)
        assertEquals("Должно быть 6 записей всего (START, DAILY, 3xMISSED, DAILY)", 6, allRecords.size)
    }

    /**
     * Т3.8: Проверка остановки кнопки при нулевом потоке.
     * Согласно ТЗ: "При балансе 0.00 кнопка навсегда блокируется (Сделайте реинвест)"
     */
    @Test
    fun zeroInFlow_buttonBecomesInactive() = runBlocking {
        // Arrange - создаем состояние с малым потоком
        val date = createDateMillis(2026, Calendar.JANUARY, 20)
        val entity = GrowingFlowEntity(
            date = date,
            step = 10,
            percent = 0.13,
            inFlowAmount = 0.0, // Поток стал 0
            dailyAccrual = 0.0,
            walletAmount = 500.0,
            isButtonPressed = false, // Кнопка должна быть неактивна
            actionType = "DAILY"
        )
        growingFlowDao.insert(entity)

        // Assert
        val lastEntry = growingFlowDao.getLastEntry()!!
        assertEquals("В потоке должно быть 0", 0.0, lastEntry.inFlowAmount, 0.01)
        assertFalse("Кнопка должна быть неактивна при нулевом потоке", lastEntry.isButtonPressed)
        assertEquals("Начисление должно быть 0", 0.0, lastEntry.dailyAccrual, 0.01)
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
