package com.flowhack.flowcapital.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность записи истории Растущего Потока (РП).
 *
 * @property id Уникальный идентификатор записи (автогенерация)
 * @property date Дата и время действия в миллисекундах (timestamp)
 * @property step Порядковый номер шага в истории потока (для экспорта в Excel)
 * @property percent Текущий процент начисления (растет на 0.003 каждый день)
 * @property inFlowAmount Сумма денег в потоке (уменьшается при начислении)
 * @property dailyAccrual Ежедневное начисление (inFlow * percent / 100)
 * @property walletAmount Сумма в кошельке (увеличивается при начислении)
 * @property isButtonPressed Была ли нажата кнопка начисления в этот день
 * @property actionType Тип действия:
 *     - START: старт потока
 *     - DAILY: ежедневное начисление
 *     - REINVEST: реинвест (взнос с бонусом)
 *     - CORRECTION: ручная корректировка значений
 *     - SUNDAY: воскресенье (нет начислений)
 *     - MISSED: пропущенный день (не нажали кнопку)
 */
@Entity(tableName = "growing_flow_history")
data class GrowingFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val step: Int = 1,
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
 * @property id Уникальный идентификатор записи (автогенерация)
 * @property date Дата и время действия в миллисекундах (timestamp)
 * @property step Порядковый номер шага в истории потока (для экспорта в Excel)
 * @property percent Процент начисления (фиксированный, по умолчанию 2%)
 * @property inFlowAmount Сумма денег в потоке (уменьшается при начислении)
 * @property dailyAccrual Ежедневное начисление (inFlow * percent / 100)
 * @property walletAmount Сумма в кошельке (увеличивается при начислении)
 * @property isButtonPressed Была ли нажата кнопка начисления в этот день
 * @property actionType Тип действия:
 *     - PN_START: старт потока ПН
 *     - PN_DAILY: ежедневное начисление ПН
 *     - PN_REINVEST: реинвест ПН (взнос с бонусом 50%)
 *     - PN_CORRECTION: ручная корректировка значений ПН
 *     - PN_FORECAST: запись прогноза ПН
 *     - PN_CYCLE_END: запись прогноза до конца цикла ПН
 *     - SUNDAY: воскресенье (нет начислений)
 *     - MISSED: пропущенный день (не нажали кнопку)
 */
@Entity(tableName = "novice_flow_history")
data class NoviceFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val step: Int = 1,
    val percent: Double,
    val inFlowAmount: Double,
    val dailyAccrual: Double,
    val walletAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String
)

/**
 * Сущность Потока Новичка (ПН) версии 2 - агрегированные данные.
 * Хранит текущее состояние одного экземпляра ПН (не используется активно, история в NoviceFlowEntity).
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property startDate Дата старта потока (timestamp)
 * @property nominalAmount Номинал потока (сумма взноса)
 * @property currentPercent Текущий процент начисления (фиксированный, из настроек)
 * @property totalInFlow Общая сумма в потоке (inFlow всех записей)
 * @property totalWallet Общая сумма в кошельке
 * @property totalAccrued Всего начислено за все время (сумма всех dailyAccrual)
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
 * ПСП длится 20 периодов по 14 дней каждый.
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property nominalAmount Номинал потока (сумма взноса за период)
 * @property startDate Дата создания потока (старт первого периода, timestamp)
 * @property totalAccrued Всего накапало по всем периодам
 * @property isActive Активен ли поток (true - можно делать взносы)
 * @property currentPeriod Текущий период (1-20, инкрементируется после взноса)
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
 * Сущность периода Премиум Стартового Потока (ПСП).
 * Хранит информацию о каждом из 20 периодов отдельно.
 * Период длится 14 дней, после чего нужно сделать взнос для перехода к следующему.
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property flowId ID родительского потока (PremiumStartFlowEntity)
 * @property periodNumber Номер периода (1-20, коэффициенты из настроек)
 * @property percent Процент начисления от номинала за этот период
 * @property startDate Дата начала периода (timestamp)
 * @property endDate Дата окончания периода (startDate + 14 дней, timestamp)
 * @property accrualAmount Сумма начисления (номинал * процент / 100)
 * @property isContributionMade Был ли сделан взнос номинала в этот период
 * @property contributionDate Дата взноса (timestamp, null если не сделан)
 * @property isCompleted Завершён ли период (после 14 дней или взноса)
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

/**
 * Сущность Быстрого Потока (БП) или Супер Быстрого Потока (СБП).
 * Каждый экземпляр создаётся отдельно и имеет свой номинал и тип.
 * БП длится 30 дней, СБП — 15 дней. Ежедневное начисление одинаково для обоих типов,
 * различаются только срок и коэффициенты.
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property type Тип потока: "BP" (Быстрый) или "SBP" (Супер Быстрый)
 * @property nominalAmount Номинал потока (сумма взноса)
 * @property startDate Дата создания потока (старт, timestamp)
 * @property currentDay Текущий день (1..30 для БП, 1..15 для СБП)
 * @property totalAccrued Всего начислено (сумма всех ежедневных начислений)
 * @property dailyAccrual Ежедневное начисление (номинал * (1 + процент/100) / дней)
 * @property percent Итоговый процент прироста (из таблицы коэффициентов)
 * @property isActive Активен ли поток (true - можно нажимать кнопку)
 */
@Entity(tableName = "fast_flows")
data class FastFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val nominalAmount: Double,
    val startDate: Long,
    val currentDay: Int = 1,
    val totalAccrued: Double = 0.0,
    val dailyAccrual: Double = 0.0,
    val percent: Double = 0.0,
    val isActive: Boolean = true
)

/**
 * Сущность дня Быстрого/Супер Быстрого Потока (БП/СБП).
 * Хранит информацию о каждом дне потока отдельно.
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property flowId ID родительского потока (FastFlowEntity)
 * @property dayNumber Номер дня (1..30 для БП, 1..15 для СБП)
 * @property date Дата дня (timestamp)
 * @property accrualAmount Начисление за этот день
 * @property isButtonPressed Была ли нажата кнопка в этот день
 * @property actionType Тип действия:
 *     - START: старт потока
 *     - DAILY: ежедневное начисление
 *     - SUNDAY: воскресенье (нет начислений)
 *     - MISSED: пропущенный день (не нажали кнопку)
 *     - CORRECTION: ручная корректировка значений
 */
@Entity(tableName = "fast_flow_days")
data class FastFlowDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val flowId: Int,
    val dayNumber: Int,
    val date: Long,
    val accrualAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String
)
