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
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.FastFlowDayEntity
import com.flowhack.flowcapital.data.db.FastFlowEntity
import com.flowhack.flowcapital.data.db.FastFlowRepository
import com.flowhack.flowcapital.data.forecast.FAST_FLOW_TYPE_BP
import com.flowhack.flowcapital.data.forecast.FAST_FLOW_TYPE_SBP
import com.flowhack.flowcapital.data.forecast.calculateFastFlowCloseDate
import com.flowhack.flowcapital.data.forecast.getFastFlowDayCount
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import com.flowhack.flowcapital.ui.theme.FlowColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG_FAST_SCREEN = "FastFlowScreen"

/**
 * Основной экран вкладки "Быстрый Поток" (БП/СБП).
 *
 * Отображает информацию о созданных потоках, позволяет:
 * - Создавать новые потоки (через диалог [CreateFastFlowDialog])
 * - Нажимать ежедневную кнопку начисления
 * - Корректировать данные текущего потока ([CorrectionFastFlowDialog])
 * - Просматривать прогноз начислений ([ForecastFastFlowDialog])
 * - Удалять потоки
 *
 * Особенности:
 * - Поддерживается переключение между несколькими потоками (стрелками)
 * - Заголовок: "БП 19.08.2026" / "СБП 19.08.2026 #1"
 * - БП длится 30 дней, СБП — 15 дней
 */
@Composable
fun FastFlowScreen() {
    AppLogger.d(TAG_FAST_SCREEN, "Инициализация экрана БП/СБП")
    val dateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val flowRepository = remember {
        FastFlowRepository(database.fastFlowDao(), database.fastFlowDayDao())
    }
    val settingsManager = remember { SettingsManager(context) }

    val viewModel: FastFlowViewModel = viewModel(
        factory = FastFlowViewModelFactory(flowRepository, settingsManager)
    )

    // Генерация пропущенных дней при открытии вкладки и возобновлении приложения
    LaunchedEffect(Unit) {
        viewModel.generateMissedDaysForFastFlow()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.generateMissedDaysForFastFlow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val flows by viewModel.allFlows.collectAsState()
    val currentFlow by viewModel.currentFlow.collectAsState()
    val days by viewModel.days.collectAsState()
    val forecastResults by viewModel.forecastResults.collectAsState()
    val currentIndex by viewModel.currentFlowIndex.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showForecastDialog by remember { mutableStateOf(false) }

    val isFlowClosed = currentFlow?.isActive == false

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
                text = buildFlowTitle(flows, currentIndex, dateFormat),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FlowColors.BP_COLOR
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

        Spacer(modifier = Modifier.height(16.dp))

        if (currentFlow == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет активных БП/СБП", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = FlowColors.BP_COLOR)
                    ) {
                        Text("Создать поток")
                    }
                }
            }
        } else {
            currentFlow?.let { flow ->
                FastFlowInfoCard(flow)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isFlowClosed) {
                FastFlowButtonsRow(
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

            if (isFlowClosed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Поток закрыт. Всего начислено: ${String.format(Locale.US, "%.2f", currentFlow?.totalAccrued ?: 0.0)}",
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FlowColors.BP_COLOR)
                            ) { Text("Создать", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Удалить", fontSize = 12.sp) }
                        }
                    }
                }
            } else {
                FastDailyButton(
                    flow = currentFlow,
                    days = days,
                    onDailyButtonClick = { viewModel.pressDailyButton() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FastFlowHistoryTable(days)
        }
    }

    if (showCreateDialog) {
        CreateFastFlowDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { type, nominal, currentDay, startDate ->
                viewModel.createFlow(type, nominal, currentDay, startDate)
                showCreateDialog = false
            }
        )
    }

    if (showCorrectionDialog) {
        CorrectionFastFlowDialog(
            onDismiss = { showCorrectionDialog = false },
            onConfirm = { totalAccrued, dailyAccrual, currentDay ->
                viewModel.makeCorrection(totalAccrued, dailyAccrual, currentDay)
                showCorrectionDialog = false
            },
            currentTotalAccrued = currentFlow?.totalAccrued ?: 0.0,
            currentDailyAccrual = currentFlow?.dailyAccrual ?: 0.0,
            currentDay = currentFlow?.currentDay ?: 1,
            dayCount = currentFlow?.let { getFastFlowDayCount(it.type) } ?: 30
        )
    }

    if (showDeleteDialog) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
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
                    Text("Удалить поток?", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Text("Текущий поток будет удалён. Это действие нельзя отменить.")
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
        ForecastFastFlowDialog(
            days = forecastResults,
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearForecast()
                showForecastDialog = false
            }
        )
    }
}

