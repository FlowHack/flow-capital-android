@file:Suppress("SpellCheckingInspection")

package com.flowhack.flowcapital.ui.screens.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.flowhack.flowcapital.BuildConfig
import com.flowhack.flowcapital.data.db.AppDatabase
import com.flowhack.flowcapital.data.db.GrowingFlowEntity
import com.flowhack.flowcapital.data.db.GrowingFlowRepository
import com.flowhack.flowcapital.data.db.NoviceFlowEntity
import com.flowhack.flowcapital.data.db.NoviceFlowRepository
import com.flowhack.flowcapital.data.db.PremiumStartFlowEntity
import com.flowhack.flowcapital.data.db.PremiumStartFlowRepository
import com.flowhack.flowcapital.data.db.PremiumStartPeriodEntity
import com.flowhack.flowcapital.data.forecast.PspForecastResult
import com.flowhack.flowcapital.data.forecast.calculateFlowForecast
import com.flowhack.flowcapital.data.forecast.calculateNoviceFlowForecast
import com.flowhack.flowcapital.data.forecast.calculatePspForecast
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.proxy.ProxyConfig
import com.flowhack.flowcapital.data.proxy.ProxyStatus
import com.flowhack.flowcapital.data.proxy.ProxyStorage
import com.flowhack.flowcapital.data.proxy.ProxyType
import com.flowhack.flowcapital.data.proxy.ProxyValidator
import com.flowhack.flowcapital.data.settings.SettingsManager
import com.flowhack.flowcapital.notifications.ReminderWorker
import com.flowhack.flowcapital.notifications.cancelAlarmReminder
import com.flowhack.flowcapital.notifications.rescheduleSavedReminders
import com.flowhack.flowcapital.notifications.scheduleAlarmReminder
import com.flowhack.flowcapital.notifications.scheduleDailyReminder
import com.flowhack.flowcapital.notifications.scheduleFinalReminder
import com.flowhack.flowcapital.ui.screens.calculator.GrowingForecastResultsDialog
import com.flowhack.flowcapital.ui.screens.calculator.NoviceForecastResultsDialog
import com.flowhack.flowcapital.ui.theme.FlowColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главный экран настроек приложения.
 * Содержит настройки для каждого типа потока с вкладками,
 * общие настройки (напоминания, вкладка по умолчанию),
 * секцию донатов и версию приложения.
 *
 * @param onOpenBrowserUrl Callback для открытия URL в браузере
 */
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenBrowserUrl: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    val savedDefaultTab by settingsManager.defaultCalcTabFlow.collectAsState(initial = 3)
    var selectedFlowTab by remember { mutableIntStateOf(savedDefaultTab) }

    LaunchedEffect(savedDefaultTab) {
        selectedFlowTab = savedDefaultTab
    }

    val isRpVip by settingsManager.isRpVipFlow.collectAsState(initial = false)
    val tabs = remember(isRpVip) {
        listOf("ПН", "БП", "ПСП", if (isRpVip) "РП VIP" else "РП", "НП")
    }
    val fullNames = remember(isRpVip) {
        listOf(
            "ПОТОК НОВИЧКА",
            "БЫСТРЫЙ ПОТОК",
            "ПРЕМИУМ СТАРТОВЫЙ ПОТОК",
            if (isRpVip) "РАСТУЩИЙ ПОТОК VIP" else "РАСТУЩИЙ ПОТОК",
            "НАКОПИТЕЛЬНЫЙ ПОТОК"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Настройки", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))

        // Вкладки потоков
        SettingsFlowTabs(
            selectedTabIndex = selectedFlowTab,
            tabs = tabs,
            fullNames = fullNames,
            onTabSelected = { selectedFlowTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Настройки конкретного потока
        when (selectedFlowTab) {
            0 -> NoviceFlowSettings(settingsManager = settingsManager, scope = scope)
            2 -> PremiumStartSettings(settingsManager = settingsManager, scope = scope)
            3 -> GrowingFlowSettings(settingsManager = settingsManager, scope = scope)
            else -> PlaceholderSettings(fullNames[selectedFlowTab])
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Блок донатов
        DonateSection(onOpenBrowserUrl = onOpenBrowserUrl)
        Spacer(modifier = Modifier.height(20.dp))

        // Общие настройки
        Text("ОБЩИЕ НАСТРОЙКИ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        // Переключатель темы
        ThemeSettings(settingsManager = settingsManager, scope = scope)
        Spacer(modifier = Modifier.height(20.dp))

        // Вкладка при входе по умолчанию
        DefaultEntryTabSettings(settingsManager = settingsManager, scope = scope)
        Spacer(modifier = Modifier.height(20.dp))

        // Вкладка расчётов по умолчанию
        DefaultCalcTabSettings(settingsManager = settingsManager, scope = scope, tabs = tabs)
        Spacer(modifier = Modifier.height(20.dp))
        NotificationsSettings(context = context, settingsManager = settingsManager, scope = scope)

        Spacer(modifier = Modifier.height(20.dp))
        ProxySettingsCard(scope = scope)

        Spacer(modifier = Modifier.height(20.dp))
        ImportExportSettingsCard(scope = scope, settingsManager = settingsManager)

        Spacer(modifier = Modifier.height(32.dp))
        UpdateCheckerCard()
        Spacer(modifier = Modifier.height(12.dp))
        SupportSection()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Версия: ${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Компонент вкладок для настроек потоков.
 *
 * @param selectedTabIndex Индекс выбранной вкладки
 * @param tabs Краткие названия вкладок
 * @param fullNames Полные названия вкладок
 * @param onTabSelected Callback выбора вкладки
 */
@Composable
fun SettingsFlowTabs(
    selectedTabIndex: Int,
    tabs: List<String>,
    fullNames: List<String>,
    onTabSelected: (Int) -> Unit
) {
    val flowColor = FlowColors.getColorForIndex(selectedTabIndex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index
                val tabColor = FlowColors.getColorForIndex(index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) tabColor else MaterialTheme.colorScheme.surface)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = fullNames[selectedTabIndex],
            color = flowColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * Переключатель темы приложения.
 * Светлая/Тёмная тема с сохранением в DataStore.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun ThemeSettings(settingsManager: SettingsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val darkTheme by settingsManager.darkThemeFlow.collectAsState(initial = true)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Тёмная тема", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("По умолчанию включена тёмная тема", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = darkTheme,
                onCheckedChange = { newVal ->
                    scope.launch { settingsManager.setDarkTheme(newVal) }
                }
            )
        }
    }
}

/**
 * Настройки Растущего Потока.
 * Позволяет изменить стартовый % и ежедневный прирост, экспортировать и очистить данные.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun GrowingFlowSettings(
    settingsManager: SettingsManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val savedStartPercent by settingsManager.startPercentFlow.collectAsState(initial = 0.1)
    val savedDailyAddition by settingsManager.dailyAdditionFlow.collectAsState(initial = 0.003)

    var startPercentText by remember { mutableStateOf("") }
    var dailyAdditionText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCalculateDialog by remember { mutableStateOf(false) }
    var showCalculateExistingDialog by remember { mutableStateOf(false) }
    var ecExpanded by remember { mutableStateOf(false) }

    val savedECurrency by settingsManager.eCurrencyCoefficientsFlow.collectAsState(initial = emptyMap())
    var ecTextValues by remember { mutableStateOf(mapOf<Double, String>()) }

    val isMathChanged = remember(startPercentText, dailyAdditionText, savedStartPercent, savedDailyAddition) {
        startPercentText != savedStartPercent.toString() || dailyAdditionText != savedDailyAddition.toString()
    }

    val isECurrencyChanged = remember(ecTextValues, savedECurrency) {
        ecTextValues.any { (key, value) -> savedECurrency[key]?.toString() != value }
    }

    LaunchedEffect(savedStartPercent, savedDailyAddition) {
        startPercentText = savedStartPercent.toString()
        dailyAdditionText = savedDailyAddition.toString()
    }

    LaunchedEffect(savedECurrency) {
        ecTextValues = savedECurrency.mapValues { it.value.toString() }
    }

    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { GrowingFlowRepository(database.growingFlowDao()) }
    val historyList by repository.allHistory.collectAsState(initial = emptyList<GrowingFlowEntity>())
    val hasHistory = historyList.isNotEmpty()

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val history = repository.allHistory.first()
                exportGrowingFlowToExcel(context, uri, history)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Математика Растущего потока", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // РП VIP переключатель
            val isRpVip by settingsManager.isRpVipFlow.collectAsState(initial = false)
            var showRpVipToggleDialog by remember { mutableStateOf(false) }
            var pendingRpVip by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("РП VIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = if (isRpVip) "Стартовый: 0.3%, Ежедневный: 0.003%" else "Стартовый: 0.1%, Ежедневный: 0.003%",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isRpVip,
                    onCheckedChange = { checked ->
                        pendingRpVip = checked
                        showRpVipToggleDialog = true
                    }
                )
            }

            if (showRpVipToggleDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(if (pendingRpVip) "Включить РП VIP?" else "Выключить РП VIP?") },
                    text = {
                        Text("История Растущего потока будет очищена, коэффициенты сброшены на дефолтные для выбранного режима. Продолжить?")
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                settingsManager.setRpVip(pendingRpVip)
                                repository.clearHistory()
                                Toast.makeText(context, if (pendingRpVip) "РП VIP включён" else "РП VIP выключен", Toast.LENGTH_SHORT).show()
                            }
                            showRpVipToggleDialog = false
                        }) { Text("Продолжить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRpVipToggleDialog = false }) { Text("Отмена") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Стартовый и добавочный проценты", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = startPercentText, onValueChange = { startPercentText = it },
                label = { Text("Стартовый %") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = dailyAdditionText, onValueChange = { dailyAdditionText = it },
                label = { Text("Добавочный % в день") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch { settingsManager.savePercentages(startPercentText.toDoubleOrNull() ?: 0.1, dailyAdditionText.toDoubleOrNull() ?: 0.003) }
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                },
                enabled = isMathChanged,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Сохранить %") }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Коэффициенты E-currency", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Нажмите для редактирования", fontSize = 12.sp, color = Color.Gray)
                TextButton(onClick = { ecExpanded = !ecExpanded }) {
                    Text(if (ecExpanded) "Свернуть" else "Развернуть")
                }
            }

            if (ecExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                ecTextValues.keys.sorted().forEach { threshold ->
                    OutlinedTextField(
                        value = ecTextValues[threshold] ?: "",
                        onValueChange = { newValue ->
                            ecTextValues = ecTextValues.toMutableMap().apply { this[threshold] = newValue }
                        },
                        label = { Text("От ${String.format(java.util.Locale.US, "%.0f", threshold)}") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val coefficients = ecTextValues.mapValues { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
                        scope.launch { settingsManager.saveECurrencyCoefficients(coefficients) }
                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                    },
                    enabled = isECurrencyChanged,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить коэффициенты") }
            }

            Text("Данные", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            GrowingExportButton(hasData = hasHistory, onExport = { exportLauncher.launch("РП_История_${System.currentTimeMillis()}.xlsx") })
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showCalculateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать поток") }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showCalculateExistingDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать действующий поток") }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить данные") }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Очистить данные?") },
            text = { Text("Вся история Растущего потока будет удалена. Это действие нельзя отменить.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.clearHistory()
                        Toast.makeText(context, "Данные очищены", Toast.LENGTH_SHORT).show()
                    }
                    showClearDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Отмена") } }
        )
    }

    if (showCalculateDialog) {
        CalculateGrowingFlowDialog(
            settingsManager = settingsManager,
            onDismiss = { showCalculateDialog = false }
        )
    }

    if (showCalculateExistingDialog) {
        CalculateExistingGrowingFlowDialog(
            settingsManager = settingsManager,
            onDismiss = { showCalculateExistingDialog = false }
        )
    }

}

@Composable
private fun GrowingExportButton(hasData: Boolean, onExport: () -> Unit) {
    var dataExists by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        val repo = GrowingFlowRepository(db.growingFlowDao())
        repo.allHistory.collect { list ->
            dataExists = list.isNotEmpty()
        }
    }

    OutlinedButton(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth(),
        enabled = dataExists
    ) { Text("Выгрузить в Excel") }
}

/** Экспорт истории РП в Excel с использованием FastExcel с цветами */
private suspend fun exportGrowingFlowToExcel(context: Context, uri: Uri, history: List<GrowingFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "История РП", null)
                val worksheet = workbook.newWorksheet("РП")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "Процент")
                worksheet.value(0, 3, "В потоке")
                worksheet.value(0, 4, "Начисление")
                worksheet.value(0, 5, "Кошелек")
                worksheet.value(0, 6, "Действие")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                history.reversed().forEachIndexed { index, entry ->
                    val isActiveAction = entry.actionType in listOf("START", "REINVEST", "DAILY")
                    if (isActiveAction) {
                        currentStep++
                    }
                    val stepDisplay = if (isActiveAction) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.percent)
                    worksheet.value(row, 3, entry.inFlowAmount)
                    worksheet.value(row, 4, entry.dailyAccrual)
                    worksheet.value(row, 5, entry.walletAmount)
                    worksheet.value(row, 6, entry.actionType)
                    val fillColor = when (entry.actionType) {
                        "START", "REINVEST" -> "C8FFC8"
                        "CORRECTION" -> "FFC8C8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..6).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = history.size
                worksheet.range(0, 0, lastRow, 6).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("Export", "Ошибка экспорта GrowingFlow", e) }
    }
}

/**
 * Настройки Потока Новичка.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun NoviceFlowSettings(
    settingsManager: SettingsManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val savedBonusPercent by settingsManager.pnBonusPercentFlow.collectAsState(initial = 50.0)
    val savedDailyPercent by settingsManager.pnDailyPercentFlow.collectAsState(initial = 2.0)

    var bonusPercentText by remember { mutableStateOf("") }
    var dailyPercentText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCalculateDialog by remember { mutableStateOf(false) }
    var showCalculateExistingDialog by remember { mutableStateOf(false) }

    val isMathChanged = remember(bonusPercentText, dailyPercentText, savedBonusPercent, savedDailyPercent) {
        bonusPercentText != savedBonusPercent.toString() || dailyPercentText != savedDailyPercent.toString()
    }

    LaunchedEffect(savedBonusPercent, savedDailyPercent) {
        bonusPercentText = savedBonusPercent.toString()
        dailyPercentText = savedDailyPercent.toString()
    }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val database = AppDatabase.getDatabase(context)
                val repository = NoviceFlowRepository(database.noviceFlowDao())
                val history = repository.allHistory.first()
                exportNoviceFlowToExcel(context, uri, history)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var hasNoviceHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        val repo = NoviceFlowRepository(db.noviceFlowDao())
        repo.allHistory.collect { list ->
            hasNoviceHistory = list.isNotEmpty()
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Математика Потока Новичка", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = bonusPercentText, onValueChange = { bonusPercentText = it },
                label = { Text("Бонус при взносе (%)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = { Text("Сумма + бонус = сумма в потоке") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = dailyPercentText, onValueChange = { dailyPercentText = it },
                label = { Text("Дневной %") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = { Text("% от суммы в потоке ежедневно") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch { settingsManager.savePnPercentages(bonusPercentText.toDoubleOrNull() ?: 50.0, dailyPercentText.toDoubleOrNull() ?: 2.0) }
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                },
                enabled = isMathChanged,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Сохранить %") }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Данные", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { exportLauncher.launch("ПН_История_${System.currentTimeMillis()}.xlsx") },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasNoviceHistory
            ) { Text("Выгрузить в Excel") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showCalculateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать поток") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showCalculateExistingDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать действующий поток") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить данные") }
        }
    }

    if (showCalculateDialog) {
        CalculateNoviceFlowDialog(
            settingsManager = settingsManager,
            onDismiss = { showCalculateDialog = false }
        )
    }

    if (showCalculateExistingDialog) {
        CalculateExistingNoviceFlowDialog(
            settingsManager = settingsManager,
            onDismiss = { showCalculateExistingDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Очистить данные?") },
            text = { Text("Вся история Потока Новичка будет удалена. Это действие нельзя отменить.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val database = AppDatabase.getDatabase(context)
                        val repository = NoviceFlowRepository(database.noviceFlowDao())
                        repository.clearHistory()
                        Toast.makeText(context, "Данные очищены", Toast.LENGTH_SHORT).show()
                    }
                    showClearDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Отмена") } }
        )
    }
}

/** Экспорт истории ПН в Excel с использованием FastExcel с цветами */
private suspend fun exportNoviceFlowToExcel(context: Context, uri: Uri, history: List<NoviceFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "История ПН", null)
                val worksheet = workbook.newWorksheet("ПН")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "В потоке")
                worksheet.value(0, 3, "Начисление")
                worksheet.value(0, 4, "Кошелек")
                worksheet.value(0, 5, "Действие")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                history.reversed().forEachIndexed { index, entry ->
                    val isActiveAction = entry.actionType in listOf("START", "REINVEST", "DAILY", "PN_START", "PN_REINVEST", "PN_DAILY")
                    if (isActiveAction) {
                        currentStep++
                    }
                    val stepDisplay = if (isActiveAction) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.inFlowAmount)
                    worksheet.value(row, 3, entry.dailyAccrual)
                    worksheet.value(row, 4, entry.walletAmount)
                    worksheet.value(row, 5, entry.actionType)
                    val fillColor = when (entry.actionType) {
                        "START", "REINVEST" -> "C8FFC8"
                        "CORRECTION" -> "FFC8C8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..5).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = history.size
                worksheet.range(0, 0, lastRow, 5).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("Export", "Ошибка экспорта NoviceFlow", e) }
    }
}

