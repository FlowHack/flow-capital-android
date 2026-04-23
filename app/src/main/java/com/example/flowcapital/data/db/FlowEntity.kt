package com.example.flowcapital.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность записи истории Растущего Потока (РП).
 *
 * @property id Уникальный идентификатор записи
 * @property date Дата и время действия (timestamp)
 * @property percent Текущий процент начисления
 * @property inFlowAmount Сумма денег в потоке
 * @property dailyAccrual Ежедневное начисление
 * @property walletAmount Сумма в кошельке
 * @property isButtonPressed Была ли нажата кнопка начисления
 * @property actionType Тип действия: START, DAILY, REINVEST, CORRECTION, SUNDAY, MISSED
 */
@Entity(tableName = "growing_flow_history")
data class GrowingFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val percent: Double,
    val inFlowAmount: Double,
    val dailyAccrual: Double,
    val walletAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String
)

/**
 * Сущность записи истории Потока Новичка (ПН).
 *
 * @property id Уникальный идентификатор записи
 * @property date Дата и время действия (timestamp)
 * @property percent Процент начисления (фиксированный 2%)
 * @property inFlowAmount Сумма денег в потоке
 * @property dailyAccrual Ежедневное начисление
 * @property walletAmount Сумма в кошельке
 * @property isButtonPressed Была ли нажата кнопка начисления
 * @property actionType Тип действия: START, DAILY, REINVEST, CORRECTION, SUNDAY, MISSED
 */
@Entity(tableName = "novice_flow_history")
data class NoviceFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val percent: Double,
    val inFlowAmount: Double,
    val dailyAccrual: Double,
    val walletAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String
)

/**
 * Сущность Потока Новичка (ПН) - агрегированные данные.
 * Хранит текущее состояние одного экземпляра ПН.
 *
 * @property id Уникальный идентификатор
 * @property startDate Дата старта потока
 * @property nominalAmount Номинал потока
 * @property currentPercent Текущий процент (фиксированный 2%)
 * @property totalInFlow Общая сумма в потоке
 * @property totalWallet Общая сумма в кошельке
 * @property totalAccrued Всего начислено за все время
 */
@Entity(tableName = "novice_flows")
data class NoviceFlowEntityV2(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startDate: Long,
    val nominalAmount: Double,
    val currentPercent: Double = 2.0,
    val totalInFlow: Double,
    val totalWallet: Double,
    val totalAccrued: Double
)

/**
 * Сущность Премиум Стартового Потока (ПСП).
 * Каждый экземпляр ПСП создаётся отдельно и имеет свой номинал.
 *
 * @property id Уникальный идентификатор
 * @property nominalAmount Номинал потока
 * @property startDate Дата создания потока
 * @property totalAccrued Всего накапало по всем периодам
 * @property isActive Активен ли поток
 * @property currentPeriod Текущий период (1-20)
 */
@Entity(tableName = "premium_start_flows")
data class PremiumStartFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nominalAmount: Double,
    val startDate: Long,
    val totalAccrued: Double = 0.0,
    val isActive: Boolean = true,
    val currentPeriod: Int = 1
)

/**
 * Сущность периода ПСП.
 * Хранит информацию о каждом периоде отдельно.
 *
 * @property id Уникальный идентификатор
 * @property flowId ID родительского потока
 * @property periodNumber Номер периода (1-20)
 * @property percent Процент начисления от номинала
 * @property startDate Дата начала периода
 * @property endDate Дата окончания периода (через 2 недели)
 * @property accrualAmount Сумма начисления (номинал * процент / 100)
 * @property isContributionMade Был ли сделан взнос номинала
 * @property contributionDate Дата взноса
 * @property isCompleted Завершён ли период
 */
@Entity(tableName = "premium_start_periods")
data class PremiumStartPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val flowId: Int,
    val periodNumber: Int,
    val percent: Double,
    val startDate: Long,
    val endDate: Long,
    val accrualAmount: Double,
    val isContributionMade: Boolean = false,
    val contributionDate: Long? = null,
    val isCompleted: Boolean = false
)