/** Формирует заголовок потока: "БП 19.08.2026" / "СБП 19.08.2026 #1" */
private fun buildFlowTitle(
    flows: List<FastFlowEntity>,
    currentIndex: Int,
    dateFormat: SimpleDateFormat
): String {
    val flow = flows.getOrNull(currentIndex) ?: return "БП"
    val prefix = if (flow.type == FAST_FLOW_TYPE_BP) "БП" else "СБП"
    val dateStr = dateFormat.format(Date(flow.startDate))

    // Нумерация среди потоков того же типа, открытых в тот же календарный день
    val flowDayStart = startOfDayMillis(flow.startDate)
    val sameDayFlows = flows.filter {
        it.type == flow.type && startOfDayMillis(it.startDate) == flowDayStart
    }.sortedBy { it.id }
    val number = sameDayFlows.indexOfFirst { it.id == flow.id } + 1

    return if (sameDayFlows.size > 1) "$prefix $dateStr #$number" else "$prefix $dateStr"
}

/** Нормализует timestamp к началу календарного дня */
private fun startOfDayMillis(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Карточка инфо БП/СБП: номинал, текущий день, закрытие, начисление, процент, всего */
@Composable
fun FastFlowInfoCard(flow: FastFlowEntity) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val dayCount = getFastFlowDayCount(flow.type)
    val closeDate = calculateFastFlowCloseDate(System.currentTimeMillis(), flow.currentDay, flow.type)

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
                InfoItem("Текущий день", "${flow.currentDay}/$dayCount")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Закрытие", dateFormat.format(Date(closeDate)))
                InfoItem("Начисление", String.format(Locale.US, "%.2f₽", flow.dailyAccrual))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Процент", String.format(Locale.US, "%.2f", flow.percent))
                InfoItem("Всего начислено", String.format(Locale.US, "%.2f₽", flow.totalAccrued))
            }
        }
    }
}

