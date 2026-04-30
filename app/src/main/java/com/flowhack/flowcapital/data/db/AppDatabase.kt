package com.flowhack.flowcapital.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Основной класс базы данных приложения.
 * Содержит отдельные таблицы для каждого типа потока:
 * - Растущий Поток (история)
 * - Поток Новичка (история)
 * - Поток Новичка v2 (агрегированные данные)
 * - Премиум Стартовый Поток
 * - Периоды ПСП
 */
@Database(
    entities = [
        GrowingFlowEntity::class,
        NoviceFlowEntity::class,
        NoviceFlowEntityV2::class,
        PremiumStartFlowEntity::class,
        PremiumStartPeriodEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun growingFlowDao(): GrowingFlowDao
    abstract fun noviceFlowDao(): NoviceFlowDao
    abstract fun noviceFlowsDao(): NoviceFlowsDao
    abstract fun premiumStartFlowDao(): PremiumStartFlowDao
    abstract fun premiumStartPeriodDao(): PremiumStartPeriodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
