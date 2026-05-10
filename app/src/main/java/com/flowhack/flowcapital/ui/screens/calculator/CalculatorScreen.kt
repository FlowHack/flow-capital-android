@file:Suppress("SpellCheckingInspection")

package com.flowhack.flowcapital.ui.screens.calculator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.GrowingFlowRepository
import com.flowhack.flowcapital.data.db.NoviceFlowRepository
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Главный экран калькулятора потоков.
 * Содержит вкладки для выбора потока (ПН, БП, ПСП, РП, НП)
 * и отображает соответствующий контент для каждого потока.
 *
 * Для РП и ПН доступны:
 * - Старт/Реинвест
 * - Корректировка
 * - Прогноз
 * - Кнопка ежедневного начисления
 *
 * Поддерживает экспорт прогнозов в CSV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val growingRepository = remember { GrowingFlowRepository(database.growingFlowDao()) }
    val noviceRepository = remember { NoviceFlowRepository(database.noviceFlowDao()) }
    val settingsManager = remember { SettingsManager(context) }

    // Загружаем сохранённую вкладку расчётов или используем РП (индекс 3) [RU:Загружаем сохранённую вкладку расчётов или используем РП]
    val savedCalcTab by settingsManager.defaultCalcTabFlow.collectAsState(initial = 3)
    var selectedTabIndex by remember { mutableIntStateOf(savedCalcTab) }

    LaunchedEffect(savedCalcTab) {
        selectedTabIndex = savedCalcTab
    }

    val viewModel: FlowViewModel = viewModel(
        factory = FlowViewModelFactory(growingRepository, noviceRepository, settingsManager)
    )

    // Генерация пропущенных дней для РП и ПН при открытии вкладки или возобновлении приложения
    LaunchedEffect(selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> viewModel.generateMissedDaysForNoviceFlow() // ПН вкладка
            3 -> viewModel.generateMissedDaysForGrowingFlow() // РП вкладка
        }
    }
    
    // Также слушаем возобновление приложения (onResume) через Lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when (selectedTabIndex) {
                    0 -> viewModel.generateMissedDaysForNoviceFlow() // ПН вкладка
                    3 -> viewModel.generateMissedDaysForGrowingFlow() // РП вкладка
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val growingHistory by viewModel.growingHistory.collectAsState(initial = emptyList())
    val noviceHistory by viewModel.noviceHistory.collectAsState(initial = emptyList())

    val startPercent by settingsManager.startPercentFlow.collectAsState(initial = 0.1)
    val pnBonusPercent by settingsManager.pnBonusPercentFlow.collectAsState(initial = 50.0)
    val pnDailyPercent by settingsManager.pnDailyPercentFlow.collectAsState(initial = 2.0)
    val eCurrencyBonusPercent by settingsManager.eCurrencyBonusPercentState.collectAsState(initial = 0.0)

    // Краткие и полные названия вкладок
    val tabs = listOf("ПН", "БП", "ПСП", "РП", "НП")
    val fullNames = listOf(
        "ПОТОК НОВИЧКА",
        "БЫСТРЫЙ ПОТОК",
        "ПРЕМИУМ СТАРТОВЫЙ ПОТОК",
        "РАСТУЩИЙ ПОТОК",
        "НАКОПИТЕЛЬНЫЙ ПОТОК"
    )

    // Состояния видимости диалогов
    var showReinvestDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoviceReinvestDialog by remember { mutableStateOf(false) }
    var showNoviceCorrectionDialog by remember { mutableStateOf(false) }
    var showNoviceForecastConfig by remember { mutableStateOf(false) }

    // Данные прогнозов из ViewModel
    val forecastData by viewModel.forecastResults.collectAsState()
    val bestDateData by viewModel.bestDateForecast.collectAsState()
    val pnForecastData by viewModel.pnForecastResults.collectAsState()
    val pnCycleEndData by viewModel.pnCycleEndForecast.collectAsState()

    // Snackbar для ошибок ПН
    val snackbarHostState = remember { SnackbarHostState() }
    val pnReinvestError by viewModel.pnReinvestError.collectAsState()

    LaunchedEffect(pnReinvestError) {
        pnReinvestError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearPnReinvestError()
        }
    }

    // Лончер для экспорта Excel файлов
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            when {
                forecastData.isNotEmpty() -> {
                    viewModel.exportForecastToCSV(context, uri, forecastData)
                    viewModel.clearForecast()
                }
                bestDateData.isNotEmpty() -> {
                    viewModel.exportForecastToCSV(context, uri, bestDateData)
                    viewModel.clearBestDateForecast()
                }
                pnForecastData.isNotEmpty() -> {
                    viewModel.exportNoviceForecastToCSV(context, uri, pnForecastData)
                    viewModel.clearPnForecast()
                }
                pnCycleEndData.isNotEmpty() -> {
                    viewModel.exportNoviceForecastToCSV(context, uri, pnCycleEndData)
                    viewModel.clearPnCycleEndForecast()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
        FlowTabs(
            selectedTabIndex = selectedTabIndex,
            tabs = tabs,
            fullNames = fullNames,
            onTabSelected = { index -> selectedTabIndex = index }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Контент в зависимости от выбранной вкладки
        when (selectedTabIndex) {
            // Поток Новичка
            0 -> NoviceFlowContent(
                lastEntry = noviceHistory.firstOrNull(),
                history = noviceHistory,
                dailyPercent = pnDailyPercent,
                onReinvestClick = { showNoviceReinvestDialog = true },
                onCorrectionClick = { showNoviceCorrectionDialog = true },
                onForecastClick = { showNoviceForecastConfig = true },
                onCycleEndClick = { viewModel.generateNoviceCycleEndForecast() },
                onDailyButtonClick = { viewModel.pressNoviceButton() }
            )
            // Премиум Стартовый Поток
            2 -> PremiumStartScreen()
            // Растущий Поток
            3 -> GrowingFlowContent(
                lastEntry = growingHistory.firstOrNull(),
                history = growingHistory,
                onReinvestClick = { showReinvestDialog = true },
                onCorrectionClick = { showCorrectionDialog = true },
                onForecastClick = { showDatePicker = true },
                onBestDateClick = { viewModel.findBestReinvestDate() },
                onDailyButtonClick = { viewModel.pressDailyButton() }
            )
            // Заглушка для нереализованных потоков
            else -> PlaceholderFlowContent(fullNames[selectedTabIndex])
        }
    }

    // Диалоги РП
    if (showReinvestDialog) {
        ReinvestDialog(
            onDismiss = { showReinvestDialog = false },
            onConfirm = { amount, percent, wallet, isExistingFlow ->
                viewModel.addReinvestOrStart(amount, percent, wallet, isExistingFlow)
                showReinvestDialog = false
            },
            defaultPercent = startPercent,
            isNewFlow = growingHistory.isEmpty(),
            eCurrencyBonusPercent = eCurrencyBonusPercent,
            onAmountChanged = { newAmount ->
                val amountDouble = newAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                CoroutineScope(Dispatchers.Main).launch {
                    settingsManager.updateECurrencyBonusPercent(amountDouble)
                }
            }
        )
    }
    if (showCorrectionDialog) {
        val lastEntry = growingHistory.firstOrNull()
        CorrectionDialog(
            onDismiss = { showCorrectionDialog = false },
            onConfirm = { inFlow, accrual, wallet, isButtonPressed ->
                viewModel.makeCorrection(inFlow, accrual, wallet, isButtonPressed)
                showCorrectionDialog = false
            },
            currentInFlow = lastEntry?.inFlowAmount ?: 0.0,
            currentAccrual = lastEntry?.dailyAccrual ?: 0.0,
            currentWallet = lastEntry?.walletAmount ?: 0.0,
            currentButtonPressed = lastEntry?.isButtonPressed ?: false,
            currentPercent = lastEntry?.percent ?: 0.1
        )
    }
    if (showDatePicker) {
        ForecastDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDateMillis ->
                showDatePicker = false
                viewModel.generateForecast(selectedDateMillis)
            }
        )
    }

    // Диалоги результатов РП
    if (forecastData.isNotEmpty()) {
        GrowingForecastResultsDialog(
            title = "Прогноз начислений",
            forecastList = forecastData,
            onDismiss = { viewModel.clearForecast() },
            onExportToExcel = { exportLauncher.launch("РП_Прогноз_${System.currentTimeMillis()}.xlsx") }
        )
    }
    if (bestDateData.isNotEmpty()) {
        // Предпоследняя запись - это и есть лучший день
        val bestDayEntry = if (bestDateData.size >= 2) bestDateData[bestDateData.size - 2] else null
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val bestDateStr = if (bestDayEntry != null) dateFormat.format(java.util.Date(bestDayEntry.date)) else "Не найдено"
        GrowingForecastResultsDialog(
            title = "Лучшая дата: $bestDateStr",
            forecastList = bestDateData,
            onDismiss = { viewModel.clearBestDateForecast() },
            onExportToExcel = { exportLauncher.launch("РП_Лучшая_дата_${System.currentTimeMillis()}.xlsx") },
            isBestDateDialog = true
        )
    }

    // Диалоги ПН
    if (showNoviceReinvestDialog) {
        NoviceReinvestDialog(
            onDismiss = { showNoviceReinvestDialog = false },
            onConfirm = { inFlow, dailyAccrual, wallet ->
                viewModel.addToNoviceFlow(inFlow, dailyAccrual, wallet, true)
                showNoviceReinvestDialog = false
            },
            bonusPercent = pnBonusPercent,
            dailyPercent = pnDailyPercent,
            isNewFlow = noviceHistory.isEmpty(),
            currentInFlow = noviceHistory.firstOrNull()?.inFlowAmount ?: 0.0
        )
    }
    if (showNoviceCorrectionDialog) {
        val lastEntry = noviceHistory.firstOrNull()
        NoviceCorrectionDialog(
            onDismiss = { showNoviceCorrectionDialog = false },
            onConfirm = { inFlow, accrual, wallet, isButtonPressed ->
                viewModel.makeNoviceCorrection(inFlow, accrual, wallet, isButtonPressed)
                showNoviceCorrectionDialog = false
            },
            currentInFlow = lastEntry?.inFlowAmount ?: 0.0,
            currentDailyPercent = lastEntry?.percent ?: pnDailyPercent,
            currentWallet = lastEntry?.walletAmount ?: 0.0,
            currentButtonPressed = lastEntry?.isButtonPressed ?: false
        )
    }
    if (showNoviceForecastConfig) {
        NoviceForecastConfigDialog(
            onDismiss = { showNoviceForecastConfig = false },
            onConfirm = { selectedDateMillis, compoundInterest, reinvestAmount ->
                showNoviceForecastConfig = false
                viewModel.generateNoviceForecast(
                    targetDateMillis = selectedDateMillis,
                    compoundInterest = compoundInterest,
                    reinvestAmount = reinvestAmount,
                    bonusPercent = pnBonusPercent
                )
            }
        )
    }

    // Диалоги результатов ПН
    if (pnForecastData.isNotEmpty()) {
        NoviceForecastResultsDialog(
            title = "Прогноз Потока Новичка",
            forecastList = pnForecastData,
            onDismiss = { viewModel.clearPnForecast() },
            onExportToExcel = { exportLauncher.launch("ПН_Прогноз_${System.currentTimeMillis()}.xlsx") }
        )
    }
    if (pnCycleEndData.isNotEmpty()) {
        val lastDay = pnCycleEndData.lastOrNull()
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val endDateStr = if (lastDay != null) dateFormat.format(java.util.Date(lastDay.date)) else "Не определено"
        // Считаем только рабочие дни (без воскресений)
        val daysCount = pnCycleEndData.count { it.actionType != "SUNDAY" }
        NoviceForecastResultsDialog(
            title = "Конец цикла: $endDateStr ($daysCount дней)",
            forecastList = pnCycleEndData,
            onDismiss = { viewModel.clearPnCycleEndForecast() },
            onExportToExcel = { exportLauncher.launch("ПН_Конец_цикла_${System.currentTimeMillis()}.xlsx") }
        )
    }
    }
}