/**
 * Настройки ПСП.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun PremiumStartSettings(settingsManager: SettingsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showCalculateDialog by remember { mutableStateOf(false) }
    var pspExpanded by remember { mutableStateOf(false) }

    val savedPsp by settingsManager.pspCoefficientsFlow.collectAsState(initial = null)
    var pspTextValues by remember { mutableStateOf(mapOf<Int, String>()) }

    val pspCoeffs = savedPsp
    val isPspChanged = remember(pspTextValues, pspCoeffs) {
        if (pspCoeffs == null) false
        else pspTextValues.any { (key, value) -> pspCoeffs[key]?.toString() != value }
    }

    LaunchedEffect(pspCoeffs) {
        if (pspCoeffs != null) {
            pspTextValues = pspCoeffs.mapValues { it.value.toString() }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val database = AppDatabase.getDatabase(context)
                val flowDao = database.premiumStartFlowDao()
                val periodDao = database.premiumStartPeriodDao()
                val flows = flowDao.getAllFlows().first()
                exportPspToExcel(context, uri, flows, periodDao)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var hasPspFlows by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        val flowDao = db.premiumStartFlowDao()
        flowDao.getAllFlows().collect { flows ->
            hasPspFlows = flows.isNotEmpty()
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Коэффициенты периодов ПСП", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Нажмите для редактирования", fontSize = 12.sp, color = Color.Gray)
                TextButton(onClick = { pspExpanded = !pspExpanded }) {
                    Text(if (pspExpanded) "Свернуть" else "Развернуть")
                }
            }

            if (pspExpanded && savedPsp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                (1..20).forEach { period ->
                    OutlinedTextField(
                        value = pspTextValues[period] ?: "",
                        onValueChange = { newValue ->
                            pspTextValues = pspTextValues.toMutableMap().apply { this[period] = newValue }
                        },
                        label = { Text("Период $period") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val coefficients = pspTextValues.mapValues { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
                        scope.launch { settingsManager.savePspCoefficients(coefficients) }
                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                    },
                    enabled = isPspChanged,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить коэффициенты") }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Данные ПСП", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { exportLauncher.launch("ПСП_Все_${System.currentTimeMillis()}.xlsx") },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasPspFlows
            ) { Text("Выгрузить всё в Excel") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showCalculateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать поток") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить все ПСП") }
        }
    }

    if (showCalculateDialog) {
        CalculatePspDialog(
            settingsManager = settingsManager,
            onDismiss = { showCalculateDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Очистить все ПСП?") },
            text = { Text("Все данные ПСП будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val database = AppDatabase.getDatabase(context)
                        val flowDao = database.premiumStartFlowDao()
                        val periodDao = database.premiumStartPeriodDao()
                        val flowRepository = PremiumStartFlowRepository(flowDao, periodDao)
                        flowRepository.clearAll()
                        Toast.makeText(context, "Все ПСП очищены", Toast.LENGTH_SHORT).show()
                    }
                    showClearDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Отмена") } }
        )
    }
}

/**
 * Диалог редактирования коэффициентов периодов ПСП.
 *
 * @param onDismiss Закрытие диалога
 */
