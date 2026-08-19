package com.flowhack.flowcapital.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Основной класс базы данных приложения.
 * Содержит отдельные таблицы для каждого типа потока:
 * - Растущий Поток (история)
 * - Поток Новичка (история)
 * - Поток Новичка v2 (агрегированные данные)
 * - Премиум Стартовый Поток
 * - Периоды ПСП
 * - Быстрый/Супер Быстрый Поток (БП/СБП)
 */
@Database(
    entities = [
        GrowingFlowEntity::class,
        NoviceFlowEntity::class,
        NoviceFlowEntityV2::class,
        PremiumStartFlowEntity::class,
        PremiumStartPeriodEntity::class,
        FastFlowEntity::class,
        FastFlowDayEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun growingFlowDao(): GrowingFlowDao
    abstract fun noviceFlowDao(): NoviceFlowDao
    abstract fun noviceFlowsDao(): NoviceFlowsDao
    abstract fun premiumStartFlowDao(): PremiumStartFlowDao
    abstract fun premiumStartPeriodDao(): PremiumStartPeriodDao
    abstract fun fastFlowDao(): FastFlowDao
    abstract fun fastFlowDayDao(): FastFlowDayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Миграция с версии 2 на 3: добавление таблиц Быстрого/Супер Быстрого Потока.
         * Создаёт новые таблицы без потери существующих данных РП/ПН/ПСП.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fast_flows` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`nominalAmount` REAL NOT NULL, " +
                        "`startDate` INTEGER NOT NULL, " +
                        "`currentDay` INTEGER NOT NULL, " +
                        "`totalAccrued` REAL NOT NULL, " +
                        "`dailyAccrual` REAL NOT NULL, " +
                        "`percent` REAL NOT NULL, " +
                        "`isActive` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fast_flow_days` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`flowId` INTEGER NOT NULL, " +
                        "`dayNumber` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`accrualAmount` REAL NOT NULL, " +
                        "`isButtonPressed` INTEGER NOT NULL, " +
                        "`actionType` TEXT NOT NULL)"
                )
            }
        }

        /**
         * Получить экземпляр базы данных.
         * Использует синглтон для предотвращения создания нескольких экземпляров.
         *
         * @param context Контекст приложения
         * @return Экземпляр AppDatabase
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "potok_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
