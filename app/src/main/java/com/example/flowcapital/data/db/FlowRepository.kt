package com.example.flowcapital.data.db

import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления историей Растущего Потока (РП).
 */
class GrowingFlowRepository(private val dao: GrowingFlowDao) {
    val allHistory: Flow<List<GrowingFlowEntity>> = dao.getAllHistory()

    suspend fun getLastEntry(): GrowingFlowEntity? = dao.getLastEntry()

    suspend fun insertEntry(entry: GrowingFlowEntity) = dao.insert(entry)

    suspend fun clearHistory() = dao.clearAll()

    suspend fun getEntriesForDateRange(startDate: Long, endDate: Long): List<GrowingFlowEntity> =
        dao.getEntriesForDateRange(startDate, endDate)

    suspend fun getLastEntryBeforeDate(date: Long): GrowingFlowEntity? =
        dao.getLastEntryBeforeDate(date)
}

/**
 * Репозиторий для управления историей Потока Новичка (ПН).
 */
class NoviceFlowRepository(private val dao: NoviceFlowDao) {
    val allHistory: Flow<List<NoviceFlowEntity>> = dao.getAllHistory()

    suspend fun getLastEntry(): NoviceFlowEntity? = dao.getLastEntry()

    suspend fun insertEntry(entry: NoviceFlowEntity) = dao.insert(entry)

    suspend fun getAllEntries(): List<NoviceFlowEntity> = dao.getAllEntries()

    suspend fun clearHistory() = dao.clearAll()
}

/**
 * Репозиторий для управления экземплярами Потока Новичка (v2).
 */
class NoviceFlowsRepository(private val dao: NoviceFlowsDao) {
    val allFlows: Flow<List<NoviceFlowEntityV2>> = dao.getAllFlows()

    suspend fun getFlowById(id: Int): NoviceFlowEntityV2? = dao.getFlowById(id)

    suspend fun insertFlow(flow: NoviceFlowEntityV2): Long = dao.insert(flow)

    suspend fun updateFlow(flow: NoviceFlowEntityV2) = dao.update(flow)

    suspend fun deleteFlow(id: Int) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun getFlowsCount(): Int = dao.getFlowsCount()
}

/**
 * Репозиторий для управления ПСП.
 */
class PremiumStartFlowRepository(
    private val flowDao: PremiumStartFlowDao,
    private val periodDao: PremiumStartPeriodDao
) {
    val allFlows: Flow<List<PremiumStartFlowEntity>> = flowDao.getAllFlows()

    suspend fun getFlowById(id: Int): PremiumStartFlowEntity? = flowDao.getFlowById(id)

    suspend fun insertFlow(flow: PremiumStartFlowEntity): Long = flowDao.insert(flow)

    suspend fun updateFlow(flow: PremiumStartFlowEntity) = flowDao.update(flow)

    /** Удаляет поток и все связанные периоды */
    suspend fun deleteFlow(id: Int) {
        periodDao.deleteByFlowId(id)
        flowDao.deleteById(id)
    }

    suspend fun getFlowsCount(): Int = flowDao.getFlowsCount()

    fun getPeriodsByFlowId(flowId: Int): Flow<List<PremiumStartPeriodEntity>> =
        periodDao.getPeriodsByFlowId(flowId)

    suspend fun getCurrentPeriod(flowId: Int): PremiumStartPeriodEntity? =
        periodDao.getCurrentPeriod(flowId)

    suspend fun insertPeriod(period: PremiumStartPeriodEntity): Long =
        periodDao.insert(period)

    suspend fun insertPeriods(periods: List<PremiumStartPeriodEntity>) =
        periodDao.insertAll(periods)

    suspend fun updatePeriod(period: PremiumStartPeriodEntity) =
        periodDao.update(period)

    /** Удаляет все периоды и все потоки */
    suspend fun clearAll() {
        periodDao.clearAll()
        flowDao.clearAll()
    }
}
