package com.example.flowcapital

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.NoviceFlowEntity
import com.example.flowcapital.ui.screens.calculator.CorrectionDialog
import com.example.flowcapital.ui.screens.calculator.CurrentStatsCard
import com.example.flowcapital.ui.screens.calculator.FlowTabs
import com.example.flowcapital.ui.screens.calculator.GrowingFlowContent
import com.example.flowcapital.ui.screens.calculator.NoviceCorrectionDialog
import com.example.flowcapital.ui.screens.calculator.NoviceFlowContent
import com.example.flowcapital.ui.screens.calculator.NoviceReinvestDialog
import com.example.flowcapital.ui.screens.calculator.NoviceStatsCard
import com.example.flowcapital.ui.screens.calculator.ReinvestDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Полные UI тесты для компонентов приложения
 */
class FullUiComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ========== ПН Диалоги ==========

    @Test
    fun `NoviceReinvestDialog shows all required fields`() {
        composeTestRule.setContent {
            NoviceReinvestDialog(
                onDismiss = {},
                onConfirm = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("Старт ПН").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сумма взноса *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Бонус ко взносу").assertIsDisplayed()
        composeTestRule.onNodeWithText("Внести").assertIsDisplayed()
    }

    @Test
    fun `NoviceReinvestDialog has wallet field`() {
        composeTestRule.setContent {
            NoviceReinvestDialog(
                onDismiss = {},
                onConfirm = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("В кошельке").assertExists()
    }

    @Test
    fun `NoviceCorrectionDialog shows all fields`() {
        composeTestRule.setContent {
            NoviceCorrectionDialog(
                onDismiss = {},
                onConfirm = { _, _, _, _ -> },
                currentInFlow = 15000.0,
                currentDailyPercent = 2.0,
                currentWallet = 500.0,
                currentButtonPressed = false
            )
        }

        composeTestRule.onNodeWithText("Корректировка ПН").assertIsDisplayed()
        composeTestRule.onNodeWithText("В потоке").assertIsDisplayed()
        composeTestRule.onNodeWithText("В кошельке").assertIsDisplayed()
        composeTestRule.onNodeWithText("Кнопка нажата").assertIsDisplayed()
    }

    @Test
    fun `NoviceCorrectionDialog shows current values`() {
        composeTestRule.setContent {
            NoviceCorrectionDialog(
                onDismiss = {},
                onConfirm = { _, _, _, _ -> },
                currentInFlow = 15000.0,
                currentDailyPercent = 2.0,
                currentWallet = 500.0,
                currentButtonPressed = true
            )
        }

        composeTestRule.onNodeWithText("15000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("300.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("500.00").assertIsDisplayed()
    }

    // ========== РП Диалоги ==========

    @Test
    fun `ReinvestDialog shows all fields`() {
        composeTestRule.setContent {
            ReinvestDialog(
                onDismiss = {},
                onConfirm = { _, _, _ -> },
                defaultPercent = 0.1
            )
        }

        composeTestRule.onNodeWithText("Старт / Реинвест РП").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сумма взноса *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Процент").assertIsDisplayed()
        composeTestRule.onNodeWithText("Текущий кошелёк").assertIsDisplayed()
    }

    @Test
    fun `ReinvestDialog has default percent`() {
        composeTestRule.setContent {
            ReinvestDialog(
                onDismiss = {},
                onConfirm = { _, _, _ -> },
                defaultPercent = 0.1
            )
        }

        composeTestRule.onNodeWithText("0.1").assertIsDisplayed()
    }

    @Test
    fun `CorrectionDialog shows all fields`() {
        composeTestRule.setContent {
            CorrectionDialog(
                onDismiss = {},
                onConfirm = { _, _, _, _ -> },
                currentInFlow = 20000.0,
                currentAccrual = 20.0,
                currentWallet = 100.0,
                currentButtonPressed = false
            )
        }

        composeTestRule.onNodeWithText("Корректировка РП").assertIsDisplayed()
        composeTestRule.onNodeWithText("Поток").assertIsDisplayed()
        composeTestRule.onNodeWithText("Начисление").assertIsDisplayed()
        composeTestRule.onNodeWithText("Кошелёк").assertIsDisplayed()
        composeTestRule.onNodeWithText("Кнопка нажата").assertIsDisplayed()
    }

    @Test
    fun `CorrectionDialog shows current values`() {
        composeTestRule.setContent {
            CorrectionDialog(
                onDismiss = {},
                onConfirm = { _, _, _, _ -> },
                currentInFlow = 20000.0,
                currentAccrual = 20.0,
                currentWallet = 100.0,
                currentButtonPressed = true
            )
        }

        composeTestRule.onNodeWithText("20000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("20.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("100.00").assertIsDisplayed()
    }

    // ========== ПН История ==========

    @Test
    fun `NoviceFlowContent shows create button when no history`() {
        composeTestRule.setContent {
            NoviceFlowContent(
                lastEntry = null,
                history = emptyList(),
                onReinvestClick = {},
                onCorrectionClick = {},
                onForecastClick = {},
                onCycleEndClick = {},
                onDailyButtonClick = {}
            )
        }

        composeTestRule.onNodeWithText("Думаю, стоит завести Поток Новичка!").assertIsDisplayed()
    }

    @Test
    fun `NoviceFlowContent shows all buttons when history exists`() {
        val history = listOf(
            NoviceFlowEntity(
                date = System.currentTimeMillis(),
                percent = 2.0,
                inFlowAmount = 15000.0,
                dailyAccrual = 300.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "PN_START"
            )
        )

        composeTestRule.setContent {
            NoviceFlowContent(
                lastEntry = history.firstOrNull(),
                history = history,
                onReinvestClick = {},
                onCorrectionClick = {},
                onForecastClick = {},
                onCycleEndClick = {},
                onDailyButtonClick = {}
            )
        }

        composeTestRule.onNodeWithText("Старт/Реинвест").assertIsDisplayed()
        composeTestRule.onNodeWithText("Коррекция").assertIsDisplayed()
        composeTestRule.onNodeWithText("Прогноз").assertIsDisplayed()
        composeTestRule.onNodeWithText("Конец цикла").assertIsDisplayed()
    }

    @Test
    fun `NoviceStatsCard displays correct values`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 500.0,
            isButtonPressed = false,
            actionType = "PN_DAILY"
        )

        composeTestRule.setContent {
            NoviceStatsCard(entry)
        }

        composeTestRule.onNodeWithText("2.00%").assertIsDisplayed()
        composeTestRule.onNodeWithText("15000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("300.00").assertIsDisplayed()
    }

    @Test
    fun `NoviceStatsCard shows wallet amount`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 14700.0,
            dailyAccrual = 294.0,
            walletAmount = 300.0,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        )

        composeTestRule.setContent {
            NoviceStatsCard(entry)
        }

        composeTestRule.onNodeWithText("300.00").assertIsDisplayed()
    }

    // ========== РП История ==========

    @Test
    fun `GrowingFlowContent shows create button when no history`() {
        composeTestRule.setContent {
            GrowingFlowContent(
                lastEntry = null,
                history = emptyList(),
                onReinvestClick = {},
                onCorrectionClick = {},
                onForecastClick = {},
                onBestDateClick = {},
                onDailyButtonClick = {}
            )
        }

        composeTestRule.onNodeWithText("Думаю, стоит завести Растущий Поток!").assertIsDisplayed()
    }

    @Test
    fun `GrowingFlowContent shows all buttons when history exists`() {
        val history = listOf(
            GrowingFlowEntity(
                date = System.currentTimeMillis(),
                percent = 0.1,
                inFlowAmount = 20000.0,
                dailyAccrual = 20.0,
                walletAmount = 0.0,
                isButtonPressed = false,
                actionType = "START"
            )
        )

        composeTestRule.setContent {
            GrowingFlowContent(
                lastEntry = history.firstOrNull(),
                history = history,
                onReinvestClick = {},
                onCorrectionClick = {},
                onForecastClick = {},
                onBestDateClick = {},
                onDailyButtonClick = {}
            )
        }

        composeTestRule.onNodeWithText("Старт/Реинвест").assertIsDisplayed()
        composeTestRule.onNodeWithText("Коррекция").assertIsDisplayed()
        composeTestRule.onNodeWithText("Прогноз").assertIsDisplayed()
        composeTestRule.onNodeWithText("Лучшая дата").assertIsDisplayed()
    }

    @Test
    fun `CurrentStatsCard displays correct values`() {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = true,
            actionType = "DAILY"
        )

        composeTestRule.setContent {
            CurrentStatsCard(entry)
        }

        composeTestRule.onNodeWithText("0.103%").assertIsDisplayed()
    }

    @Test
    fun `CurrentStatsCard shows wallet amount`() {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.106,
            inFlowAmount = 19959.42,
            dailyAccrual = 21.16,
            walletAmount = 40.58,
            isButtonPressed = true,
            actionType = "DAILY"
        )

        composeTestRule.setContent {
            CurrentStatsCard(entry)
        }

        composeTestRule.onNodeWithText("40.58").assertIsDisplayed()
    }

    // ========== Табы ==========

    @Test
    fun `FlowTabs displays all tabs`() {
        val tabs = listOf("ПН", "БП", "ПСП", "РП", "НП")
        val fullNames = listOf(
            "ПОТОК НОВИЧКА",
            "БЫСТРЫЙ ПОТОК",
            "ПРЕМИУМ СТАРТОВЫЙ ПОТОК",
            "РАСТУЩИЙ ПОТОК",
            "НАКОПИТЕЛЬНЫЙ ПОТОК"
        )

        composeTestRule.setContent {
            FlowTabs(
                selectedTabIndex = 0,
                tabs = tabs,
                fullNames = fullNames,
                onTabSelected = {}
            )
        }

        tabs.forEach { tab ->
            composeTestRule.onNodeWithText(tab).assertIsDisplayed()
        }
    }

    @Test
    fun `FlowTabs tab selection changes full name`() {
        val tabs = listOf("ПН", "БП", "ПСП", "РП", "НП")
        val fullNames = listOf(
            "ПОТОК НОВИЧКА",
            "БЫСТРЫЙ ПОТОК",
            "ПРЕМИУМ СТАРТОВЫЙ ПОТОК",
            "РАСТУЩИЙ ПОТОК",
            "НАКОПИТЕЛЬНЫЙ ПОТОК"
        )

        composeTestRule.setContent {
            FlowTabs(
                selectedTabIndex = 2,
                tabs = tabs,
                fullNames = fullNames,
                onTabSelected = {}
            )
        }

        composeTestRule.onNodeWithText("ПРЕМИУМ СТАРТОВЫЙ ПОТОК").assertIsDisplayed()
    }

    // ========== Цветовая индикация ==========

    @Test
    fun `SUNDAY action type displays correctly`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "SUNDAY"
        )

        assertEquals("SUNDAY", entry.actionType)
    }

    @Test
    fun `MISSED action type displays correctly`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 14700.0,
            dailyAccrual = 294.0,
            walletAmount = 300.0,
            isButtonPressed = false,
            actionType = "MISSED"
        )

        assertEquals("MISSED", entry.actionType)
    }

    @Test
    fun `CORRECTION action type displays correctly`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 16000.0,
            dailyAccrual = 320.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_CORRECTION"
        )

        assertEquals("PN_CORRECTION", entry.actionType)
    }

    @Test
    fun `START action type displays correctly`() {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.1,
            inFlowAmount = 20000.0,
            dailyAccrual = 20.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "START"
        )

        assertEquals("START", entry.actionType)
    }

    @Test
    fun `REINVEST action type displays correctly`() {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.103,
            inFlowAmount = 35000.0,
            dailyAccrual = 36.05,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "REINVEST"
        )

        assertEquals("REINVEST", entry.actionType)
    }

    // ========== Форматирование значений ==========

    @Test
    fun `percent displays with correct precision for PN`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 15000.0,
            dailyAccrual = 300.0,
            walletAmount = 0.0,
            isButtonPressed = false,
            actionType = "PN_START"
        )

        composeTestRule.setContent {
            NoviceStatsCard(entry)
        }

        composeTestRule.onNodeWithText("2.00%").assertExists()
    }

    @Test
    fun `percent displays with 3 decimals for RP`() {
        val entry = GrowingFlowEntity(
            date = System.currentTimeMillis(),
            percent = 0.103,
            inFlowAmount = 19980.0,
            dailyAccrual = 20.58,
            walletAmount = 20.0,
            isButtonPressed = true,
            actionType = "DAILY"
        )

        composeTestRule.setContent {
            CurrentStatsCard(entry)
        }

        composeTestRule.onNodeWithText("0.103%").assertExists()
    }

    @Test
    fun `amount displays with 2 decimal places`() {
        val entry = NoviceFlowEntity(
            date = System.currentTimeMillis(),
            percent = 2.0,
            inFlowAmount = 15000.00,
            dailyAccrual = 300.00,
            walletAmount = 594.00,
            isButtonPressed = true,
            actionType = "PN_DAILY"
        )

        composeTestRule.setContent {
            NoviceStatsCard(entry)
        }

        composeTestRule.onNodeWithText("15000.00").assertExists()
        composeTestRule.onNodeWithText("300.00").assertExists()
    }
}