@Composable
fun PspCoefficientsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val pspCoefficients by settingsManager.pspCoefficientsFlow.collectAsState(initial = null)

    val coefficients = pspCoefficients

    if (coefficients == null) {
        LaunchedEffect(Unit) {
            settingsManager.refreshPspCacheSync()
        }
        return
    }

    var textValues by remember(coefficients) { mutableStateOf(coefficients.mapValues { it.value.toString() }) }

    LaunchedEffect(coefficients) {
        textValues = coefficients.mapValues { it.value.toString() }
    }

    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = { Text("Коэффициенты периодов ПСП", fontSize = 18.sp) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Период", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Коэффициент %", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                (1..20).forEach { period ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$period", modifier = Modifier.weight(0.3f), fontSize = 14.sp)
                        OutlinedTextField(
                            value = textValues[period] ?: "",
                            onValueChange = { value ->
                                textValues = textValues.toMutableMap().apply { put(period, value) }
                            },
                            modifier = Modifier.weight(0.7f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalCoefficients = textValues.mapValues { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
                scope.launch {
                    settingsManager.savePspCoefficients(finalCoefficients)
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Таблица коэффициентов периодов ПСП по умолчанию */
private fun getDefaultPspCoefficients(): Map<Int, Double> = mapOf(
    1 to 30.0, 2 to 55.8, 3 to 78.0, 4 to 97.07,
    5 to 113.48, 6 to 127.59, 7 to 139.73, 8 to 150.17,
    9 to 159.14, 10 to 166.86, 11 to 173.5, 12 to 179.21,
    13 to 184.12, 14 to 188.35, 15 to 191.97, 16 to 195.1,
    17 to 197.79, 18 to 198.0, 19 to 199.0, 20 to 200.0
)

/** Таблица коэффициентов E-currency для РП по умолчанию */
private fun getDefaultECurrencyCoefficients(): Map<Double, Double> = mapOf(
    1000.0 to 50.0,
    5000.0 to 75.0,
    10000.0 to 100.0,
    50000.0 to 125.0,
    100000.0 to 150.0,
    500000.0 to 175.0,
    1000000.0 to 200.0
)

@Composable
fun ECurrencyCoefficientsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val eCurrencyCoefficients by settingsManager.eCurrencyCoefficientsFlow.collectAsState(initial = emptyMap())
    if (eCurrencyCoefficients.isEmpty()) return
    var textValues by remember(eCurrencyCoefficients) { mutableStateOf(eCurrencyCoefficients.mapValues { it.value.toString() }) }

    LaunchedEffect(eCurrencyCoefficients) {
        textValues = eCurrencyCoefficients.mapValues { it.value.toString() }
    }

    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = { Text("Коэффициенты E-currency РП", fontSize = 18.sp) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Сумма от", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Бонус %", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                eCurrencyCoefficients.entries.sortedBy { it.key }.forEach { (threshold, _) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${String.format(Locale.US, "%.0f", threshold)}", modifier = Modifier.weight(0.5f), fontSize = 14.sp)
                        OutlinedTextField(
                            value = textValues[threshold] ?: "",
                            onValueChange = { value ->
                                textValues = textValues.toMutableMap().apply { put(threshold, value) }
                            },
                            modifier = Modifier.weight(0.5f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalCoefficients = textValues.mapValues { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }
                scope.launch {
                    settingsManager.saveECurrencyCoefficients(finalCoefficients)
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Экспорт всех ПСП в Excel с использованием FastExcel - каждый поток на отдельном листе */
private suspend fun exportPspToExcel(
    context: Context,
    uri: Uri,
    flows: List<PremiumStartFlowEntity>,
    periodDao: com.flowhack.flowcapital.data.db.PremiumStartPeriodDao
) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Все ПСП", null)
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

                flows.forEachIndexed { index, flow ->
                    val dateStr = dateFormat.format(Date(flow.startDate))
                    val worksheetName = if (index == 0) "ПСП_$dateStr" else "ПСП${index + 1}_$dateStr"
                    val worksheet = workbook.newWorksheet(worksheetName)
                    var currentRow = 0

                    worksheet.value(currentRow, 0, "=== ПСП ${index + 1} ($dateStr) ===")
                    currentRow++

                    worksheet.value(currentRow, 0, "Номинал")
                    worksheet.value(currentRow, 1, flow.nominalAmount)
                    currentRow++

                    worksheet.value(currentRow, 0, "Текущий период")
                    worksheet.value(currentRow, 1, "${flow.currentPeriod}/20")
                    currentRow++

                    worksheet.value(currentRow, 0, "Всего получено")
                    worksheet.value(currentRow, 1, flow.totalAccrued)
                    currentRow += 2

                    worksheet.value(currentRow, 0, "Период")
                    worksheet.value(currentRow, 1, "Процент")
                    worksheet.value(currentRow, 2, "Начисление")
                    worksheet.value(currentRow, 3, "Дата начала")
                    worksheet.value(currentRow, 4, "Дата окончания")
                    worksheet.value(currentRow, 5, "Взнос сделан")
                    worksheet.value(currentRow, 6, "Дата взноса")
                    currentRow++

                    val periods = periodDao.getPeriodsByFlowId(flow.id).first()
                    periods.forEach { period ->
                        worksheet.value(currentRow, 0, period.periodNumber)
                        worksheet.value(currentRow, 1, period.percent)
                        worksheet.value(currentRow, 2, period.accrualAmount)
                        worksheet.value(currentRow, 3, dateFormat.format(Date(period.startDate)))
                        worksheet.value(currentRow, 4, dateFormat.format(Date(period.endDate)))
                        worksheet.value(currentRow, 5, if (period.isContributionMade) "Да" else "Нет")
                        worksheet.value(currentRow, 6, period.contributionDate?.let { dateFormat.format(Date(it)) } ?: "-")
                        currentRow++
                    }
                    currentRow += 2

                    val totalAll = flows.sumOf { it.totalAccrued }
                    worksheet.value(currentRow, 0, "ИТОГО ВО ВСЕХ ПСП")
                    worksheet.value(currentRow, 1, totalAll)
                    // Центрирование для текущего листа
                    val lastDataRow = currentRow
                    val lastDataCol = 6 // максимальная колонка на листе ПСП
                    worksheet.range(0, 0, lastDataRow, lastDataCol).style().horizontalAlignment("center").set()
                }
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("Export", "Ошибка экспорта PSP", e) }
    }
}

/**
 * Заглушка для нереализованных потоков.
 *
 * @param flowName Название потока
 */
@Composable
fun PlaceholderSettings(flowName: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "В разработке. Скоро будет!", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Выбор вкладки расчётов по умолчанию.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 * @param tabs Список вкладок
 */
@Composable
fun DefaultCalcTabSettings(settingsManager: SettingsManager, scope: kotlinx.coroutines.CoroutineScope, tabs: List<String>) {
    val savedDefaultTab by settingsManager.defaultCalcTabFlow.collectAsState(initial = 3)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Вкладка расчётов по умолчанию", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = savedDefaultTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) FlowColors.getColorForIndex(index) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { scope.launch { settingsManager.setDefaultCalcTab(index) } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tabName, 
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Эта вкладка будет открываться первой при входе в раздел Расчёты", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

/**
 * Выбор вкладки при входе в приложение.
 *
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun DefaultEntryTabSettings(settingsManager: SettingsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val savedEntryTab by settingsManager.defaultEntryTabFlow.collectAsState(initial = 1)

    val entryTabs = listOf(
        Triple("Браузер", Icons.Default.Language, 0),
        Triple("Расчёты", Icons.Default.AccountBalanceWallet, 1),
        Triple("Настройки", Icons.Default.Settings, 2)
    )

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Вкладка при входе по умолчанию", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                entryTabs.forEach { (name, icon, index) ->
                    val isSelected = savedEntryTab == index
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { scope.launch { settingsManager.setDefaultEntryTab(index) } }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Эта вкладка будет открываться первой при входе в приложение", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

/**
 * Настройки напоминаний.
 *
 * @param context Контекст приложения
 * @param settingsManager Менеджер настроек
 * @param scope Корутинный скоуп
 */
@Composable
fun NotificationsSettings(context: Context, settingsManager: SettingsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val savedReminders by settingsManager.remindersFlow.collectAsState(initial = emptySet())
    val alarmSet by settingsManager.alarmRemindersFlow.collectAsState(initial = emptySet())
    val sortedReminders = savedReminders.toList().sorted()
    val smartNotifications by settingsManager.smartNotificationsFlow
        .collectAsState(initial = false)
    var showSmartInfoDialog by remember { mutableStateOf(false) }

    val timePickerDialog = TimePickerDialog(context, { _, hour, min ->
        val timeString = String.format(java.util.Locale.US, "%02d:%02d", hour, min)
        scope.launch {
            settingsManager.addReminder(timeString)
            scheduleDailyReminder(context, hour, min, timeString)
            scheduleFinalReminder(context)
        }
    }, 10, 0, true)

    // Проверка разрешений
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val canScheduleExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Напоминания", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Переключатель умных уведомлений (учёт времени клика по кнопке)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Умные уведомления", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Учитывают время последнего клика по кнопке",
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { showSmartInfoDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Что такое умные уведомления",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Switch(
                    checked = smartNotifications,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setSmartNotifications(enabled) }
                    }
                )
            }

            if (showSmartInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showSmartInfoDialog = false },
                    title = { Text("Умные уведомления") },
                    text = {
                        Text(
                            "Умные уведомления учитывают время вашего последнего " +
                                "клика по кнопке. Напоминания и будильники не будут " +
                                "беспокоить вас раньше, чем кнопка станет активной " +
                                "на проекте (через 24 часа после прошлого клика)."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSmartInfoDialog = false }) { Text("Понятно") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Для своевременной доставки напоминаний и будильников отключите оптимизацию батареи. Для точного срабатывания будильников и показа экрана на заблокированном устройстве необходимы дополнительные разрешения.", fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { openBatterySettings(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Настроить работу в фоне", fontSize = 12.sp) }

            // Блок разрешения точных будильников
            if (!canScheduleExact) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Разрешить точные будильники", fontSize = 12.sp) }
            }

            if (!isIgnoringBattery) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Отключить оптимизацию батареи", fontSize = 12.sp) }
            }

            // Блок разрешения полноэкранных уведомлений (Android 14+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val canUseFullScreenIntent = notificationManager.canUseFullScreenIntent()
                if (!canUseFullScreenIntent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Разрешить полноэкранные уведомления", fontSize = 12.sp) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Напоминания (${sortedReminders.size}/5)", fontWeight = FontWeight.Bold)
                Button(onClick = { timePickerDialog.show() }, enabled = sortedReminders.size < 5) { Text("Добавить") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sortedReminders.isEmpty()) Text("Список пуст", fontSize = 12.sp, color = Color.Gray)

            sortedReminders.forEach { time ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(time, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = "Напоминание", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = time in alarmSet,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    settingsManager.updateAlarmModeForReminder(time, checked)
                                    val parts = time.split(":")
                                    val hour = parts[0].toIntOrNull() ?: return@launch
                                    val min = parts[1].toIntOrNull() ?: return@launch
                                    if (checked) {
                                        WorkManager.getInstance(context).cancelUniqueWork("potok_rem_$time")
                                        scheduleAlarmReminder(context, hour, min, time)
                                    } else {
                                        cancelAlarmReminder(context, time)
                                        scheduleDailyReminder(context, hour, min, time)
                                    }
                                    scheduleFinalReminder(context)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Alarm, contentDescription = "Будильник", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            scope.launch {
                                settingsManager.removeReminder(time)
                                WorkManager.getInstance(context).cancelUniqueWork("potok_rem_$time")
                                cancelAlarmReminder(context, time)
                                scheduleFinalReminder(context)
                            }
                        }) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text("В 23:00 сработает будильник, если по одному из потоков требуется действие, независимо от установленных напоминаний или будильников", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }
    }
}

private const val MAX_PROXIES = 3

@Composable
fun ProxySettingsCard(scope: CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    val proxyStorage = remember { ProxyStorage(context) }
    val savedProxies by proxyStorage.proxiesFlow.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<ProxyConfig?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var selectedProxyType by remember { mutableStateOf(ProxyType.SOCKS5) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    val isAddButtonEnabled = savedProxies.size < MAX_PROXIES

    LaunchedEffect(savedProxies) {
        AppLogger.log("ProxySettings", "Proxy list updated: ${savedProxies.size} proxies")
    }

    fun simulateProxyConnection(proxyId: String, onResult: (ProxyConfig) -> Unit) {
        scope.launch {
            kotlinx.coroutines.delay(2000)
            val proxy = savedProxies.find { it.id == proxyId }
            if (proxy == null) {
                onResult(ProxyConfig(
                    id = proxyId,
                    status = ProxyStatus.UNAVAILABLE
                ))
                return@launch
            }

            try {
                val startTime = System.currentTimeMillis()
                val url = URL("https://www.google.com")
                val connection = url.openConnection(proxy.toProxy()) as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = false

                try {
                    connection.connect()
                    val endTime = System.currentTimeMillis()
                    val ping = (endTime - startTime).toInt()

                    if (connection.responseCode in 200..399) {
                        val updatedProxy = proxy.copy(
                            status = ProxyStatus.CONNECTED,
                            pingMs = ping
                        )
                        onResult(updatedProxy)
                    } else {
                        val updatedProxy = proxy.copy(
                            status = ProxyStatus.UNAVAILABLE,
                            pingMs = null
                        )
                        onResult(updatedProxy)
                    }
                } catch (e: Exception) {
                    AppLogger.e("ProxySettings", "Ошибка подключения к прокси", e)
                    val updatedProxy = proxy.copy(
                        status = ProxyStatus.UNAVAILABLE,
                        pingMs = null
                    )
                    onResult(updatedProxy)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                AppLogger.e("ProxySettings", "Ошибка создания подключения к прокси", e)
                val updatedProxy = proxy.copy(
                    status = ProxyStatus.UNAVAILABLE,
                    pingMs = null
                )
                onResult(updatedProxy)
            }
        }
    }

    fun openEditDialog(proxy: ProxyConfig) {
        selectedProxyType = proxy.type
        server = proxy.server
        port = proxy.port.toString()
        username = proxy.username ?: ""
        password = proxy.password ?: ""
        secret = proxy.secret ?: ""
        validationErrors = emptyList()
        showEditDialog = proxy
    }

    fun openAddDialog() {
        selectedProxyType = ProxyType.SOCKS5
        server = ""
        port = ""
        username = ""
        password = ""
        secret = ""
        validationErrors = emptyList()
        showAddDialog = true
    }

    fun connectProxy() {
        val validationResult = when (selectedProxyType) {
            ProxyType.SOCKS5 -> ProxyValidator.validateSocks5Proxy(server, port, username, password)
            ProxyType.MTPROTO -> ProxyValidator.validateMtProtoProxy(server, port, secret)
        }

        if (validationResult.isValid) {
            val newProxy = ProxyConfig(
                type = selectedProxyType,
                server = server,
                port = port.toIntOrNull() ?: 0,
                username = username.takeIf { it.isNotBlank() },
                password = password.takeIf { it.isNotBlank() },
                secret = secret.takeIf { it.isNotBlank() },
                status = ProxyStatus.CONNECTING
            )
            scope.launch {
                proxyStorage.addProxy(newProxy)
                val addedProxy = savedProxies.lastOrNull() ?: newProxy
                simulateProxyConnection(addedProxy.id) { updatedProxy ->
                    scope.launch {
                        proxyStorage.updateProxy(updatedProxy)
                    }
                }
            }
            showAddDialog = false
        } else {
            validationErrors = validationResult.errors
        }
    }

    fun editProxy(existingProxy: ProxyConfig) {
        val validationResult = when (selectedProxyType) {
            ProxyType.SOCKS5 -> ProxyValidator.validateSocks5Proxy(server, port, username, password)
            ProxyType.MTPROTO -> ProxyValidator.validateMtProtoProxy(server, port, secret)
        }

        if (validationResult.isValid) {
            val updatedProxy = existingProxy.copy(
                type = selectedProxyType,
                server = server,
                port = port.toIntOrNull() ?: 0,
                username = username.takeIf { it.isNotBlank() },
                password = password.takeIf { it.isNotBlank() },
                secret = secret.takeIf { it.isNotBlank() },
                status = ProxyStatus.CONNECTING
            )
            scope.launch {
                proxyStorage.updateProxy(updatedProxy)
                simulateProxyConnection(updatedProxy.id) { resultProxy ->
                    scope.launch {
                        proxyStorage.updateProxy(resultProxy)
                    }
                }
            }
            showEditDialog = null
        } else {
            validationErrors = validationResult.errors
        }
    }

    fun deleteProxy(proxyId: String) {
        scope.launch {
            proxyStorage.removeProxy(proxyId)
        }
        showDeleteConfirmDialog = null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Настройка прокси",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Добавлено ${savedProxies.size}/$MAX_PROXIES",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Button(
                        onClick = { openAddDialog() },
                        enabled = isAddButtonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAddButtonEnabled) Color(0xFFE53935) else Color.Gray,
                            disabledContainerColor = Color.Gray
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Добавить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (savedProxies.isEmpty()) {
                    Text(
                        "Нет добавленных прокси",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    savedProxies.forEach { proxy ->
                        ProxyItem(
                            proxy = proxy,
                            onDelete = { showDeleteConfirmDialog = proxy.id },
                            onEdit = { openEditDialog(proxy) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Приложения, с которыми использовать прокси",
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                val availableSites = listOf("ПОТОКCASH", "СБЕРКАССА", "E-ID")
                availableSites.forEach { site ->
                    val isAnyProxyEnabled = savedProxies.any { site in it.enabledForSites }
                    val hasAnyProxy = savedProxies.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAnyProxyEnabled,
                            onCheckedChange = { checked ->
                                if (savedProxies.isNotEmpty()) {
                                    val firstProxy = savedProxies.first()
                                    val newSites = if (checked) {
                                        firstProxy.enabledForSites + site
                                    } else {
                                        firstProxy.enabledForSites - site
                                    }
                                    scope.launch {
                                        proxyStorage.updateProxySites(firstProxy.id, newSites)
                                    }
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE53935)),
                            enabled = hasAnyProxy
                        )
                        Text(site, fontSize = 14.sp, color = if (hasAnyProxy) Color.White else Color.Gray)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProxyAddEditDialog(
            title = "Добавить прокси",
            proxyType = selectedProxyType,
            server = server,
            port = port,
            username = username,
            password = password,
            secret = secret,
            validationErrors = validationErrors,
            onProxyTypeChange = {
                selectedProxyType = it
                validationErrors = emptyList()
            },
            onServerChange = {
                server = it
                validationErrors = emptyList()
            },
            onPortChange = {
                port = it
                validationErrors = emptyList()
            },
            onUsernameChange = {
                username = it
                validationErrors = emptyList()
            },
            onPasswordChange = {
                password = it
                validationErrors = emptyList()
            },
            onSecretChange = {
                secret = it
                validationErrors = emptyList()
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { connectProxy() }
        )
    }

    showEditDialog?.let { proxy ->
        ProxyAddEditDialog(
            title = "Редактировать прокси",
            proxyType = selectedProxyType,
            server = server,
            port = port,
            username = username,
            password = password,
            secret = secret,
            validationErrors = validationErrors,
            onProxyTypeChange = {
                selectedProxyType = it
                validationErrors = emptyList()
            },
            onServerChange = {
                server = it
                validationErrors = emptyList()
            },
            onPortChange = {
                port = it
                validationErrors = emptyList()
            },
            onUsernameChange = {
                username = it
                validationErrors = emptyList()
            },
            onPasswordChange = {
                password = it
                validationErrors = emptyList()
            },
            onSecretChange = {
                secret = it
                validationErrors = emptyList()
            },
            onDismiss = { showEditDialog = null },
            onConfirm = { editProxy(proxy) }
        )
    }

    showDeleteConfirmDialog?.let { proxyId ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Удалить прокси?") },
            text = { Text("Вы уверены, что хотите удалить этот прокси?") },
            confirmButton = {
                TextButton(onClick = { deleteProxy(proxyId) }) {
                    Text("Удалить", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ProxyItem(
    proxy: ProxyConfig,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val statusColor = when (proxy.status) {
        ProxyStatus.CONNECTING -> Color(0xFFFF9800)
        ProxyStatus.CONNECTED -> Color(0xFF4CAF50)
        ProxyStatus.UNAVAILABLE -> Color(0xFFE53935)
        ProxyStatus.DISCONNECTED -> Color.Gray
    }

    val statusText = when (proxy.status) {
        ProxyStatus.CONNECTING -> "Соединение"
        ProxyStatus.CONNECTED -> "${proxy.pingMs ?: 0}мс"
        ProxyStatus.UNAVAILABLE -> "Недоступно"
        ProxyStatus.DISCONNECTED -> "Отключен"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = Color(0xFFE53935)
                    )
                }
                Text(
                    text = "${proxy.server}:${proxy.port}",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onEdit() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProxyAddEditDialog(
    title: String,
    proxyType: ProxyType,
    server: String,
    port: String,
    username: String,
    password: String,
    secret: String,
    validationErrors: List<String>,
    onProxyTypeChange: (ProxyType) -> Unit,
    onServerChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = proxyType == ProxyType.SOCKS5,
                            onClick = { onProxyTypeChange(ProxyType.SOCKS5) },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE53935))
                        )
                        Text("SOCKS5", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = proxyType == ProxyType.MTPROTO,
                            onClick = { onProxyTypeChange(ProxyType.MTPROTO) },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE53935))
                        )
                        Text("MTProto", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = server,
                    onValueChange = onServerChange,
                    label = { Text("Сервер (IP)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationErrors.any { it.contains("IP") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.all { c -> c.isDigit() }) onPortChange(it) },
                    label = { Text("Порт") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationErrors.any { it.contains("Порт") }
                )

                if (proxyType == ProxyType.SOCKS5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Логин") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = validationErrors.any { it.contains("Логин") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = validationErrors.any { it.contains("Пароль") }
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secret,
                        onValueChange = onSecretChange,
                        label = { Text("Ключ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = validationErrors.any { it.contains("Ключ") }
                    )
                }

                if (validationErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    validationErrors.forEach { error ->
                        Text(
                            text = error,
                            color = Color(0xFFE53935),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text(if (title.contains("Добавить")) "Подключиться" else "Редактировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Секция поддержки проекта (донат).
 *
 * @param onOpenBrowserUrl Callback открытия URL
 */
@Composable
fun DonateSection(onOpenBrowserUrl: (String) -> Unit = {}) {
    val context = LocalContext.current
    val email = "flow.hack.com@gmail.com"
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Отблагодарить", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Вы можете отблагодарить создателя приложения", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onOpenBrowserUrl("https://sberkassa.site/transfer") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Перейти в СберКассу", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Email", email))
                        Toast.makeText(context, "E-mail скопирован", Toast.LENGTH_SHORT).show()
                    },
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Пользователь СберКассы: ", fontSize = 11.sp, color = Color.Gray)
                    Text(email, fontSize = 11.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Секция обратной связи */
@Composable
fun SupportSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLogger = com.flowhack.flowcapital.data.logging.AppLogger

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = appLogger.exportLogToFile(context, uri)
                if (result.isSuccess) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("dmitriy@flow-hack.ru"))
                        putExtra(Intent.EXTRA_SUBJECT, "Сообщение о проблемах в FlowCapital")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "К письму приложен лог файл с информацией о приложении. Это поможет лучше понять проблему и быстрее исправить, не удаляйте его. Вы можете более подробно описать ошибку:\n\n"
                        )
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Отправить письмо"))
                } else {
                    Toast.makeText(context, "Ошибка при сохранении лога", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Обратная связь",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFFE53935),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Для новых идей пишите на:",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("dmitriy@flow-hack.ru"))
                        putExtra(Intent.EXTRA_SUBJECT, "Обратная связь по FlowCapital")
                    }
                    context.startActivity(Intent.createChooser(intent, "Отправить письмо"))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("dmitriy@flow-hack.ru", fontSize = 14.sp, color = Color(0xFFE53935))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    appLogger.log("SupportSection", "Пользователь нажал 'Сообщить об ошибке'")
                    logExportLauncher.launch("FlowCapital_Log_${System.currentTimeMillis()}.txt")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сообщить об ошибке")
            }

            Spacer(modifier = Modifier.height(4.dp))

            val logCount = appLogger.getLogCount()
            Text(
                "К письму будет автоматически приложен лог файл приложения ($logCount записей)",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Карточка импорта/экспорта настроек.
 */
@Composable
fun ImportExportSettingsCard(scope: CoroutineScope = rememberCoroutineScope(), settingsManager: SettingsManager) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    exportSettingsToJson(context, uri, settingsManager)
                    Toast.makeText(context, "Данные успешно экспортированы", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e("SettingsScreen", "Ошибка экспорта настроек", e)
                    Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    importSettingsFromJson(context, uri, settingsManager)
                    Toast.makeText(context, "Данные успешно восстановлены", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e("SettingsScreen", "Ошибка импорта настроек", e)
                    Toast.makeText(context, "Ошибка импорта. Файл поврежден или имеет неверный формат", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Экспорт/Импорт",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Вы можете экспортировать или импортировать настройки и историю всех потоков для резервного копирования.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val timestamp = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
                        exportLauncher.launch("FlowCapital_Backup_$timestamp.json")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FlowColors.PSP_COLOR)
                ) {
                    Text("Экспортировать", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Импортировать", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Карточка отображения состояния обновлений в настройках.
 * Только читает из GlobalUpdateManager, НЕ делает сетевых запросов.
 */
@Composable
fun UpdateCheckerCard() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var isChecking by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val apkDownloader = remember { com.flowhack.flowcapital.data.update.ApkDownloader(context) }
    val downloadState by apkDownloader.downloadState.collectAsState()

    val release by com.flowhack.flowcapital.data.update.GlobalUpdateManager.releaseState.collectAsState()
    val skippedVersion = release?.versionName

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Обновления", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (skippedVersion != null) {
                Text("Доступно обновление до $skippedVersion", fontSize = 12.sp, color = Color(0xFFFF9800))
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (!isChecking) {
                OutlinedButton(onClick = {
                    isChecking = true
                    scope.launch {
                        val result = com.flowhack.flowcapital.data.update.checkForUpdate("FlowHack", "flow-capital-android")
                        isChecking = false
                        when (result) {
                            is com.flowhack.flowcapital.data.update.UpdateCheckResult.UpdateAvailable -> {
                                com.flowhack.flowcapital.data.update.GlobalUpdateManager.releaseState.value = result.release
                                showUpdateDialog = true
                            }
                            is com.flowhack.flowcapital.data.update.UpdateCheckResult.NoUpdate -> {
                                com.flowhack.flowcapital.data.update.GlobalUpdateManager.releaseState.value = null
                            }
                            is com.flowhack.flowcapital.data.update.UpdateCheckResult.Error -> {}
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Проверить обновления") }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Проверяем...", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val checkOnStart by settingsManager.checkUpdateOnStartFlow.collectAsState(initial = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Checkbox(
                    checked = checkOnStart,
                    onCheckedChange = { checked ->
                        scope.launch { settingsManager.setCheckUpdateOnStart(checked) }
                    }
                )
                Text("Проверять обновления при входе в приложение", fontSize = 12.sp)
            }
        }
    }

    if (showUpdateDialog) {
        val currentRelease = release
        if (currentRelease != null) {
            com.flowhack.flowcapital.data.update.UpdateDialog(
                release = currentRelease,
                settingsManager = settingsManager,
                apkDownloader = apkDownloader,
                onDismiss = {
                    showUpdateDialog = false
                },
                onOpenRelease = {
                    apkDownloader.openInBrowser(currentRelease.htmlUrl)
                    showUpdateDialog = false
                },
            onSkipVersion = { version ->
                scope.launch { settingsManager.setSkippedVersion(version) }
            },
            onSkipAutoUpdate = {
                scope.launch { settingsManager.setSkipAutoUpdate(true) }
            }
            )
        }
    }
}

/**
 * Открыть настройки батареи.
 * Маршрут зависит от производителя устройства.
 *
 * @param context Контекст приложения
 */
fun openBatterySettings(context: Context) {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val intent: Intent?

    try {
        intent = when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                Intent("miui.intent.action.OP_AUTO_START").apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }
            manufacturer.contains("samsung") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
            }
            manufacturer.contains("oppo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            manufacturer.contains("realme") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            manufacturer.contains("oneplus") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
            }
            manufacturer.contains("vivo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            manufacturer.contains("asus") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                }
            }
            manufacturer.contains("meizu") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.security.SHOW_APPSEC"
                    )
                }
            }
            manufacturer.contains("lenovo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.lenovo.security",
                        "com.lenovo.security.purebackground.PureBackgroundActivity"
                    )
                }
            }
            manufacturer.contains("zte") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.zte.heartyservice",
                        "com.zte.heartyservice.extendfunc.ExtendFuncActivity"
                    )
                }
            }
            manufacturer.contains("alcatel") || manufacturer.contains("tcl") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.tcl.security",
                        "com.tcl.security.autorun.AutoRunActivity"
                    )
                }
            }
            manufacturer.contains("sony") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.sonymobile.cta",
                        "com.sonymobile.cta.SomcCTAActivity"
                    )
                }
            }
            manufacturer.contains("google") -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Toast.makeText(context, "Отключите оптимизацию для этого приложения", Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        AppLogger.e("BatterySettings", "Ошибка открытия специфичных настроек: ${e.message}", e)
    }

    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
        Toast.makeText(context, "Отключите оптимизацию для этого приложения", Toast.LENGTH_LONG).show()
    } catch (e1: Exception) {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            context.startActivity(intent)
        } catch (e2: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
                Toast.makeText(context, "Найдите 'Оптимизация батареи' и отключите для приложения", Toast.LENGTH_LONG).show()
            } catch (e3: Exception) {
                AppLogger.e("BatterySettings", "Не удалось открыть настройки батареи", e3)
            }
        }
    }
}

/**
 * Экспортировать все настройки и историю потоков в JSON-файл.
 *
 * Читает:
 * - Все настройки из DataStore (SettingsManager + ProxyStorage)
 * - Историю РП, ПН, ПСП из Room (включая периоды)
 *
 * @param context Контекст приложения
 * @param uri URI файла, выбранного через SAF
 * @param settingsManager Экземпляр SettingsManager (должен быть тот же, что наблюдает UI для реактивности)
 */
private suspend fun exportSettingsToJson(context: Context, uri: Uri, settingsManager: SettingsManager) {
    withContext(Dispatchers.IO) {
        AppLogger.d("SettingsScreen", "Начат экспорт настроек")
        val database = AppDatabase.getDatabase(context)

        // Сбор данных из БД
        val growingHistory = database.growingFlowDao().getAllHistory().first()
        val noviceHistory = database.noviceFlowDao().getAllHistory().first()
        val pspFlows = database.premiumStartFlowDao().getAllFlows().first()
        AppLogger.d("SettingsScreen", "Собрано: РП=${growingHistory.size}, ПН=${noviceHistory.size}, ПСП=${pspFlows.size} потоков")

        // Сбор периодов для всех ПСП
        val allPeriods = mutableListOf<PremiumStartPeriodBackup>()
        pspFlows.forEach { flow ->
            val periods = database.premiumStartPeriodDao().getPeriodsByFlowId(flow.id).first()
            periods.forEach { period ->
                allPeriods.add(
                    PremiumStartPeriodBackup(
                        id = period.id,
                        flowId = period.flowId,
                        periodNumber = period.periodNumber,
                        percent = period.percent,
                        startDate = period.startDate,
                        endDate = period.endDate,
                        accrualAmount = period.accrualAmount,
                        isContributionMade = period.isContributionMade,
                        isCompleted = period.isCompleted,
                        contributionDate = period.contributionDate
                    )
                )
            }
        }
        AppLogger.d("SettingsScreen", "Собрано периодов ПСП: ${allPeriods.size}")

        val exportData = FullBackupData(
            appMarker = "FlowCapital_Backup",
            exportDate = System.currentTimeMillis(),
            startPercent = settingsManager.startPercentFlow.first(),
            dailyAddition = settingsManager.dailyAdditionFlow.first(),
            pnBonusPercent = settingsManager.pnBonusPercentFlow.first(),
            pnDailyPercent = settingsManager.pnDailyPercentFlow.first(),
            eCurrencyCoefficients = settingsManager.eCurrencyCoefficientsFlow.first(),
            pspCoefficients = settingsManager.pspCoefficientsFlow.first(),
            growingFlowHistory = growingHistory.map {
                GrowingFlowEntityBackup(it.id, it.date, it.percent, it.inFlowAmount, it.dailyAccrual, it.walletAmount, it.isButtonPressed, it.actionType, step = it.step)
            },
            noviceFlowHistory = noviceHistory.map {
                NoviceFlowEntityBackup(it.id, it.date, it.percent, it.inFlowAmount, it.dailyAccrual, it.walletAmount, it.isButtonPressed, it.actionType, step = it.step)
            },
            pspFlows = pspFlows.map {
                PremiumStartFlowBackup(it.id, it.nominalAmount, it.startDate, it.totalAccrued, it.isActive, it.currentPeriod)
            },
            pspPeriods = allPeriods,
            isRpVip = settingsManager.isRpVipFlow.first(),
            checkUpdateOnStart = settingsManager.checkUpdateOnStartFlow.first(),
            darkTheme = settingsManager.darkThemeFlow.first(),
            reminders = settingsManager.remindersFlow.first(),
            alarmReminders = settingsManager.alarmRemindersFlow.first(),
            pspReminders = settingsManager.pspRemindersFlow.first(),
            defaultEntryTab = settingsManager.defaultEntryTabFlow.first(),
            defaultCalcTab = settingsManager.defaultCalcTabFlow.first(),
            browserFabOffsetX = settingsManager.browserFabOffsetXFlow.first(),
            browserFabOffsetY = settingsManager.browserFabOffsetYFlow.first(),
            skipAutoUpdate = settingsManager.skipAutoUpdateFlow.first(),
            skippedVersion = settingsManager.skippedVersionFlow.first(),
            proxiesJson = ProxyStorage(context).getProxiesJson()
        )

        val json = Json.encodeToString(FullBackupData.serializer(), exportData)
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw Exception("Не удалось открыть файл для записи")
        outputStream.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
        AppLogger.d("SettingsScreen", "Экспорт завершён (${json.length} байт)")
    }
}

/**
 * Импортировать настройки и историю потоков из JSON-файла.
 *
 * Порядок (критически важен для консистентности):
 * 1. Валидация файла (appMarker, структура JSON)
 * 2. Заливка истории в Room в единой транзакции (атомарно)
 * 3. Заливка настроек в DataStore
 *
 * Если Room-транзакция падает — все изменения БД откатываются, DataStore не тронут.
 * Если DataStore падает — Room уже записан консистентно, можно переимпортировать.
 *
 * @param context Контекст приложения
 * @param uri URI файла, выбранного через SAF
 * @param settingsManager Экземпляр SettingsManager (должен быть тот же, что наблюдает UI для реактивности кеша коэффициентов)
 * @throws Exception при ошибке чтения, неверном формате или повреждённом файле
 */
private suspend fun importSettingsFromJson(context: Context, uri: Uri, settingsManager: SettingsManager) {
    withContext(Dispatchers.IO) {
        AppLogger.d("SettingsScreen", "Начат импорт настроек")

        // 1. Чтение и парсинг файла
        val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw Exception("Не удалось прочитать файл")
        AppLogger.d("SettingsScreen", "Файл прочитан (${json.length} байт)")

        val importData: FullBackupData
        try {
            importData = Json.decodeFromString(FullBackupData.serializer(), json)
        } catch (e: Exception) {
            AppLogger.e("SettingsScreen", "Ошибка парсинга JSON", e)
            throw Exception("Файл поврежден или имеет неверный формат")
        }

        if (importData.appMarker != "FlowCapital_Backup") {
            AppLogger.e("SettingsScreen", "Неверный appMarker: ${importData.appMarker}")
            throw Exception("Неверный формат файла")
        }

        val database = AppDatabase.getDatabase(context)

        // 2. Импорт истории в Room (атомарно — единая транзакция)
        database.withTransaction {
            AppLogger.d("SettingsScreen", "Начата транзакция Room")

            database.growingFlowDao().clearAll()
            importData.growingFlowHistory.forEach { backup ->
                database.growingFlowDao().insert(
                    GrowingFlowEntity(
                        id = backup.id,
                        date = backup.date,
                        percent = backup.percent,
                        inFlowAmount = backup.inFlowAmount,
                        dailyAccrual = backup.dailyAccrual,
                        walletAmount = backup.walletAmount,
                        isButtonPressed = backup.isButtonPressed,
                        actionType = backup.actionType,
                        step = backup.step
                    )
                )
            }
            AppLogger.d("SettingsScreen", "Импортировано РП: ${importData.growingFlowHistory.size} записей")

            database.noviceFlowDao().clearAll()
            importData.noviceFlowHistory.forEach { backup ->
                database.noviceFlowDao().insert(
                    NoviceFlowEntity(
                        id = backup.id,
                        date = backup.date,
                        percent = backup.percent,
                        inFlowAmount = backup.inFlowAmount,
                        dailyAccrual = backup.dailyAccrual,
                        walletAmount = backup.walletAmount,
                        isButtonPressed = backup.isButtonPressed,
                        actionType = backup.actionType,
                        step = backup.step
                    )
                )
            }
            AppLogger.d("SettingsScreen", "Импортировано ПН: ${importData.noviceFlowHistory.size} записей")

            database.premiumStartPeriodDao().clearAll()
            database.premiumStartFlowDao().clearAll()
            importData.pspFlows.forEach { backup ->
                database.premiumStartFlowDao().insert(
                    PremiumStartFlowEntity(
                        id = backup.id,
                        nominalAmount = backup.nominalAmount,
                        startDate = backup.startDate,
                        totalAccrued = backup.totalAccrued,
                        isActive = backup.isActive,
                        currentPeriod = backup.currentPeriod
                    )
                )
            }
            AppLogger.d("SettingsScreen", "Импортировано ПСП потоков: ${importData.pspFlows.size}")

            importData.pspPeriods.forEach { backup ->
                database.premiumStartPeriodDao().insert(
                    PremiumStartPeriodEntity(
                        id = backup.id,
                        flowId = backup.flowId,
                        periodNumber = backup.periodNumber,
                        percent = backup.percent,
                        startDate = backup.startDate,
                        endDate = backup.endDate,
                        accrualAmount = backup.accrualAmount,
                        isContributionMade = backup.isContributionMade,
                        isCompleted = backup.isCompleted,
                        contributionDate = backup.contributionDate
                    )
                )
            }
            AppLogger.d("SettingsScreen", "Импортировано периодов ПСП: ${importData.pspPeriods.size}")
        }

        // 3. Импорт настроек в DataStore — только после успешной Room-транзакции
        importData.isRpVip?.let { settingsManager.setRpVip(it) }
        importData.startPercent?.let {
            settingsManager.savePercentages(it, importData.dailyAddition ?: 0.003)
        }
        importData.pnBonusPercent?.let {
            settingsManager.savePnPercentages(it, importData.pnDailyPercent ?: 2.0)
        }
        importData.eCurrencyCoefficients.let {
            settingsManager.saveECurrencyCoefficients(it)
        }
        importData.pspCoefficients.let {
            settingsManager.savePspCoefficients(it)
        }
        importData.reminders?.let { settingsManager.saveReminders(it) }
        importData.alarmReminders?.let { settingsManager.saveAlarmReminders(it) }
        importData.pspReminders?.let { settingsManager.savePspReminders(it) }
        importData.defaultEntryTab?.let { settingsManager.setDefaultEntryTab(it) }
        importData.defaultCalcTab?.let { settingsManager.setDefaultCalcTab(it) }
        importData.checkUpdateOnStart?.let { settingsManager.setCheckUpdateOnStart(it) }
        importData.darkTheme?.let { settingsManager.setDarkTheme(it) }
        importData.browserFabOffsetX?.let { x ->
            importData.browserFabOffsetY?.let { y ->
                settingsManager.saveBrowserFabOffset(x, y)
            }
        }
        importData.skipAutoUpdate?.let { settingsManager.setSkipAutoUpdate(it) }
        importData.skippedVersion?.let { settingsManager.setSkippedVersion(it) }
        importData.proxiesJson?.let { ProxyStorage(context).saveProxiesJson(it) }

        rescheduleSavedReminders(context, settingsManager)
        AppLogger.d("SettingsScreen", "Импорт завершён успешно")
    }
}

/** Data class для экспорта/импорта настроек */
@Serializable
data class GrowingFlowEntityBackup(
    val id: Int,
    val date: Long,
    val percent: Double,
    val inFlowAmount: Double,
    val dailyAccrual: Double,
    val walletAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String,
    val step: Int = 1
)

@Serializable
data class NoviceFlowEntityBackup(
    val id: Int,
    val date: Long,
    val percent: Double,
    val inFlowAmount: Double,
    val dailyAccrual: Double,
    val walletAmount: Double,
    val isButtonPressed: Boolean,
    val actionType: String,
    val step: Int = 1
)

@Serializable
data class PremiumStartFlowBackup(
    val id: Int,
    val nominalAmount: Double,
    val startDate: Long,
    val totalAccrued: Double,
    val isActive: Boolean,
    val currentPeriod: Int
)

@Serializable
data class PremiumStartPeriodBackup(
    val id: Int,
    val flowId: Int,
    val periodNumber: Int,
    val percent: Double,
    val startDate: Long,
    val endDate: Long,
    val accrualAmount: Double,
    val isContributionMade: Boolean,
    val isCompleted: Boolean,
    val contributionDate: Long? = null
)

@Serializable
data class FullBackupData(
    val appMarker: String,
    val exportDate: Long,
    val startPercent: Double?,
    val dailyAddition: Double?,
    val pnBonusPercent: Double?,
    val pnDailyPercent: Double?,
    val eCurrencyCoefficients: Map<Double, Double>,
    val pspCoefficients: Map<Int, Double>,
    val growingFlowHistory: List<GrowingFlowEntityBackup>,
    val noviceFlowHistory: List<NoviceFlowEntityBackup>,
    val pspFlows: List<PremiumStartFlowBackup>,
    val pspPeriods: List<PremiumStartPeriodBackup>,
    val reminders: Set<String>? = null,
    val alarmReminders: Set<String>? = null,
    val pspReminders: Set<String>? = null,
    val defaultEntryTab: Int? = null,
    val defaultCalcTab: Int? = null,
    val isRpVip: Boolean? = null,
    val checkUpdateOnStart: Boolean? = null,
    val darkTheme: Boolean? = null,
    val browserFabOffsetX: Int? = null,
    val browserFabOffsetY: Int? = null,
    val skipAutoUpdate: Boolean? = null,
    val skippedVersion: String? = null,
    val proxiesJson: String? = null
)

/**
 * Диалог расчёта потока РП.
 * Позволяет ввести параметры нового потока и спрогнозировать историю до указанной даты.
 * Ничего не записывает в БД - только рассчитывает и показывает результат.
 */
@Composable
private fun CalculateGrowingFlowDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedStartPercent by settingsManager.startPercentFlow.collectAsState(initial = 0.1)
    val savedDailyAddition by settingsManager.dailyAdditionFlow.collectAsState(initial = 0.003)
    val savedECurrency by settingsManager.eCurrencyCoefficientsFlow.collectAsState(initial = emptyMap())

    var contributionText by remember { mutableStateOf("") }
    var percentText by remember(savedStartPercent) { mutableStateOf(savedStartPercent.toString()) }
    var walletText by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var targetDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }

    var forecastResults by remember { mutableStateOf<List<GrowingFlowEntity>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                exportForecastToExcel(context, uri, forecastResults)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val contribution = parseDouble(contributionText)
    val percent = parseDouble(percentText)
    val wallet = if (walletText.isBlank()) 0.0 else parseDouble(walletText)

    val inFlow = if (contribution > 0 && savedECurrency.isNotEmpty()) {
        val bonus = savedECurrency.entries.filter { it.key <= contribution }.maxByOrNull { it.key }?.value ?: 0.0
        contribution + contribution * bonus / 100.0
    } else contribution
    val dailyAccrual = if (inFlow > 0) inFlow * (percent / 100.0) else 0.0

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Рассчитать поток РП", fontSize = 18.sp) },
        text = {
            Column {
                Text("Введите параметры нового потока для расчёта прогноза:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Бонус ко взносу по таблице: ${if (contribution > 0 && savedECurrency.isNotEmpty()) {
                    savedECurrency.entries.filter { it.key <= contribution }.maxByOrNull { it.key }?.value ?: 0.0
                } else 0.0}%", fontSize = 11.sp, color = Color.Gray)

                OutlinedTextField(
                    value = contributionText,
                    onValueChange = { contributionText = it },
                    label = { Text("Сумма взноса") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = percentText,
                    onValueChange = { percentText = it },
                    label = { Text("Процент") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = walletText,
                    onValueChange = { walletText = it },
                    label = { Text("В кошельке") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("С: ${dateFormat.format(Date(startDateMillis))}")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showTargetDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("До: ${dateFormat.format(Date(targetDateMillis))}")
                }

                if (contribution > 0 && percent > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("После старта:", fontSize = 12.sp)
                    Text("В потоке: ${String.format(Locale.US, "%.2f", inFlow)}", fontSize = 14.sp)
                    Text("Начисление: ${String.format(Locale.US, "%.3f", dailyAccrual)}", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (contribution > 0 && percent > 0) {
                        val results = calculateFlowForecast(
                            inFlow = inFlow,
                            percent = percent,
                            wallet = wallet,
                            startDateMillis = startDateMillis,
                            targetDateMillis = targetDateMillis,
                            dailyAddition = savedDailyAddition
                        )
                        forecastResults = results
                        showResults = true
                    }
                },
                enabled = contribution > 0 && percent > 0
            ) { Text("Рассчитать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showStartDatePicker) {
        val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    startDatePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { targetDateMillis = it }
                    showTargetDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResults && forecastResults.isNotEmpty()) {
        GrowingForecastResultsDialog(
            title = "Прогноз потока РП",
            forecastList = forecastResults,
            onDismiss = { showResults = false },
            onExportToExcel = { exportLauncher.launch("РП_Рассчет_${System.currentTimeMillis()}.xlsx") }
        )
    }
}

/**
 * Диалог расчёта действующего потока РП.
 * Поля: "В потоке", "Начисление", "В кошельке", "До даты".
 * Процент рассчитывается как (Начисление * 100) / В потоке.
 * В начале прогноза создаётся запись START и сразу DAILY (как будто кнопка нажата в тот же день).
 */
@Composable
private fun CalculateExistingGrowingFlowDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedDailyAddition by settingsManager.dailyAdditionFlow.collectAsState(initial = 0.003)

    var inFlowText by remember { mutableStateOf("") }
    var accrualText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var targetDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }
    var showTargetDatePicker by remember { mutableStateOf(false) }

    var forecastResults by remember { mutableStateOf<List<GrowingFlowEntity>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                exportExistingForecastToExcel(context, uri, forecastResults)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val inFlow = parseDouble(inFlowText)
    val accrual = parseDouble(accrualText)
    val wallet = if (walletText.isBlank()) 0.0 else parseDouble(walletText)
    
    // Рассчитываем процент: (Начисление * 100) / В потоке
    val calculatedPercent = if (inFlow > 0 && accrual > 0) (accrual * 100.0) / inFlow else 0.0
    
    val isFormValid = inFlow > 0 && accrual > 0 && calculatedPercent > 0

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Рассчитать действующий поток РП", fontSize = 18.sp) },
        text = {
            Column {
                Text("Введите параметры текущего состояния потока:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = inFlowText,
                    onValueChange = { inFlowText = it },
                    label = { Text("В потоке *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = accrualText,
                    onValueChange = { accrualText = it },
                    label = { Text("Начисление *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        if (calculatedPercent > 0) {
                            Text("Текущий процент: ${String.format(Locale.US, "%.3f", calculatedPercent)}%", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = walletText,
                    onValueChange = { walletText = it },
                    label = { Text("В кошельке") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = { Text("Оставьте пустым, если в кошельке пусто", fontSize = 10.sp, color = Color.Gray) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                OutlinedButton(
                    onClick = { showTargetDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("До: ${dateFormat.format(Date(targetDateMillis))}")
                }
                
                if (isFormValid) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Параметры прогноза:", fontSize = 12.sp)
                    Text("Процент: ${String.format(Locale.US, "%.3f", calculatedPercent)}%", fontSize = 14.sp)
                    Text("В потоке: ${String.format(Locale.US, "%.2f", inFlow)}", fontSize = 14.sp)
                    Text("Начисление: ${String.format(Locale.US, "%.2f", accrual)}", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFormValid) {
                        val results = calculateFlowForecast(
                            inFlow = inFlow,
                            percent = calculatedPercent,
                            wallet = wallet,
                            startDateMillis = System.currentTimeMillis(),
                            targetDateMillis = targetDateMillis,
                            dailyAddition = savedDailyAddition,
                            isExistingFlow = true
                        )
                        forecastResults = results
                        showResults = true
                    }
                },
                enabled = isFormValid
            ) { Text("Рассчитать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { targetDateMillis = it }
                    showTargetDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showTargetDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showResults && forecastResults.isNotEmpty()) {
        GrowingForecastResultsDialog(
            title = "Прогноз действующего потока РП",
            forecastList = forecastResults,
            onDismiss = { showResults = false },
            onExportToExcel = { exportLauncher.launch("РП_Действующий_${System.currentTimeMillis()}.xlsx") }
        )
    }
}

/**
 * Экспорт прогноза действующего потока РП в Excel с колонкой Шаг.
 */
private suspend fun exportExistingForecastToExcel(context: Context, uri: Uri, forecast: List<GrowingFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз РП", null)
                val worksheet = workbook.newWorksheet("РП")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "Процент")
                worksheet.value(0, 3, "В потоке")
                worksheet.value(0, 4, "Начисление")
                worksheet.value(0, 5, "Кошелек")
                worksheet.value(0, 6, "Действие")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                forecast.forEachIndexed { index, entry ->
                    val isActiveAction = entry.actionType in listOf("START", "DAILY", "REINVEST")
                    if (isActiveAction) currentStep++
                    val stepDisplay = if (isActiveAction) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.percent)
                    worksheet.value(row, 3, entry.inFlowAmount)
                    worksheet.value(row, 4, entry.dailyAccrual)
                    worksheet.value(row, 5, entry.walletAmount)
                    worksheet.value(row, 6, entry.actionType)
                    val fillColor = when (entry.actionType) {
                        "START", "DAILY" -> "C8FFC8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..6).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = forecast.size
                worksheet.range(0, 0, lastRow, 6).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("SettingsScreen", "Ошибка экспорта прогноза РП", e) }
    }
}

/**
 * Экспорт прогноза РП в Excel с колонкой Шаг.
 */
private suspend fun exportForecastToExcel(context: Context, uri: Uri, forecast: List<GrowingFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз РП", null)
                val worksheet = workbook.newWorksheet("РП")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "Процент")
                worksheet.value(0, 3, "В потоке")
                worksheet.value(0, 4, "Начисление")
                worksheet.value(0, 5, "Кошелек")
                worksheet.value(0, 6, "Действие")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                forecast.forEachIndexed { index, entry ->
                    val isActiveAction = entry.actionType in listOf("START", "DAILY", "REINVEST")
                    if (isActiveAction) currentStep++
                    val stepDisplay = if (isActiveAction) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.percent)
                    worksheet.value(row, 3, entry.inFlowAmount)
                    worksheet.value(row, 4, entry.dailyAccrual)
                    worksheet.value(row, 5, entry.walletAmount)
                    worksheet.value(row, 6, entry.actionType)
                    val fillColor = when (entry.actionType) {
                        "START", "DAILY" -> "C8FFC8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..6).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = forecast.size
                worksheet.range(0, 0, lastRow, 6).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("SettingsScreen", "Ошибка экспорта прогноза РП", e) }
    }
}

/**
 * Диалог расчёта прогноза ПН (Поток Новичка).
 */
@Composable
private fun CalculateNoviceFlowDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedBonusPercent by settingsManager.pnBonusPercentFlow.collectAsState(initial = 50.0)
    val savedDailyPercent by settingsManager.pnDailyPercentFlow.collectAsState(initial = 2.0)

    var contributionText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var targetDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }

    var forecastResults by remember { mutableStateOf<List<NoviceFlowEntity>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val repo = NoviceFlowRepository(db.noviceFlowDao())
                exportNoviceForecastToExcel(context, uri, forecastResults)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val contribution = parseDouble(contributionText)
    val wallet = if (walletText.isBlank()) 0.0 else parseDouble(walletText)

    val bonusPercent = savedBonusPercent
    val inFlow = if (contribution > 0) contribution + contribution * bonusPercent / 100.0 else contribution
    val dailyAccrual = if (inFlow > 0) inFlow * (savedDailyPercent / 100.0) else 0.0

    var compoundInterest by remember { mutableStateOf(false) }
    var reinvestAmountText by remember { mutableStateOf("2000") }
    val reinvestAmount = reinvestAmountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isReinvestAmountValid = !compoundInterest || (compoundInterest && reinvestAmount > 0)

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Рассчитать поток ПН", fontSize = 18.sp) },
        text = {
            Column {
                Text("Введите параметры нового потока:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Бонус: ${bonusPercent}%, Ежедневный: ${savedDailyPercent}%", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contributionText,
                    onValueChange = { contributionText = it },
                    label = { Text("Сумма взноса") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletText,
                    onValueChange = { walletText = it },
                    label = { Text("В кошельке") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("С: ${dateFormat.format(Date(startDateMillis))}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showTargetDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("До: ${dateFormat.format(Date(targetDateMillis))}")
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Чекбокс сложного процента
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = compoundInterest,
                        onCheckedChange = { compoundInterest = it }
                    )
                    Text("Включить сложный процент", fontSize = 14.sp)
                }

                if (compoundInterest) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reinvestAmountText,
                        onValueChange = { reinvestAmountText = it },
                        label = { Text("Сумма для реинвеста") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            if (!isReinvestAmountValid) {
                                Text("Сумма не может быть пустой или нулевой", color = Color.Red, fontSize = 10.sp)
                            } else {
                                Text("По умолчанию 2000", fontSize = 10.sp, color = Color.Gray)
                            }
                        },
                        isError = !isReinvestAmountValid,
                        singleLine = true
                    )
                }

                if (contribution > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "После старта: В потоке=${String.format(Locale.US, "%.2f", inFlow)}, Начисление=${String.format(Locale.US, "%.2f", dailyAccrual)}",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (contribution > 0 && isReinvestAmountValid) {
                    val results = calculateNoviceFlowForecast(
                        inFlow = inFlow,
                        dailyPercent = savedDailyPercent,
                        wallet = wallet,
                        startDateMillis = startDateMillis,
                        targetDateMillis = targetDateMillis,
                        compoundInterest = compoundInterest,
                        reinvestAmount = if (compoundInterest) reinvestAmount else 2000.0,
                        bonusPercent = savedBonusPercent
                    )
                    forecastResults = results
                    showResults = true
                }
            }, enabled = contribution > 0 && isReinvestAmountValid) { Text("Рассчитать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { targetDateMillis = it }
                    showTargetDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showTargetDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showResults && forecastResults.isNotEmpty()) {
        NoviceForecastResultsDialog(
            title = "Прогноз потока ПН",
            forecastList = forecastResults,
            onDismiss = { showResults = false },
            onExportToExcel = { exportLauncher.launch("ПН_Рассчет_${System.currentTimeMillis()}.xlsx") }
        )
    }
}

/**
 * Диалог расчёта действующего потока ПН.
 * Поля: "В потоке"; "В кошельке"; "До даты".
 * Расчёт идёт с текущей даты, без даты "С".
 * В начале прогноза создаётся запись START с указанными параметрами,
 * затем обычный расчёт со следующего дня (isExistingFlow=true).
 */
@Composable
private fun CalculateExistingNoviceFlowDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedDailyPercent by settingsManager.pnDailyPercentFlow.collectAsState(initial = 2.0)
    val savedBonusPercent by settingsManager.pnBonusPercentFlow.collectAsState(initial = 50.0)

    var inFlowText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var targetDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }
    var showTargetDatePicker by remember { mutableStateOf(false) }

    var forecastResults by remember { mutableStateOf<List<NoviceFlowEntity>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                exportExistingNoviceForecastToExcel(context, uri, forecastResults)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val inFlow = parseDouble(inFlowText)
    val wallet = if (walletText.isBlank()) 0.0 else parseDouble(walletText)
    val dailyPercent = savedDailyPercent

    val dailyAccrual = if (inFlow > 0) inFlow * (savedDailyPercent / 100.0) else 0.0

    var compoundInterest by remember { mutableStateOf(false) }
    var reinvestAmountText by remember { mutableStateOf("2000") }
    val reinvestAmount = reinvestAmountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isReinvestAmountValid = !compoundInterest || (compoundInterest && reinvestAmount > 0)

    val isFormValid = inFlow > 0 && dailyPercent > 0 && isReinvestAmountValid

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Рассчитать действующий поток ПН", fontSize = 18.sp) },
        text = {
            Column {
                Text("Введите параметры текущего состояния потока:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inFlowText,
                    onValueChange = { inFlowText = it },
                    label = { Text("В потоке *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = walletText,
                    onValueChange = { walletText = it },
                    label = { Text("В кошельке") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = { Text("Оставьте пустым, если в кошельке пусто", fontSize = 10.sp, color = Color.Gray) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                OutlinedButton(
                    onClick = { showTargetDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("До: ${dateFormat.format(Date(targetDateMillis))}")
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Чекбокс сложного процента
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = compoundInterest,
                        onCheckedChange = { compoundInterest = it }
                    )
                    Text("Включить сложный процент", fontSize = 14.sp)
                }

                if (compoundInterest) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reinvestAmountText,
                        onValueChange = { reinvestAmountText = it },
                        label = { Text("Сумма для реинвеста") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            if (!isReinvestAmountValid) {
                                Text("Сумма не может быть пустой или нулевой", color = Color.Red, fontSize = 10.sp)
                            } else {
                                Text("По умолчанию 2000", fontSize = 10.sp, color = Color.Gray)
                            }
                        },
                        isError = !isReinvestAmountValid,
                        singleLine = true
                    )
                }

                if (isFormValid) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Параметры прогноза:", fontSize = 12.sp)
                    Text("Ежедневный %: ${String.format(Locale.US, "%.2f", dailyPercent)}%", fontSize = 14.sp)
                    Text("В потоке: ${String.format(Locale.US, "%.2f", inFlow)}", fontSize = 14.sp)
                    Text("Начисление: ${String.format(Locale.US, "%.2f", dailyAccrual)}", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFormValid) {
                        val results = calculateNoviceFlowForecast(
                            inFlow = inFlow,
                            dailyPercent = dailyPercent,
                            wallet = wallet,
                            startDateMillis = System.currentTimeMillis(),
                            targetDateMillis = targetDateMillis,
                            isExistingFlow = true,
                            compoundInterest = compoundInterest,
                            reinvestAmount = if (compoundInterest) reinvestAmount else 2000.0,
                            bonusPercent = savedBonusPercent
                        )
                        forecastResults = results
                        showResults = true
                    }
                },
                enabled = isFormValid
            ) { Text("Рассчитать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { targetDateMillis = it }
                    showTargetDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showTargetDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showResults && forecastResults.isNotEmpty()) {
        NoviceForecastResultsDialog(
            title = "Прогноз действующего потока ПН",
            forecastList = forecastResults,
            onDismiss = { showResults = false },
            onExportToExcel = { exportLauncher.launch("ПН_Действующий_${System.currentTimeMillis()}.xlsx") }
        )
    }
}

/**
 * Экспорт прогноза действующего потока ПН в Excel с колонкой Шаг.
 */
private suspend fun exportExistingNoviceForecastToExcel(context: Context, uri: Uri, forecast: List<NoviceFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПН", null)
                val worksheet = workbook.newWorksheet("ПН")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "В потоке")
                worksheet.value(0, 3, "Начисление")
                worksheet.value(0, 4, "Кошелек")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                forecast.forEachIndexed { index, entry ->
                    val isActive = entry.actionType in listOf("PN_START", "PN_DAILY")
                    if (isActive) currentStep++
                    val stepDisplay = if (isActive) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.inFlowAmount)
                    worksheet.value(row, 3, entry.dailyAccrual)
                    worksheet.value(row, 4, entry.walletAmount)
                    val fillColor = when (entry.actionType) {
                        "PN_START", "PN_DAILY" -> "C8FFC8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..4).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = forecast.size
                worksheet.range(0, 0, lastRow, 4).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("SettingsScreen", "Ошибка экспорта прогноза ПН", e) }
    }
}

/**
 * Экспорт прогноза ПН в Excel.
 */
private suspend fun exportNoviceForecastToExcel(context: Context, uri: Uri, forecast: List<NoviceFlowEntity>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПН", null)
                val worksheet = workbook.newWorksheet("ПН")
                worksheet.value(0, 0, "Шаг")
                worksheet.value(0, 1, "Дата")
                worksheet.value(0, 2, "В потоке")
                worksheet.value(0, 3, "Начисление")
                worksheet.value(0, 4, "Кошелек")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                var currentStep = 0
                forecast.forEachIndexed { index, entry ->
                    val isActive = entry.actionType in listOf("PN_START", "PN_DAILY")
                    if (isActive) currentStep++
                    val stepDisplay = if (isActive) currentStep.toString() else "-"
                    val date = dateFormat.format(Date(entry.date))
                    val row = index + 1
                    worksheet.value(row, 0, stepDisplay)
                    worksheet.value(row, 1, date)
                    worksheet.value(row, 2, entry.inFlowAmount)
                    worksheet.value(row, 3, entry.dailyAccrual)
                    worksheet.value(row, 4, entry.walletAmount)
                    val fillColor = when (entry.actionType) {
                        "PN_START", "PN_DAILY" -> "C8FFC8"
                        "SUNDAY" -> "E6C8FF"
                        else -> null
                    }
                    if (fillColor != null) {
                        (0..4).forEach { col ->
                            worksheet.style(row, col).fillColor(fillColor).set()
                        }
                    }
                }
                val lastRow = forecast.size
                worksheet.range(0, 0, lastRow, 4).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("SettingsScreen", "Ошибка экспорта прогноза ПН", e) }
    }
}

/**
 * Диалог расчёта прогноза ПСП (Премиум Стартовый Поток).
 */
@Composable
private fun CalculatePspDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedPspCoeffs by settingsManager.pspCoefficientsFlow.collectAsState(initial = null)

    var nominalText by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var forecastResults by remember { mutableStateOf<List<PspForecastResult>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                exportPspForecastToExcel(context, uri, forecastResults)
                Toast.makeText(context, "Экспорт завершён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val nominal = parseDouble(nominalText)
    val coefficients = savedPspCoeffs ?: emptyMap()
    val hasValidNominal = nominal > 0 && coefficients.isNotEmpty()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Рассчитать поток ПСП", fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Введите параметры потока:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nominalText,
                    onValueChange = { nominalText = it },
                    label = { Text("Номинал") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Старт: ${dateFormat.format(Date(startDateMillis))}")
                }
                if (nominal > 0 && coefficients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    val totalAccrued = nominal * (coefficients[20] ?: 0.0) / 100.0
                    Text("Всего периодов: 20", fontSize = 12.sp)
                    Text("Итого начислено: ${String.format(Locale.US, "%.2f", totalAccrued)}", fontSize = 14.sp, color = Color(0xFF4CAF50))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (hasValidNominal) {
                    val results = calculatePspForecast(nominal, startDateMillis, coefficients)
                    forecastResults = results
                    showResults = true
                }
            }, enabled = hasValidNominal) { Text("Рассчитать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showResults && forecastResults.isNotEmpty()) {
        PspForecastResultsDialog(
            title = "Прогноз ПСП",
            forecastList = forecastResults,
            onDismiss = { showResults = false },
            onExportToExcel = { exportLauncher.launch("ПСП_Рассчет_${System.currentTimeMillis()}.xlsx") }
        )
    }
}

/**
 * Экспорт прогноза ПСП в Excel.
 */
private suspend fun exportPspForecastToExcel(context: Context, uri: Uri, forecast: List<PspForecastResult>) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val workbook = org.dhatim.fastexcel.Workbook(outputStream, "Прогноз ПСП", null)
                val worksheet = workbook.newWorksheet("ПСП")
                worksheet.value(0, 0, "Период")
                worksheet.value(0, 1, "Дата взноса")
                worksheet.value(0, 2, "Дата закрытия")
                worksheet.value(0, 3, "Начислено")
                worksheet.value(0, 4, "%")
                worksheet.value(0, 5, "Всего получено")
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                forecast.forEachIndexed { index, entry ->
                    val row = index + 1
                    worksheet.value(row, 0, entry.periodNumber)
                    worksheet.value(row, 1, dateFormat.format(Date(entry.startDate)))
                    worksheet.value(row, 2, dateFormat.format(Date(entry.endDate)))
                    worksheet.value(row, 3, entry.accrualAmount)
                    worksheet.value(row, 4, entry.percent)
                    worksheet.value(row, 5, entry.totalAccrued)
                    if (entry.isCompleted) {
                        (0..5).forEach { col ->
                            worksheet.style(row, col).fillColor("C8FFC8").set()
                        }
                    }
                }
                val lastRow = forecast.size
                worksheet.range(0, 0, lastRow, 5).style().horizontalAlignment("center").set()
                workbook.finish()
            }
        } catch (e: Exception) { AppLogger.e("SettingsScreen", "Ошибка экспорта прогноза ПСП", e) }
    }
}

/**
 * Диалог результатов прогноза ПСП.
 */
@Composable
private fun PspForecastResultsDialog(
    title: String,
    forecastList: List<PspForecastResult>,
    onDismiss: () -> Unit,
    onExportToExcel: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val tableFontSize = if (isLandscape) 12.sp else 11.sp

    val weightPeriod = 0.6f
    val weightDate = 1.4f
    val weightAccrual = 1.2f
    val weightTotal = 1.2f

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
                .fillMaxWidth(if (isLandscape) 0.8f else 0.85f)
                .fillMaxHeight(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(title, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Пер.", modifier = Modifier.weight(weightPeriod), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Закрытие", modifier = Modifier.weight(weightDate), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Начислено", modifier = Modifier.weight(weightAccrual), fontSize = tableFontSize, textAlign = TextAlign.Center)
                    Text("Всего", modifier = Modifier.weight(weightTotal), fontSize = tableFontSize, textAlign = TextAlign.Center)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(forecastList.size) { index ->
                        val entry = forecastList[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (entry.isCompleted) Color(0x33C8FFC8) else Color.Transparent
                                )
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(weightPeriod), contentAlignment = Alignment.Center) {
                                Text(entry.periodNumber.toString(), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(weightDate), contentAlignment = Alignment.Center) {
                                Text(dateFormat.format(Date(entry.endDate)), fontSize = tableFontSize, textAlign = TextAlign.Center)
                            }
                            Box(modifier = Modifier.weight(weightAccrual), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.accrualAmount),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center,
                                    color = if (entry.isCompleted) Color(0xFF4CAF50) else Color(0xFF4CAF50)
                                )
                            }
                            Box(modifier = Modifier.weight(weightTotal), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.totalAccrued),
                                    fontSize = tableFontSize,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onExportToExcel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        )
                    ) { Text("Excel", fontSize = 12.sp) }
                }
            }
        }
    }
}
