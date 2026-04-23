@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.flowcapital.ui.screens.calculator

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.NoviceFlowEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Диалог старта/реинвеста для Растущего Потока.
 * Позволяет ввести сумму взноса, процент и текущий кошелёк.
 * Поддерживает два режима: новый поток и действующий поток.
 *
 * @param onDismiss Закрытие диалога
 * @param onConfirm Подтверждение с суммой, процентом и кошельком
 * @param defaultPercent Значение процента по умолчанию
 * @param isNewFlow true если это создание нового потока
 */
@Composable
fun ReinvestDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?, Double?, Boolean) -> Unit,
    defaultPercent: Double = 0.1,
    isNewFlow: Boolean = false,
    eCurrencyBonusPercent: Double = 0.0,
    onAmountChanged: (String) -> Unit = {}
) {
    var amountText by remember { mutableStateOf("") }
    var percentText by remember { mutableStateOf(if (isNewFlow) defaultPercent.toString() else "") }
    var walletText by remember { mutableStateOf("") }
    var walletExplicitlySet by remember { mutableStateOf(false) }
    var isExistingFlow by remember { mutableStateOf(false) }

    val eCurrencyBonus = if (amountText.isNotEmpty()) eCurrencyBonusPercent else 0.0

    val currentPercent: Double = if (isExistingFlow && amountText.isNotEmpty() && percentText.isNotEmpty()) {
        val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val accrual = percentText.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (amount > 0) (accrual * 100.0) / amount else 0.0
    } else defaultPercent

    val isPercentValid = !isExistingFlow || percentText.isNotEmpty()
    val isAmountValid = amountText.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewFlow) "Старт РП" else "Реинвест", fontSize = 18.sp) },
        text = {
            Column {
                if (!isExistingFlow) {
                    Text("Процент начинается с ${String.format(Locale.US, "%.3f", defaultPercent)}%", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (amountText.isNotEmpty()) "Бонус ко взносу по таблице: ${String.format(Locale.US, "%.0f", eCurrencyBonus)}%"
                        else "Бонус ко взносу по таблице",
                        fontSize = 12.sp, color = Color.Gray
                    )
                } else {
                    Text(
                        if (amountText.isNotEmpty() && percentText.isNotEmpty()) "Текущий процент: ${String.format(Locale.US, "%.3f", currentPercent)}%"
                        else "Введите данные для расчёта",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                if (isNewFlow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isExistingFlow,
                            onCheckedChange = { 
                                isExistingFlow = it
                                if (it && percentText.isEmpty()) {
                                    percentText = ""
                                } else if (!it && percentText.isEmpty()) {
                                    percentText = defaultPercent.toString()
                                }
                            }
                        )
                        Text("Поток уже действующий", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText, 
                    onValueChange = { 
                        amountText = it
                        onAmountChanged(it)
                    },
                    label = { Text(if (!isExistingFlow) "Сумма взноса *" else "В потоке *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = amountText.isEmpty() && isAmountValid
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = percentText,
                    onValueChange = { percentText = it },
                    label = { Text(if (!isExistingFlow) "Процент *" else "Начисление *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isExistingFlow && percentText.isEmpty(),
                    supportingText = {
                        if (!isExistingFlow) {
                            Text("По умолчанию: ${String.format(Locale.US, "%.3f", defaultPercent)}%", fontSize = 10.sp, color = Color.Gray)
                        } else if (percentText.isEmpty()) {
                            Text("Обязательно для заполнения", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Будет рассчитан текущий процент", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletText, onValueChange = { 
                        walletText = it
                        walletExplicitlySet = it.isNotEmpty()
                    },
                    label = { Text("В кошельке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { 
                        Text(
                            if (walletExplicitlySet && walletText.isEmpty()) 
                                "Введите 0, чтобы обнулить кошелёк"
                            else if (!walletExplicitlySet) 
                                "Оставьте поле пустым, если в кошельке пусто"
                            else 
                                "Введите нужную сумму"
                        ) 
                    }
                )
            }
        },
        confirmButton = {
            val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
            val percent = percentText.replace(",", ".").toDoubleOrNull() ?: defaultPercent
            val isEnabled = amount > 0 && (!isExistingFlow || percentText.isNotEmpty())
            Button(
                onClick = {
                    if (isEnabled) {
                        onConfirm(amount, percent, if (walletExplicitlySet) walletText.replace(",", ".").toDoubleOrNull() else null, isExistingFlow)
                    }
                },
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) { Text("Внести") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Диалог с результатами прогноза РП.
 * Отображает таблицу с датами, суммами и начислениями.
 *
 * @param title Заголовок диалога
 * @param forecastList Список записей прогноза
 * @param onDismiss Закрытие диалога
 * @param onExportToExcel Экспорт в CSV
 */
@Composable
fun GrowingForecastResultsDialog(
    title: String,
    forecastList: List<GrowingFlowEntity>,
    onDismiss: () -> Unit,
    onExportToExcel: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val narrowScreen = isNarrowScreen()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        title = { Text(title, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Дата", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text("В потоке", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    if (!narrowScreen) Text("Начисление", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text("Кошелек", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(forecastList) { entry ->
                        val isSunday = entry.actionType == "SUNDAY"
                        val isDropDay = entry.actionType == "DROP_DAY"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDropDay) Color(0x33F44336)
                                    else if (isSunday) Color(0xFF2C2C2C)
                                    else Color.Transparent
                                )
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    dateFormat.format(Date(entry.date)),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.inFlowAmount),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (!narrowScreen) Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "+%.2f", entry.dailyAccrual),
                                    color = if (isSunday) Color.Gray else if (isDropDay) Color(0xFFEF5350) else Color(0xFF4CAF50),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.walletAmount),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onExportToExcel) { Text("Excel", fontSize = 12.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/**
 * Диалог с результатами прогноза ПН.
 *
 * @param title Заголовок диалога
 * @param forecastList Список записей прогноза
 * @param onDismiss Закрытие диалога
 * @param onExportToExcel Экспорт в CSV
 */
@Composable
fun NoviceForecastResultsDialog(
    title: String,
    forecastList: List<NoviceFlowEntity>,
    onDismiss: () -> Unit,
    onExportToExcel: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val narrowScreen = isNarrowScreen()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        title = { Text(title, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Дата", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text("В потоке", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    if (!narrowScreen) Text("Начисление", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text("Кошелек", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(forecastList) { entry ->
                        val isSunday = entry.actionType == "SUNDAY"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSunday) Color(0xFF2C2C2C)
                                    else Color.Transparent
                                )
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    dateFormat.format(Date(entry.date)),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.inFlowAmount),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (!narrowScreen) Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "+%.2f", entry.dailyAccrual),
                                    color = if (isSunday) Color.Gray else Color(0xFF4CAF50),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(Locale.US, "%.2f", entry.walletAmount),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onExportToExcel) { Text("Excel", fontSize = 12.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/**
 * Диалог корректировки значений РП.
 * Позволяет изменить поток, начисление, кошелёк и состояние кнопки.
 * Обязательно должно быть изменено хотя бы одно поле относительно текущего состояния.
 *
 * @param onDismiss Закрытие диалога
 * @param onConfirm Подтверждение с новыми значениями
 * @param currentInFlow Текущее значение потока
 * @param currentAccrual Текущее начисление
 * @param currentWallet Текущий кошелёк
 * @param currentButtonPressed Текущее состояние кнопки
 * @param currentPercent Текущий процент
 */
@Composable
fun CorrectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double, Boolean) -> Unit,
    currentInFlow: Double,
    currentAccrual: Double,
    currentWallet: Double,
    currentButtonPressed: Boolean,
    currentPercent: Double = 0.1
) {
    var flowText by remember { mutableStateOf("") }
    var accrualText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var isButtonPressed by remember { mutableStateOf(currentButtonPressed) }

    fun parseDouble(text: String): Double? {
        return text.replace(",", ".").toDoubleOrNull()
    }

    val flowChanged = flowText.isNotEmpty() && parseDouble(flowText)?.let { it != currentInFlow } ?: false
    val accrualChanged = accrualText.isNotEmpty() && parseDouble(accrualText)?.let { it != currentAccrual } ?: false
    val walletChanged = walletText.isNotEmpty() && parseDouble(walletText)?.let { it != currentWallet } ?: false
    val checkboxChanged = isButtonPressed != currentButtonPressed

    val hasAnyChange = flowChanged || accrualChanged || walletChanged || checkboxChanged

    val hasSomeInput = flowText.isNotEmpty() || accrualText.isNotEmpty() || walletText.isNotEmpty() || checkboxChanged

    val isEnabled = hasAnyChange && hasSomeInput

    val newFlow = parseDouble(flowText) ?: currentInFlow
    val newAccrual = parseDouble(accrualText) ?: currentAccrual
    val newWallet = parseDouble(walletText) ?: currentWallet

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Корректировка РП", fontSize = 18.sp) },
        text = {
            Column {
                Text("Если указано только 'В потоке' - пересчитывается начисление (процент остаётся). Если только 'Начисление' - пересчитывается процент.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = flowText, onValueChange = { flowText = it },
                    label = { Text("В потоке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Текущее: ${String.format(Locale.US, "%.2f", currentInFlow)}", fontSize = 10.sp, color = Color.Gray) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = accrualText, onValueChange = { accrualText = it },
                    label = { Text("Начисление") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Текущее: ${String.format(Locale.US, "%.2f", currentAccrual)}", fontSize = 10.sp, color = Color.Gray) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletText, onValueChange = { walletText = it },
                    label = { Text("В кошельке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Текущее: ${String.format(Locale.US, "%.2f", currentWallet)}", fontSize = 10.sp, color = Color.Gray) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isButtonPressed,
                        onCheckedChange = { isButtonPressed = it }
                    )
                    Text("Кнопка нажата (было: ${if (currentButtonPressed) "да" else "нет"})")
                }
                if (!isEnabled && hasSomeInput && !hasAnyChange) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Хотя бы одно поле должно измениться относительно текущего состояния", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalFlow = if (flowText.isNotEmpty()) parseDouble(flowText) ?: currentInFlow else currentInFlow
                    val finalAccrual = if (accrualText.isNotEmpty()) parseDouble(accrualText) ?: currentAccrual else currentAccrual
                    val finalWallet = if (walletText.isNotEmpty()) parseDouble(walletText) ?: currentWallet else currentWallet
                    onConfirm(finalFlow, finalAccrual, finalWallet, isButtonPressed)
                },
                enabled = isEnabled
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Диалог выбора даты для прогноза.
 *
 * @param onDismiss Закрытие диалога
 * @param onDateSelected Выбранная дата (timestamp)
 */
@Composable
fun ForecastDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                datePickerState.selectedDateMillis?.let { onDateSelected(it) }
            }) { Text("Выбрать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Диалог старта/реинвеста для Потока Новичка.
 * Поддерживает два режима: новый поток и действующий поток.
 *
 * @param onDismiss Закрытие диалога
 * @param onConfirm Подтверждение с данными потока
 * @param bonusPercent Бонус за взнос в процентах (из БД Настроек)
 * @param dailyPercent Ежедневный процент начислений (из БД Настроек)
 * @param isNewFlow true если это создание нового потока
 */
@Composable
fun NoviceReinvestDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double) -> Unit,
    bonusPercent: Double = 50.0,
    dailyPercent: Double = 2.0,
    isNewFlow: Boolean = false
) {
    var isExistingFlow by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var walletExplicitlySet by remember { mutableStateOf(false) }

    fun parseDouble(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    val amount = parseDouble(amountText)
    val inFlow = if (isExistingFlow) amount else amount + amount * bonusPercent / 100.0
    val dailyAccrual = inFlow * dailyPercent / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewFlow) "Старт ПН" else "Реинвест ПН", fontSize = 18.sp) },
        text = {
            Column {
                if (isExistingFlow) {
                    Text(
                        "Начисление: ${String.format(Locale.US, "%.2f", dailyAccrual)} руб.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    Text("Бонус ко взносу: ${String.format(Locale.US, "%.0f", bonusPercent)}%", fontSize = 12.sp, color = Color.Gray)
                    Text("Ежедневный процент: ${String.format(Locale.US, "%.0f", dailyPercent)}%", fontSize = 12.sp, color = Color.Gray)
                    if (amount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "В потоке: ${String.format(Locale.US, "%.2f", inFlow)} (+${String.format(Locale.US, "%.0f", bonusPercent)}%)",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            "Начисление: ${String.format(Locale.US, "%.2f", dailyAccrual)} руб.",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                if (isNewFlow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isExistingFlow,
                            onCheckedChange = { isExistingFlow = it }
                        )
                        Text("Поток действующий", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isExistingFlow) "В потоке" else "Сумма взноса *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletText,
                    onValueChange = {
                        walletText = it
                        walletExplicitlySet = it.isNotEmpty()
                    },
                    label = { Text("Текущий кошелек") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (walletExplicitlySet && walletText.isEmpty())
                                "Введите 0, чтобы обнулить кошелёк"
                            else if (!walletExplicitlySet)
                                "Оставьте поле пустым, если в кошельке пусто"
                            else
                                "Введите нужную сумму"
                        )
                    }
                )
            }
        },
        confirmButton = {
            val wallet = if (walletExplicitlySet) parseDouble(walletText) else 0.0
            Button(
                onClick = {
                    if (amount > 0) {
                        onConfirm(inFlow, dailyAccrual, wallet)
                    }
                },
                enabled = amount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) { Text("Внести") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Диалог корректировки значений ПН.
 * Обязательно должно быть изменено хотя бы одно поле относительно текущего состояния.
 * Начисление пересчитывается автоматически (2% от потока).
 *
 * @param onDismiss Закрытие диалога
 * @param onConfirm Подтверждение с новыми значениями
 * @param currentInFlow Текущее значение потока
 * @param currentDailyPercent Текущий дневной процент
 * @param currentWallet Текущий кошелёк
 * @param currentButtonPressed Текущее состояние кнопки
 */
@Composable
fun NoviceCorrectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double?, Boolean) -> Unit,
    currentInFlow: Double,
    currentDailyPercent: Double,
    currentWallet: Double,
    currentButtonPressed: Boolean
) {
    var flowText by remember { mutableStateOf("") }
    var walletText by remember { mutableStateOf("") }
    var walletExplicitlySet by remember { mutableStateOf(false) }
    var isButtonPressed by remember { mutableStateOf(currentButtonPressed) }

    fun parseDouble(text: String): Double? {
        return text.replace(",", ".").toDoubleOrNull()
    }

    val flowChanged = flowText.isNotEmpty() && parseDouble(flowText)?.let { it != currentInFlow } ?: false
    val walletChanged = walletText.isNotEmpty() && parseDouble(walletText)?.let { it != currentWallet } ?: false
    val checkboxChanged = isButtonPressed != currentButtonPressed

    val hasAnyChange = flowChanged || walletChanged || checkboxChanged
    val hasSomeInput = flowText.isNotEmpty() || walletText.isNotEmpty() || checkboxChanged

    val isEnabled = hasAnyChange && hasSomeInput

    val newFlowValue = parseDouble(flowText) ?: currentInFlow
    val newAccrual = newFlowValue * currentDailyPercent / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Корректировка ПН", fontSize = 18.sp) },
        text = {
            Column {
                Text("Процент начисления фиксирован: ${String.format(Locale.US, "%.2f%%", currentDailyPercent)}", fontSize = 11.sp, color = Color.Gray)
                Text("Начисление пересчитывается автоматически", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = flowText, onValueChange = { flowText = it },
                    label = { Text("В потоке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { 
                        Text(
                            "Текущее: ${String.format(Locale.US, "%.2f", currentInFlow)}",
                            fontSize = 10.sp, 
                            color = Color.Gray
                        ) 
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletText, onValueChange = { 
                        walletText = it
                        walletExplicitlySet = it.isNotEmpty()
                    },
                    label = { Text("В кошельке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { 
                        Text(
                            if (walletExplicitlySet && walletText.isEmpty()) 
                                "Введите 0, чтобы обнулить кошелёк"
                            else if (!walletExplicitlySet) 
                                "Оставьте пустым - кошелёк не изменится"
                            else 
                                "Введите нужную сумму"
                        ) 
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isButtonPressed,
                        onCheckedChange = { isButtonPressed = it }
                    )
                    Text("Кнопка нажата (было: ${if (currentButtonPressed) "да" else "нет"})")
                }
                if (!isEnabled && hasSomeInput && !hasAnyChange) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Хотя бы одно поле должно измениться относительно текущего состояния", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalFlow = if (flowText.isNotEmpty()) parseDouble(flowText) ?: currentInFlow else currentInFlow
                    val finalAccrual = finalFlow * currentDailyPercent / 100.0
                    onConfirm(
                        finalFlow,
                        finalAccrual,
                        if (walletExplicitlySet) parseDouble(walletText) else null,
                        isButtonPressed
                    )
                },
                enabled = isEnabled
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