@Composable
fun FastFlowButtonsRow(
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
                colors = ButtonDefaults.buttonColors(containerColor = FlowColors.BP_COLOR)
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

/** Ежедневная кнопка начисления БП/СБП */
@Composable
fun FastDailyButton(
    flow: FastFlowEntity?,
    days: List<FastFlowDayEntity>,
    onDailyButtonClick: () -> Unit
) {
    val isSunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val isPressedToday = days.any {
        it.isButtonPressed && isSameDayMillis(it.date, todayStart)
    }
    val isFlowZero = flow?.isActive == false

    val isButtonDisabled = isSunday || isPressedToday || isFlowZero

    val buttonContainerColor = when {
        isFlowZero -> Color(0xFF333333)
        isSunday -> Color(0xFF9C27B0)
        isPressedToday -> Color(0xFF444444)
        else -> FlowColors.BP_COLOR
    }

    Button(
        onClick = onDailyButtonClick,
        enabled = !isButtonDisabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF333333),
            disabledContentColor = Color.LightGray
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = when {
                isFlowZero -> "ПОТОК ЗАВЕРШЁН"
                isSunday -> "ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ"
                isPressedToday -> "НАЧИСЛЕНИЕ ВЫПОЛНЕНО"
                else -> "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun isSameDayMillis(millis: Long, todayStart: Long): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis == todayStart
}

/** Таблица истории БП/СБП: День, Дата, Начислено */
@Composable
fun FastFlowHistoryTable(days: List<FastFlowDayEntity>) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "История",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (days.isEmpty()) {
            Text("Нет записей", fontSize = 12.sp, color = Color.Gray)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("День", modifier = Modifier.weight(0.5f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("Дата", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("Начислено", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }

            days.sortedBy { it.dayNumber }.forEach { day ->
                val backgroundColor = when (day.actionType) {
                    "SUNDAY" -> Color(0x33E040FB)
                    "MISSED" -> Color(0x33FF9800)
                    "CORRECTION" -> Color(0x33FF5252)
                    "START", "DAILY" -> Color(0x334CAF50)
                    else -> Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        day.dayNumber.toString(),
                        modifier = Modifier.weight(0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        dateFormat.format(Date(day.date)),
                        modifier = Modifier.weight(1.2f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        String.format(Locale.US, "%.2f", day.accrualAmount),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = if (day.actionType == "SUNDAY") Color(0xFF9C27B0) else Color(0xFF4CAF50)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

/**
 * Диалог создания нового БП/СБП потока.
 * @param onDismiss закрытие без сохранения
 * @param onConfirm (тип, номинал, текущий день, дата старта)
 */
@Composable
fun CreateFastFlowDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int, Long) -> Unit
) {
    AppLogger.d(TAG_FAST_SCREEN, "Открыт диалог создания БП/СБП")
    var flowTypeIndex by remember { mutableFloatStateOf(0f) }
    val flowType = if (flowTypeIndex < 0.5f) FAST_FLOW_TYPE_BP else FAST_FLOW_TYPE_SBP
    var nominalText by remember { mutableStateOf("") }
    var currentDay by remember { mutableIntStateOf(1) }
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dayCount = getFastFlowDayCount(flowType)
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { startDate = it }
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                Text("Создать поток", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

                Text("Тип потока", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = flowTypeIndex,
                    onValueChange = { flowTypeIndex = it },
                    valueRange = 0f..1f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (flowType == FAST_FLOW_TYPE_BP) "БП (30 дней)" else "СБП (15 дней)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowColors.BP_COLOR,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Номинал", fontWeight = FontWeight.Bold)
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

                Text("Текущий день", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                // Разбиваем чипы выбора дня на ряды по 5 (как в ПСП)
                (1..dayCount).chunked(5).forEach { rowDays ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowDays.forEach { day ->
                                FilterChip(
                                    selected = currentDay == day,
                                    onClick = { currentDay = day },
                                    label = { Text("$day") }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Дата старта:", fontSize = 13.sp)
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(dateFormat.format(Date(startDate)), fontSize = 13.sp)
                    }
                }

                if (currentDay > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "История прошлых дней будет рассчитана автоматически с учётом воскресений",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val nominal = nominalText.replace(",", ".").toDoubleOrNull() ?: 0.0
                val isNominalValid = nominal > 0
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                }.timeInMillis
                val isStartDateInFuture = startDate > today
                val isFormValid = isNominalValid && !isStartDateInFuture

                if (isStartDateInFuture) {
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
                            Text(
                                "Дата старта не может быть больше текущей даты.",
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
                            onConfirm(flowType, nominal, currentDay, startDate)
                        },
                        enabled = isFormValid
                    ) { Text("Создать") }
                }
            }
        }
    }
}

/** Диалог корректировки БП/СБП: Всего начислено, Начисление, Текущий день */
@Composable
fun CorrectionFastFlowDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Int) -> Unit,
    currentTotalAccrued: Double,
    currentDailyAccrual: Double,
    currentDay: Int,
    dayCount: Int
) {
    var totalAccruedText by remember { mutableStateOf(currentTotalAccrued.toString()) }
    var dailyAccrualText by remember { mutableStateOf(currentDailyAccrual.toString()) }
    var currentDayText by remember { mutableStateOf(currentDay.toString()) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                Text("Корректировка потока", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                Text("Редактируются: Всего начислено, Начисление, Текущий день", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = totalAccruedText,
                    onValueChange = { totalAccruedText = it },
                    label = { Text("Всего начислено") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dailyAccrualText,
                    onValueChange = { dailyAccrualText = it },
                    label = { Text("Начисление") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentDayText,
                    onValueChange = { currentDayText = it },
                    label = { Text("Текущий день (1..$dayCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val total = totalAccruedText.replace(",", ".").toDoubleOrNull() ?: currentTotalAccrued
                        val accrual = dailyAccrualText.replace(",", ".").toDoubleOrNull() ?: currentDailyAccrual
                        val day = currentDayText.toIntOrNull() ?: currentDay
                        onConfirm(total, accrual, day)
                    }) { Text("Сохранить") }
                }
            }
        }
    }
}

/** Прогноз БП/СБП: оставшиеся дни, кнопка Excel */
@Composable
fun ForecastFastFlowDialog(
    days: List<FastFlowDayEntity>,
    viewModel: FastFlowViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { viewModel.exportForecastToExcel(context, it) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val tableFontSize = if (isLandscape) 12.sp else 10.sp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                    "Прогноз",
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
                    Text("День", modifier = Modifier.weight(0.6f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Дата", modifier = Modifier.weight(1f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Начислено", modifier = Modifier.weight(1f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(days) { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                                Text(day.dayNumber.toString(), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(dateFormat.format(Date(day.date)), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", day.accrualAmount),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center,
                                    color = if (day.actionType == "SUNDAY") Color(0xFF9C27B0) else Color(0xFF4CAF50)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val total = days.sumOf { it.accrualAmount }
                Text(
                    "Итого: ${String.format(Locale.US, "%.2f", total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = FlowColors.BP_COLOR
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("БП_Прогноз_${System.currentTimeMillis()}.xlsx") },
                        colors = ButtonDefaults.buttonColors(containerColor = FlowColors.BP_COLOR)
                    ) {
                        Text("Excel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Диалог результатов прогноза БП/СБП (используется в настройках).
 * Показывает таблицу прогноза и кнопку экспорта в Excel.
 *
 * @param title Заголовок диалога
 * @param forecastList Список записей прогноза
 * @param onDismiss Закрытие диалога
 * @param onExportToExcel Экспорт в Excel
 */
@Composable
fun FastForecastResultsDialog(
    title: String,
    forecastList: List<FastFlowDayEntity>,
    onDismiss: () -> Unit,
    onExportToExcel: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val tableFontSize = if (isLandscape) 12.sp else 10.sp

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
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
                    title,
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
                    Text("День", modifier = Modifier.weight(0.5f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Дата", modifier = Modifier.weight(1.2f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Начислено", modifier = Modifier.weight(1f), fontSize = tableFontSize, textAlign = TextAlign.Center)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(forecastList) { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                                Text(day.dayNumber.toString(), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                Text(dateFormat.format(Date(day.date)), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", day.accrualAmount),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center,
                                    color = if (day.actionType == "SUNDAY") Color(0xFF9C27B0) else Color(0xFF4CAF50)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val total = forecastList.sumOf { it.accrualAmount }
                Text(
                    "Итого: ${String.format(Locale.US, "%.2f", total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = FlowColors.BP_COLOR
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onExportToExcel,
                        colors = ButtonDefaults.buttonColors(containerColor = FlowColors.BP_COLOR)
                    ) {
                        Text("Excel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
