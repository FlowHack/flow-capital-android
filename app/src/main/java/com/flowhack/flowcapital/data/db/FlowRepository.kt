package com.flowhack.flowcapital.data.db

import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления историей Растущего Потока (РП).
 */
class GrowingFlowRepository(private val dao: GrowingFlowDao) {
    val allHistory: Flow<List<GrowingFlowEntity>> = dao.getAllHistory()

    suspend fun getLastEntry(): GrowingFlowEntity? = dao.getLastEntry()

    suspend fun insertEntry(entry: GrowingFlowEntity) = dao.insert(entry)

    suspend fun updateEntry(entry: GrowingFlowEntity) = dao.update(entry)

    suspend fun clearHistory() = dao.clearAll()

    suspend fun getEntriesForDateRange(startDate: Long, endDate: Long): List<GrowingFlowEntity> =
        dao.getEntriesForDateRange(startDate, endDate)

    suspend fun getLastEntryBeforeDate(date: Long): GrowingFlowEntity? =
        dao.getLastEntryBeforeDate(date)

    suspend fun getFirstStartEntry(): GrowingFlowEntity? =
        dao.getFirstStartEntry()

    suspend fun getLastPressEntry(): GrowingFlowEntity? = dao.getLastPressEntry()
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

    suspend fun getEntriesForDateRange(startDate: Long, endDate: Long): List<NoviceFlowEntity> =
        dao.getEntriesForDateRange(startDate, endDate)

    suspend fun getLastEntryBeforeDate(date: Long): NoviceFlowEntity? =
        dao.getLastEntryBeforeDate(date)

    suspend fun getFirstStartEntry(): NoviceFlowEntity? =
        dao.getFirstStartEntry()

    suspend fun getLastPressEntry(): NoviceFlowEntity? = dao.getLastPressEntry()

    suspend fun updateEntry(entry: NoviceFlowEntity) = dao.update(entry)
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

    suspend fun getPeriodByNumber(flowId: Int, periodNumber: Int): PremiumStartPeriodEntity? =
        periodDao.getPeriodByNumber(flowId, periodNumber)

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

/**
 * Репозиторий для управления Быстрым/Супер Быстрым Потоком (БП/СБП).
 */
class FastFlowRepository(
    private val flowDao: FastFlowDao,
    private val dayDao: FastFlowDayDao
) {
    val allFlows: Flow<List<FastFlowEntity>> = flowDao.getAllFlows()

    suspend fun getFlowById(id: Int): FastFlowEntity? = flowDao.getFlowById(id)

    suspend fun insertFlow(flow: FastFlowEntity): Long = flowDao.insert(flow)

    suspend fun updateFlow(flow: FastFlowEntity) = flowDao.update(flow)

    /** Удаляет поток и все связанные дни */
    suspend fun deleteFlow(id: Int) {
        dayDao.deleteByFlowId(id)
        flowDao.deleteById(id)
    }

    suspend fun getFlowsCount(): Int = flowDao.getFlowsCount()

    fun getDaysByFlowId(flowId: Int): Flow<List<FastFlowDayEntity>> =
        dayDao.getDaysByFlowId(flowId)

    suspend fun getAllDaysForFlow(flowId: Int): List<FastFlowDayEntity> =
        dayDao.getAllDaysForFlow(flowId)

    suspend fun getCurrentDay(flowId: Int): FastFlowDayEntity? =
        dayDao.getCurrentDay(flowId)

    suspend fun getDayByNumber(flowId: Int, dayNumber: Int): FastFlowDayEntity? =
        dayDao.getDayByNumber(flowId, dayNumber)

    suspend fun getLastPressEntry(flowId: Int): FastFlowDayEntity? =
        dayDao.getLastPressEntry(flowId)

    suspend fun insertDay(day: FastFlowDayEntity): Long =
        dayDao.insert(day)

    suspend fun insertDays(days: List<FastFlowDayEntity>) =
        dayDao.insertAll(days)

    suspend fun updateDay(day: FastFlowDayEntity) =
        dayDao.update(day)

    /** Удаляет все дни и все потоки */
    suspend fun clearAll() {
        dayDao.clearAll()
        flowDao.clearAll()
    }
}
