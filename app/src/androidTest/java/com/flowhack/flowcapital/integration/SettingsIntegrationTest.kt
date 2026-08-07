package com.flowhack.flowcapital.integration

import com.flowhack.flowcapital.data.db.NoviceFlowEntityV2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Интеграционные тесты для NoviceFlowsDao (v2) - таблица novice_flows.
 * Проверяют реальную работу с БД (Room) для потоков новичка v2.
 */
class SettingsIntegrationTest : BaseIntegrationTest() {

    private lateinit var noviceFlowsDao: com.flowhack.flowcapital.data.db.NoviceFlowsDao

    override fun setUp() {
        super.setUp()
        noviceFlowsDao = database.noviceFlowsDao()
    }

    /**
     * Проверка: Вставка потока новичка v2 сохраняется в БД.
     */
    @Test
    fun insertNoviceFlow_savesToDb() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)

        // Act
        val flowId = noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Assert
        val flow = noviceFlowsDao.getFlowById(flowId.toInt())
        assertTrue("Поток должен быть найден", flow != null)
        assertEquals("Номинал должен быть 10000", 10000.0, flow!!.nominalAmount, 0.01)
        assertEquals("Процент должен быть 2.0", 2.0, flow.currentPercent, 0.01)
        assertEquals("В потоке должно быть 15000", 15000.0, flow.totalInFlow, 0.01)
    }

    /**
     * Проверка: getAllFlows возвращает все потоки.
     */
    @Test
    fun getAllFlows_returnsAllFlows() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )
        noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 5000.0,
                currentPercent = 2.0,
                totalInFlow = 7500.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Act
        val flows = noviceFlowsDao.getAllFlows().first()

        // Assert
        assertEquals("Должно быть 2 потока", 2, flows.size)
    }

    /**
     * Проверка: getFlowsCount возвращает количество потоков.
     */
    @Test
    fun getFlowsCount_returnsNumberOfFlows() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Act
        val count = noviceFlowsDao.getFlowsCount()

        // Assert
        assertEquals("Должен быть 1 поток", 1, count)
    }

    /**
     * Проверка: update обновляет поток.
     */
    @Test
    fun update_updatesExistingFlow() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val flowId = noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Act
        val flow = noviceFlowsDao.getFlowById(flowId.toInt())!!
        noviceFlowsDao.update(flow.copy(totalWallet = 500.0, totalAccrued = 300.0))

        // Assert
        val updated = noviceFlowsDao.getFlowById(flowId.toInt())!!
        assertEquals("Кошелек должен обновиться", 500.0, updated.totalWallet, 0.01)
        assertEquals("Начислено должно обновиться", 300.0, updated.totalAccrued, 0.01)
    }

    /**
     * Проверка: deleteById удаляет поток.
     */
    @Test
    fun deleteById_removesFlow() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        val flowId = noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Act
        noviceFlowsDao.deleteById(flowId.toInt())

        // Assert
        assertEquals("Поток должен быть удалён", 0, noviceFlowsDao.getFlowsCount())
    }

    /**
     * Проверка: clearAll очищает все потоки.
     */
    @Test
    fun clearAll_removesAllFlows() = runBlocking {
        // Arrange
        val startDate = createDateMillis(2026, Calendar.JANUARY, 15)
        noviceFlowsDao.insert(
            NoviceFlowEntityV2(
                startDate = startDate,
                nominalAmount = 10000.0,
                currentPercent = 2.0,
                totalInFlow = 15000.0,
                totalWallet = 0.0,
                totalAccrued = 0.0
            )
        )

        // Act
        noviceFlowsDao.clearAll()

        // Assert
        assertEquals("Потоки должны быть очищены", 0, noviceFlowsDao.getFlowsCount())
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
