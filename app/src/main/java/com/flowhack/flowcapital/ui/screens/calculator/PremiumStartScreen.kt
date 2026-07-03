@file:OptIn(ExperimentalMaterial3Api::class)

package com.flowhack.flowcapital.ui.screens.calculator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.PremiumStartFlowEntity
import com.flowhack.flowcapital.data.db.PremiumStartFlowRepository
import com.flowhack.flowcapital.data.db.PremiumStartPeriodEntity
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import com.flowhack.flowcapital.ui.theme.FlowColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG_PSP_SCREEN = "PremiumStartScreen"

/**
 * Основной экран вкладки "Премиум Стартовый Поток" (ПСП).
 *
 * Отображает информацию о созданных потоках, позволяет:
 * - Создавать новые потоки (через диалог [CreatePSPDialog])
 * - Делать взносы номинала ([ContributionDialog])
 * - Корректировать данные текущего потока ([CorrectionPSPDialog])
 * - Просматривать прогноз начислений ([ForecastPSPDialog])
 * - Удалять потоки
 *
 * Особенности:
 * - Поддерживается переключение между несколькими потоками (стрелками)
 * - Автоматически рассчитывается "Всего накапало" по всем потокам
 * - Потоки живут 20 периодов по 14 дней каждый
 */
@Composable
fun PremiumStartScreen() {
    AppLogger.d(TAG_PSP_SCREEN, "Инициализация экрана ПСП")
    val dateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val flowRepository = remember {
        PremiumStartFlowRepository(
            database.premiumStartFlowDao(),
            database.premiumStartPeriodDao()
        )
    }
    val settingsManager = remember { SettingsManager(context) }

    val viewModel: PremiumStartViewModel = viewModel(
        factory = PremiumStartViewModelFactory(flowRepository, settingsManager)
    )

    val flows by viewModel.allFlows.collectAsState()
    val currentFlow by viewModel.currentFlow.collectAsState()
    val currentPeriod by viewModel.currentPeriod.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val contributionHistory by viewModel.contributionHistory.collectAsState()
    val forecastResults by viewModel.forecastResults.collectAsState()
    val currentIndex by viewModel.currentFlowIndex.collectAsState()
    val totalAccrued by viewModel.totalAccruedAllFlows.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showContributionDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showForecastDialog by remember { mutableStateOf(false) }

    // Логирование состояния
    AppLogger.d(TAG_PSP_SCREEN, "Текущее состояние: потоков=${flows.size}, " +
            "текущий индекс=$currentIndex, есть поток=${currentFlow != null}")

    val isFlowClosed = currentFlow?.isActive == false
    val isLastPeriod = currentPeriod?.periodNumber == 20
    val periodIsCompleted = currentPeriod?.isContributionMade == true
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val periodEnd = currentPeriod?.endDate ?: 0L
    val isPeriodEndDateReached = today >= periodEnd
    // Для 20-го периода кнопка "ЗАКРЫТЬ ПОТОК" когда дата закрытия подошла (независимо от взноса)
    // Для остальных периодов - кнопка "СДЕЛАТЬ ВЗНОС" когда дата подошла и взнос не сделан
    val canClose = isLastPeriod && isPeriodEndDateReached
    val canContribute = !isLastPeriod && isPeriodEndDateReached && !periodIsCompleted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (currentIndex > 0) viewModel.selectFlow(currentIndex - 1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = if (currentIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Text(
                text = if (flows.isNotEmpty()) {
                    val flow = flows.getOrNull(currentIndex)
                    if (flow != null) {
                        "ПСП ${dateFormat.format(Date(flow.startDate))}"
                    } else {
                        "ПСП"
                    }
                } else "ПСП",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FlowColors.PSP_COLOR
            )

            IconButton(onClick = { if (currentIndex < flows.size - 1) viewModel.selectFlow(currentIndex + 1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Вперёд",
                    tint = if (currentIndex < flows.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Всего накапало: ${String.format(Locale.US, "%.2f", totalAccrued)}",
            fontSize = 14.sp,
            color = FlowColors.PSP_COLOR,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (currentFlow == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет активных ПСП", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = FlowColors.PSP_COLOR)
                    ) {
                        Text("Создать поток")
                    }
                }
            }
        } else {
            currentFlow?.let { flow ->
                currentPeriod?.let { period ->
                    PSPInfoCard(flow, period)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isFlowClosed) {
            PSPButtonsRow(
                onCreateClick = { showCreateDialog = true },
                onCorrectionClick = { showCorrectionDialog = true },
                onDeleteClick = { showDeleteDialog = true },
                onForecastClick = {
                    viewModel.generateForecast()
                    showForecastDialog = true
                }
            )
        }

            Spacer(modifier = Modifier.height(12.dp))

            val canContribute = currentPeriod?.let { period ->
                val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                val periodEnd = Calendar.getInstance().apply { timeInMillis = period.endDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                today >= periodEnd && !period.isContributionMade
            } ?: false

            if (isFlowClosed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Поток закрыт. Всего получено: ${String.format(Locale.US, "%.2f", currentFlow?.totalAccrued ?: 0.0)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FlowColors.PSP_COLOR)
                        ) {
                            Text("Создать", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Удалить", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { if (canClose) viewModel.closeCurrentFlow() else showContributionDialog = true },
                enabled = canContribute || canClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlowColors.PSP_COLOR,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor = Color.LightGray
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when {
                        canClose -> "ЗАКРЫТЬ ПОТОК"
                        periodIsCompleted -> "ВЗНОС СДЕЛАН"
                        canContribute -> "СДЕЛАТЬ ВЗНОС НОМИНАЛА"
                        else -> "ОЖИДАНИЕ ДАТЫ ВЗНОСА"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

            Spacer(modifier = Modifier.height(12.dp))

            PSPContributionHistory(contributionHistory)
        }
    }

    if (showCreateDialog) {
        CreatePSPDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { nominal, currentPeriod, firstPeriodStart, currentPeriodStart ->
                viewModel.createFlow(nominal, currentPeriod, firstPeriodStart, currentPeriodStart)
                showCreateDialog = false
            }
        )
    }

    if (showCorrectionDialog) {
        CorrectionPSPDialog(
            onDismiss = { showCorrectionDialog = false },
            onConfirm = { newTotalAccrued, newEndDate ->
                viewModel.correctTotalAccrued(newTotalAccrued)
                if (newEndDate != null) {
                    viewModel.correctPeriodEndDate(newEndDate)
                }
                showCorrectionDialog = false
            },
            currentTotalAccrued = currentFlow?.totalAccrued ?: 0.0,
            currentPeriod = currentPeriod
        )
    }

    if (showContributionDialog) {
        val period = currentPeriod
        ContributionDialog(
            accrualForPeriod = period?.accrualAmount ?: 0.0,
            onDismiss = { showContributionDialog = false },
            onConfirm = { piggyBank ->
                viewModel.makeContribution(piggyBank)
                showContributionDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        Dialog(
            onDismissRequest = { showDeleteDialog = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.8f else 0.9f)
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("Удалить ПСП?", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Text("Текущий ПСП будет удалён. Это действие нельзя отменить.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.deleteCurrentFlow()
                                showDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Удалить") }
                    }
                }
            }
        }
    }

    if (showForecastDialog && forecastResults.isNotEmpty()) {
        ForecastPSPDialog(
            periods = forecastResults,
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearForecast()
                showForecastDialog = false
            }
        )
    }
}

/** Карточка инфо ПСП: номинал, период, начисление, % */
@Composable
fun PSPInfoCard(flow: PremiumStartFlowEntity, period: PremiumStartPeriodEntity) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    val closeDateText = if (period.isContributionMade) {
        period.contributionDate?.let { dateFormat.format(Date(it)) } ?: "Взнос сделан"
    } else {
        dateFormat.format(Date(period.endDate))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Номинал", String.format(Locale.US, "%.0f₽", flow.nominalAmount))
                InfoItem("Период", "${period.periodNumber}/20")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Закрытие", closeDateText)
                InfoItem("Будет начислено", String.format(Locale.US, "%.2f₽", period.accrualAmount))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Процент", String.format(Locale.US, "%.2f", period.percent))
                InfoItem("Всего получено", String.format(Locale.US, "%.2f₽", flow.totalAccrued))
            }
        }
    }
}

@Composable
fun InfoItem(title: String, value: String) {
    Column {
        Text(title, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PSPButtonsRow(
    onCreateClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onForecastClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FlowColors.PSP_COLOR)
            ) { Text("Создать", fontSize = 11.sp) }
            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Удалить", fontSize = 11.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCorrectionClick,
                modifier = Modifier.weight(1f)
            ) { Text("Корректировать", fontSize = 11.sp) }
            OutlinedButton(
                onClick = onForecastClick,
                modifier = Modifier.weight(1f)
            ) { Text("Прогноз", fontSize = 11.sp) }
        }
    }
}

/**
 * История взносов Премиум Стартового Потока (ПСП).
 *
 * Отображает таблицу всех периодов, где был сделан взнос (isContributionMade = true).
 * Если история пуста, показывает соответствующее сообщение.
 *
 * Особенности:
 * - Заголовок "История взносов" по центру
 * - Колонки: Период, Дата, Начислено, %
 * - Цвет начисления - зелёный (0xFF4CAF50)
 * - Даты в формате dd.MM.yy
 * - Скрывается при ширине экрана < 600dp (isWideScreen)
 *
 * @param history Список периодов с выполненными взносами
 */
@Composable
fun PSPContributionHistory(history: List<PremiumStartPeriodEntity>) {
    AppLogger.d(TAG_PSP_SCREEN, "Отображение истории взносов: ${history.size} записей")
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("История взносов", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text("Нет взносов", fontSize = 12.sp, color = Color.Gray)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Пер.", modifier = Modifier.weight(0.5f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("Дата взноса", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("Начислено", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("%", modifier = Modifier.weight(0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }

            history.forEachIndexed { index, period ->
                val periodText = if (period.isContributionMade) {
                    "${period.periodNumber}"
                } else {
                    "${period.periodNumber - 1}"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        periodText,
                        modifier = Modifier.weight(0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        period.contributionDate?.let { dateFormat.format(Date(it)) } ?: "-",
                        modifier = Modifier.weight(1.2f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        String.format(Locale.US, "%.2f", period.accrualAmount),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        String.format(Locale.US, "%.2f", period.percent),
                        modifier = Modifier.weight(0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

/**
 * Диалог создания нового ПСП.
 * @param onDismiss закрытие без сохранения
 * @param onConfirm (номинал, период, дата1, датаТек)
 */
@Composable
fun CreatePSPDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Int, Long, Long?) -> Unit
) {
    AppLogger.d(TAG_PSP_SCREEN, "Открыт диалог создания ПСП")
    var nominalText by remember { mutableStateOf("5000") }
    var currentPeriod by remember { mutableIntStateOf(1) }
    var firstPeriodStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentPeriodStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showFirstDatePicker by remember { mutableStateOf(false) }
    var showCurrentDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    if (showFirstDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFirstDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { firstPeriodStart = it }
                    showFirstDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showFirstDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCurrentDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showCurrentDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { currentPeriodStart = it }
                    showCurrentDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showCurrentDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val wideScreen = isWideScreen()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.8f else 0.9f)
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Создать ПСП", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text("Номинал ПСП", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nominalText,
                    onValueChange = { nominalText = it },
                    label = { Text("Номинал") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Текущий период", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        (1..5).forEach { period ->
                            FilterChip(
                                selected = currentPeriod == period,
                                onClick = { currentPeriod = period },
                                label = { Text("$period") }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        (6..10).forEach { period ->
                            FilterChip(
                                selected = currentPeriod == period,
                                onClick = { currentPeriod = period },
                                label = { Text("$period") }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        (11..15).forEach { period ->
                            FilterChip(
                                selected = currentPeriod == period,
                                onClick = { currentPeriod = period },
                                label = { Text("$period") }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        (16..20).forEach { period ->
                            FilterChip(
                                selected = currentPeriod == period,
                                onClick = { currentPeriod = period },
                                label = { Text("$period") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Даты начала периодов", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Дата 1-го периода:", fontSize = 13.sp)
                    TextButton(onClick = { showFirstDatePicker = true }) {
                        Text(dateFormat.format(Date(firstPeriodStart)), fontSize = 13.sp)
                    }
                }

                if (currentPeriod > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Дата текущего периода:", fontSize = 13.sp)
                        TextButton(onClick = { showCurrentDatePicker = true }) {
                            Text(dateFormat.format(Date(currentPeriodStart)), fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Все периоды с 1-го по текущий будут созданы автоматически",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                val minDaysRequired = (currentPeriod - 1) * 14
                val daysBetween = if (currentPeriod > 1) {
                    val diff = currentPeriodStart - firstPeriodStart
                    (diff / (24 * 60 * 60 * 1000)).toInt()
                } else 0
                val isDateValid = currentPeriod == 1 || daysBetween >= minDaysRequired

                if (!isDateValid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ошибка в датах!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Между 1-м и ${currentPeriod}-м периодом должно быть минимум $minDaysRequired дней (по 14 дней на период). Сейчас: $daysBetween дней.",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Проверьте даты или напишите в техподдержку: dmitriy@flow-hack.ru",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Поток будет создан на 20 периодов (10 месяцев)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                val nominal = nominalText.replace(",", ".").toDoubleOrNull() ?: 0.0
                val isNominalValid = nominal > 0
                val minDaysRequired2 = (currentPeriod - 1) * 14
                val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                val isFirstDateInFuture = firstPeriodStart > today
                val isCurrentDateInFuture = currentPeriod > 1 && currentPeriodStart > today
                val daysBetween2 = if (currentPeriod > 1) {
                    val diff = currentPeriodStart - firstPeriodStart
                    (diff / (24 * 60 * 60 * 1000)).toInt()
                } else 0
                val isDateValid2 = currentPeriod == 1 || daysBetween2 >= minDaysRequired2
                val isFormValid = isNominalValid && isDateValid2 && !isFirstDateInFuture && !isCurrentDateInFuture

                if (isFirstDateInFuture || isCurrentDateInFuture) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ошибка в датах!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Даты не могут быть больше текущей даты.",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(nominal, currentPeriod, firstPeriodStart, if (currentPeriod > 1) currentPeriodStart else null)
                        },
                        enabled = isFormValid
                    ) { Text("Создать") }
                }
            }
        }
    }
}

@Composable
fun CorrectionPSPDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Long?) -> Unit,
    currentTotalAccrued: Double,
    currentPeriod: PremiumStartPeriodEntity?
) {
    var totalAccruedText by remember { mutableStateOf(currentTotalAccrued.toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val initialEndDate: Long = currentPeriod?.endDate ?: 0L
    var newEndDate by remember { mutableStateOf(initialEndDate) }

    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val hasEndDate = initialEndDate > 0
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    AppLogger.d(TAG_PSP_SCREEN, "Открыт диалог корректировки: totalAccrued=$currentTotalAccrued")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.8f else 0.8f)
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Корректировка ПСП", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text("Редактируется 'Всего получено' и дата закрытия периода", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = totalAccruedText,
                    onValueChange = { totalAccruedText = it },
                    label = { Text("Всего получено") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (hasEndDate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Дата закрытия текущего периода:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(dateFormat.format(Date(newEndDate)))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val amount = totalAccruedText.replace(",", ".").toDoubleOrNull() ?: currentTotalAccrued
                        onConfirm(amount, if (hasEndDate) newEndDate else null)
                    }) { Text("Сохранить") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = newEndDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { newEndDate = it }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Взнос номинала ПСП: toWalletAll или сумма в кошелёк */
@Composable
fun ContributionDialog(
    /** Начисление за период */
    accrualForPeriod: Double,
    /** Закрыть без сохранения */
    onDismiss: () -> Unit,
    /** Подтвердить взнос (сумма в кошелёк) */
    onConfirm: (Double) -> Unit
) {
    AppLogger.d(TAG_PSP_SCREEN, "Открыт диалог взноса: начисление=$accrualForPeriod")
    var toWalletAll by remember { mutableStateOf(true) }
    var piggyBankText by remember { mutableStateOf("") }
    val piggyBankAmount = piggyBankText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isPiggyBankError = !toWalletAll && piggyBankAmount > accrualForPeriod

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.8f else 0.8f)
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Взнос номинала", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = toWalletAll,
                        onCheckedChange = { toWalletAll = it }
                    )
                    Text("В кошелёк пойдёт всё начисление", fontSize = 14.sp)
                }
                if (!toWalletAll) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = piggyBankText,
                        onValueChange = { piggyBankText = it },
                        label = { Text("Сумма в кошелёк") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isPiggyBankError,
                        supportingText = if (isPiggyBankError) {
                            { Text("Сумма не может быть больше начисления (${String.format(Locale.US, "%.2f", accrualForPeriod)})", color = Color.Red) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    val amountToPass = if (toWalletAll) accrualForPeriod else piggyBankAmount
                    Button(
                        onClick = { onConfirm(amountToPass) },
                        enabled = !isPiggyBankError
                    ) { Text("Подтвердить") }
                }
            }
        }
    }
}

/** Прогноз ПСП: оставшиеся периоды, кнопки Закрыть/Excel */
@Composable
fun ForecastPSPDialog(
    /** Список периодов для прогноза */
    periods: List<PremiumStartPeriodEntity>,
    /** ViewModel для экспорта в Excel */
    viewModel: PremiumStartViewModel,
    /** Закрыть без сохранения */
    onDismiss: () -> Unit
) {
    AppLogger.d(TAG_PSP_SCREEN, "Открыт прогноз ПСП: ${periods.size} периодов")
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportForecastToExcel(context, it) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val wideScreen = isWideScreen()
    val tableFontSize = when {
        isLandscape -> 12.sp
        wideScreen -> 11.sp
        else -> 10.sp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.8f else 0.8f)
                .fillMaxHeight(0.9f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Прогноз ПСП",
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Период", modifier = Modifier.weight(0.6f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Дата", modifier = Modifier.weight(1f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Начислено", modifier = Modifier.weight(1f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(periods) { period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                                Text(
                                    "${period.periodNumber}/20",
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    dateFormat.format(Date(period.endDate)),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", period.accrualAmount),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val total = periods.sumOf { it.accrualAmount }
                Text(
                    "Итого: ${String.format(Locale.US, "%.2f", total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = FlowColors.PSP_COLOR
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("ПСП_Прогноз_${System.currentTimeMillis()}.xlsx") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Excel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
