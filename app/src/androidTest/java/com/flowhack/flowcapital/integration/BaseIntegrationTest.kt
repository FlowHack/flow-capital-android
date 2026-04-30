package com.flowhack.flowcapital.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.GrowingFlowDao
import com.flowhack.flowcapital.data.db.NoviceFlowDao
import com.flowhack.flowcapital.data.db.PremiumStartFlowDao
import com.flowhack.flowcapital.data.db.PremiumStartPeriodDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/**
 * Базовый класс для интеграционных тестов.
 * Настраивает in-memory базу Room для тестирования DAO слоя.
 */
open class BaseIntegrationTest {
    
    protected lateinit var database: AppDatabase
    protected lateinit var growingFlowDao: GrowingFlowDao
    protected lateinit var noviceFlowDao: NoviceFlowDao
    protected lateinit var premiumStartFlowDao: PremiumStartFlowDao
    protected lateinit var premiumStartPeriodDao: PremiumStartPeriodDao
    protected lateinit var context: Context
    
    @Before
    open fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Создаем in-memory базу Room
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        
        growingFlowDao = database.growingFlowDao()
        noviceFlowDao = database.noviceFlowDao()
        premiumStartFlowDao = database.premiumStartFlowDao()
        premiumStartPeriodDao = database.premiumStartPeriodDao()
    }
    
    @After
    open fun tearDown() {
        database.clearAllTables()
        database.close()
    }
}
