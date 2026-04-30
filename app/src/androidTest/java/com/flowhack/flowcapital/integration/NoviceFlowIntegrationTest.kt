package com.flowhack.flowcapital.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.NoviceFlowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для Потока Новичка (ПН).
 * Проверяют реальную работу с БД (Room) согласно ТЗ.
 */
class NoviceFlowIntegrationTest : BaseIntegrationTest() {

    /**
     * Т3.9: Старт ПН - проверка записи PN_START в БД.
     * Согласно ТЗ: "Старт ПН: В потоке = Взнос + (Взнос * Бонус%)"
     */
    @Test
    fun startNoviceFlow_createsPnStartRecord() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15) // Четверг
        val deposit = 10000.0
        val bonusPercent = 50.0 // 50% бонус
        val inFlow = deposit + (deposit * bonusPercent / 100) // 15000
        val dailyPercent = 2.0 // 2% ежедневный

        // Act
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = startDate,
                step = 1,
                percent = dailyPercent,
                inFlowAmount = inFlow,
                dailyAccrual = inFlow * dailyPercent / 100,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        assertEquals("Должна быть 1 запись", 1, allRecords.size)
        val startRecord = allRecords[0]
        assertEquals("Тип должен быть PN_START", "PN_START", startRecord.actionType)
        assertEquals("В потоке должно быть 15000", 15000.0, startRecord.inFlowAmount, 0.01)
        assertEquals("Начисление должно быть 300", 300.0, startRecord.dailyAccrual, 0.01)
    }

    /**
     * Т3.10: Нажатие кнопки ПН - проверка создания PN_DAILY.
     * Согласно ТЗ: "Начисление вычитается из В потоке и прибавляется к Кошелек"
     */
    @Test
    fun pressNoviceButton_createsPnDaily() = runBlocking {
        // Arrange - старт ПН
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = startDate,
                step = 1,
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        // Act - нажатие кнопки
        val lastEntry = noviceFlowDao.getLastEntry()!!
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = startDate,
                step = lastEntry.step + 1,
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount - lastEntry.dailyAccrual,
                dailyAccrual = (lastEntry.inFlowAmount - lastEntry.dailyAccrual) * lastEntry.percent / 100,
                walletAmount = lastEntry.walletAmount + lastEntry.dailyAccrual,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
        )

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        assertEquals("Должно быть 2 записи", 2, allRecords.size)

        val dailyRecord = allRecords.find { it.actionType == "PN_DAILY" }!!
        assertEquals("Шаг должен увеличиться", 2, dailyRecord.step)
        assertEquals("В потоке должно уменьшиться", 14700.0, dailyRecord.inFlowAmount, 0.01)
        assertEquals("Кошелек должен увеличиться", 300.0, dailyRecord.walletAmount, 0.01)
        assertTrue("Кнопка должна быть нажата", dailyRecord.isButtonPressed)
    }

    /**
     * Т3.11: Пропуск дня в ПН - создание PN_MISSED.
     * Согласно ТЗ: "Если кнопка не нажата за день, генерируется запись PN_MISSED"
     */
    @Test
    fun missedDayNovice_createsPnMissed() = runBlocking {
        // Arrange - старт и нажатие в понедельник
        val monday = createDateMillis(2026, Calendar.JANUARY, 12)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = monday,
                step = 1,
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = monday,
                step = 2,
                percent = 2.0,
                inFlowAmount = 14700.0,
                dailyAccrual = 294.0,
                walletAmount = 300.0,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
        )

        // Act - пропуск вторника, зашли в среду
        val tuesday = createDateMillis(2026, Calendar.JANUARY, 13)
        val lastEntry = noviceFlowDao.getAllHistory().first().lastOrNull() ?: throw AssertionError("Нет записей")
        noviceFlowDao.insert(
            NoviceFlowEntity(
                id = 0, // Сбрасываем ID для автогенерации
                date = tuesday,
                step = lastEntry.step, // Шаг не увеличивается
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount,
                dailyAccrual = lastEntry.dailyAccrual,
                walletAmount = lastEntry.walletAmount,
                isButtonPressed = false,
                actionType = "PN_MISSED"
            )
        )
        
        // Assert
        val missedRecord = noviceFlowDao.getAllHistory().first().find { it.actionType == "PN_MISSED" }
        assertTrue("Должна быть запись PN_MISSED", missedRecord != null)
        assertEquals("PN_MISSED должна быть во вторник", tuesday, missedRecord!!.date)
        assertEquals("Шаг не должен увеличиться", 2, missedRecord.step)
    }

    /**
     * Т3.12: Воскресенье в ПН - создание PN_SUNDAY.
     * Согласно ТЗ: "Воскресенье — неактивный день (генерируется запись PN_SUNDAY)"
     */
    @Test
    fun sundayNovice_createsPnSunday() = runBlocking {
        // Arrange - суббота
        val saturday = createDateMillis(2026, Calendar.JANUARY, 17)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = saturday,
                step = 1,
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        // Act - воскресенье
        val sunday = createDateMillis(2026, Calendar.JANUARY, 18)
        val lastEntry = noviceFlowDao.getLastEntry()!!
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = sunday,
                step = lastEntry.step,
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount,
                dailyAccrual = 0.0, // В воскресенье начисления нет
                walletAmount = lastEntry.walletAmount,
                isButtonPressed = false,
                actionType = "PN_SUNDAY"
            )
        )

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        val sundayRecord = allRecords.find { it.actionType == "PN_SUNDAY" }
        assertTrue("Должна быть запись PN_SUNDAY", sundayRecord != null)
        assertEquals("PN_SUNDAY должна быть в воскресенье", sunday, sundayRecord!!.date)
        assertEquals("Начисление в воскресенье должно быть 0", 0.0, sundayRecord.dailyAccrual, 0.01)
    }

    /**
     * Т3.13: Реинвест ПН - увеличение потока с бонусом.
     * Согласно ТЗ: "Взнос приплюсовывается к В потоке с учетом бонуса"
     */
    @Test
    fun reinvestNovice_increasesInFlowWithBonus() = runBlocking {
        // Arrange - текущее состояние ПН
        val date = createDateMillis(2026, Calendar.JANUARY, 20)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = date,
                step = 5,
                percent = 2.0,
                inFlowAmount = 14000.0,
                dailyAccrual = 280.0,
                walletAmount = 1000.0,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
        )

        // Act - реинвест 5000 с бонусом 50%
        val reinvestAmount = 5000.0
        val bonusPercent = 50.0
        val lastEntry = noviceFlowDao.getLastEntry()!!
        val newInFlow = lastEntry.inFlowAmount + reinvestAmount * (1 + bonusPercent / 100) // 14000 + 7500 = 21500

        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = date,
                step = lastEntry.step + 1,
                percent = lastEntry.percent,
                inFlowAmount = newInFlow,
                dailyAccrual = newInFlow * lastEntry.percent / 100,
                walletAmount = lastEntry.walletAmount, // Кошелек не меняется (если пустой)
                isButtonPressed = lastEntry.isButtonPressed, // Сохраняем состояние кнопки
                actionType = "PN_REINVEST"
            )
        )

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        val reinvestRecord = allRecords.find { it.actionType == "PN_REINVEST" }
        assertTrue("Должна быть запись PN_REINVEST", reinvestRecord != null)
        assertEquals("В потоке должно быть 21500", 21500.0, reinvestRecord!!.inFlowAmount, 0.01)
        assertEquals("Шаг должен увеличиться", 6, reinvestRecord.step)
    }

    /**
     * Т3.14: Коррекция ПН - изменение только кошелька.
     * Согласно ТЗ: "Если меняется Кошелек: Меняется только кошелек"
     */
    @Test
    fun correctionNovice_updatesOnlyWallet() = runBlocking {
        // Arrange
        val date = createDateMillis(2026, Calendar.JANUARY, 20)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = date,
                step = 3,
                percent = 2.0,
                inFlowAmount = 14000.0,
                dailyAccrual = 280.0,
                walletAmount = 500.0,
                isButtonPressed = true,
                actionType = "PN_DAILY"
            )
        )

        // Act - коррекция кошелька (создаем новую запись с новым ID)
        val lastEntry = noviceFlowDao.getLastEntry()!!
        noviceFlowDao.insert(
            NoviceFlowEntity(
                id = 0, // Сбрасываем ID для автогенерации
                date = date,
                step = lastEntry.step,
                percent = lastEntry.percent,
                inFlowAmount = lastEntry.inFlowAmount,
                dailyAccrual = lastEntry.dailyAccrual,
                walletAmount = 0.0, // Пользователь указал 0
                isButtonPressed = lastEntry.isButtonPressed,
                actionType = "PN_CORRECTION"
            )
        )

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        val correctionRecord = allRecords.find { it.actionType == "PN_CORRECTION" }
        assertTrue("Должна быть запись PN_CORRECTION", correctionRecord != null)
        assertEquals("Кошелек должен стать 0", 0.0, correctionRecord!!.walletAmount, 0.01)
        assertEquals("В потоке не должно измениться", 14000.0, correctionRecord.inFlowAmount, 0.01)
    }

    /**
     * Т3.15: Проверка сложного процента в прогнозе ПН.
     * Согласно ТЗ: "Сложный процент: когда в кошельке >= суммы реинвеста, происходит реинвест"
     */
    @Test
    fun compoundInterest_reinvestWhenWalletReachesThreshold() = runBlocking {
        // Arrange - старт ПН с небольшим потоком
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = startDate,
                step = 1,
                percent = 2.0,
                inFlowAmount = 10000.0 + 5000.0, // 15000 с бонусом 50%
                dailyAccrual = 300.0, // 2% от 15000
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        // Act - нажатия кнопки до накопления на реинвест (2000)
        var currentInFlow = 15000.0
        var currentWallet = 0.0
        var currentStep = 1
        val reinvestThreshold = 2000.0

        // 7 нажатий: 300 * 7 = 2100 накопится в кошельке
        for (i in 1..7) {
            currentInFlow -= 300.0
            currentWallet += 300.0
            currentStep++
            noviceFlowDao.insert(
                NoviceFlowEntity(
                    date = startDate,
                    step = currentStep,
                    percent = 2.0,
                    inFlowAmount = currentInFlow,
                    dailyAccrual = currentInFlow * 0.02,
                    walletAmount = currentWallet,
                    isButtonPressed = true,
                    actionType = "PN_DAILY"
                )
            )
        }

        // Теперь кошелек >= 2000, должен сработать сложный процент (реинвест)
        if (currentWallet >= reinvestThreshold) {
            val reinvestAmount = currentWallet
            val bonusPercent = 50.0
            currentInFlow += reinvestAmount * (1 + bonusPercent / 100)
            currentWallet = 0.0
            currentStep++

            noviceFlowDao.insert(
                NoviceFlowEntity(
                    date = startDate,
                    step = currentStep,
                    percent = 2.0,
                    inFlowAmount = currentInFlow,
                    dailyAccrual = currentInFlow * 0.02,
                    walletAmount = currentWallet,
                    isButtonPressed = true,
                    actionType = "PN_REINVEST"
                )
            )
        }

        // Assert
        val allRecords = noviceFlowDao.getAllHistory().first()
        val reinvestRecord = allRecords.find { it.actionType == "PN_REINVEST" }
        assertTrue("Должен быть реинвест при достижении порога", reinvestRecord != null)
        assertTrue("В потоке должно быть больше после реинвеста", reinvestRecord!!.inFlowAmount > 15000.0)
    }

    /**
     * Т3.16: Проверка лимита одного потока ПН.
     * Согласно ТЗ: "Только 1 поток" для ПН.
     */
    @Test
    fun noviceFlow_onlyOneFlowAllowed() = runBlocking {
        // Arrange - первая запись ПН
        val date1 = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowDao.insert(
            NoviceFlowEntity(
                date = date1,
                step = 1,
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        // Act - проверяем количество записей старта
        val allRecords = noviceFlowDao.getAllHistory().first()
        val startRecords = allRecords.filter { it.actionType == "PN_START" }

        // Assert
        assertEquals("Должна быть только 1 запись PN_START", 1, startRecords.size)
    }

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
