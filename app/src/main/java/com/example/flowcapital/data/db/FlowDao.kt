package com.example.flowcapital.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с историей Растущего Потока (РП).
 */
@Dao
interface GrowingFlowDao {
    @Query("SELECT * FROM growing_flow_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<GrowingFlowEntity>>

    @Query("SELECT * FROM growing_flow_history ORDER BY date DESC LIMIT 1")
    suspend fun getLastEntry(): GrowingFlowEntity?

    @Insert
    suspend fun insert(flowEntity: GrowingFlowEntity)

    @Query("DELETE FROM growing_flow_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM growing_flow_history")
    suspend fun getRecordsCount(): Int

    @Query("SELECT * FROM growing_flow_history WHERE date >= :startDate AND date < :endDate ORDER BY date ASC")
    suspend fun getEntriesForDateRange(startDate: Long, endDate: Long): List<GrowingFlowEntity>

    @Query("SELECT * FROM growing_flow_history WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getLastEntryBeforeDate(date: Long): GrowingFlowEntity?

    @Query("SELECT * FROM growing_flow_history WHERE actionType = 'START' ORDER BY date ASC LIMIT 1")
    suspend fun getFirstStartEntry(): GrowingFlowEntity?
}

/**
 * DAO для работы с историей Потока Новичка (ПН).
 */
@Dao
interface NoviceFlowDao {
    @Query("SELECT * FROM novice_flow_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<NoviceFlowEntity>>

    @Query("SELECT * FROM novice_flow_history ORDER BY date DESC LIMIT 1")
    suspend fun getLastEntry(): NoviceFlowEntity?

    @Insert
    suspend fun insert(flowEntity: NoviceFlowEntity)

    @Query("DELETE FROM novice_flow_history")
    suspend fun clearAll()

    @Query("SELECT * FROM novice_flow_history")
    suspend fun getAllEntries(): List<NoviceFlowEntity>
}

/**
 * DAO для работы с экземплярами Потока Новичка (v2).
 */
@Dao
interface NoviceFlowsDao {
    @Query("SELECT * FROM novice_flows")
    fun getAllFlows(): Flow<List<NoviceFlowEntityV2>>

    @Query("SELECT * FROM novice_flows WHERE id = :id")
    suspend fun getFlowById(id: Int): NoviceFlowEntityV2?

    @Insert
    suspend fun insert(flow: NoviceFlowEntityV2): Long

    @Update
    suspend fun update(flow: NoviceFlowEntityV2)

    @Query("DELETE FROM novice_flows WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM novice_flows")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM novice_flows")
    suspend fun getFlowsCount(): Int
}

/**
 * DAO для работы с ПСП.
 */
@Dao
interface PremiumStartFlowDao {
    @Query("SELECT * FROM premium_start_flows ORDER BY id DESC")
    fun getAllFlows(): Flow<List<PremiumStartFlowEntity>>

    @Query("SELECT * FROM premium_start_flows WHERE id = :id")
    suspend fun getFlowById(id: Int): PremiumStartFlowEntity?

    @Insert
    suspend fun insert(flow: PremiumStartFlowEntity): Long

    @Update
    suspend fun update(flow: PremiumStartFlowEntity)

    @Query("DELETE FROM premium_start_flows WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM premium_start_flows")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM premium_start_flows")
    suspend fun getFlowsCount(): Int
}

/**
 * DAO для работы с периодами ПСП.
 */
@Dao
interface PremiumStartPeriodDao {
    @Query("SELECT * FROM premium_start_periods WHERE flowId = :flowId ORDER BY periodNumber ASC")
    fun getPeriodsByFlowId(flowId: Int): Flow<List<PremiumStartPeriodEntity>>

    @Query("SELECT * FROM premium_start_periods WHERE flowId = :flowId AND periodNumber = :periodNumber")
    suspend fun getPeriodByNumber(flowId: Int, periodNumber: Int): PremiumStartPeriodEntity?

    /** Возвращает первый незавершённый период */
    @Query("SELECT * FROM premium_start_periods WHERE flowId = :flowId AND isCompleted = 0 ORDER BY periodNumber ASC LIMIT 1")
    suspend fun getCurrentPeriod(flowId: Int): PremiumStartPeriodEntity?

    @Insert
    suspend fun insert(period: PremiumStartPeriodEntity): Long

    @Insert
    suspend fun insertAll(periods: List<PremiumStartPeriodEntity>)

    @Update
    suspend fun update(period: PremiumStartPeriodEntity)

    @Query("DELETE FROM premium_start_periods WHERE flowId = :flowId")
    suspend fun deleteByFlowId(flowId: Int)

    @Query("DELETE FROM premium_start_periods")
    suspend fun clearAll()
}
