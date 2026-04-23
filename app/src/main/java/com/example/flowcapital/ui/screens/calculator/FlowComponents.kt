package com.example.flowcapital.ui.screens.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowcapital.data.db.GrowingFlowEntity
import com.example.flowcapital.data.db.NoviceFlowEntity
import com.example.flowcapital.ui.theme.FlowColors
import com.example.flowcapital.ui.screens.calculator.isNarrowScreen
import java.text.SimpleDateFormat
import java.util.*

/**
 * Компонент вкладок для выбора потока.
 * Отображает краткие названия (ПН, БП, ПСП, РП, НП) и полное название под вкладками.
 *
 * @param selectedTabIndex Индекс выбранной вкладки
 * @param tabs Список кратких названий
 * @param fullNames Список полных названий потоков
 * @param onTabSelected Callback при выборе вкладки
 */
@Composable
fun FlowTabs(
    selectedTabIndex: Int,
    tabs: List<String>,
    fullNames: List<String>,
    onTabSelected: (Int) -> Unit
) {
    val flowColor = FlowColors.getColorForIndex(selectedTabIndex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 12.dp),
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
 * Таблица истории Растущего Потока.
 * Показывает дату, шаг, процент, сумму в потоке, начисление и кошелёк.
 * Строки окрашиваются по типу действия (SUNDAY, CORRECTION, REINVEST и т.д.)
 *
 * @param history Список записей истории
 */
@Composable
fun HistoryTable(history: List<GrowingFlowEntity>) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val wideScreen = isWideScreen()

    val historyWithStep = remember(history) {
        var step = 0
        history.map { entry ->
            val isActiveAction = entry.actionType in listOf("START", "REINVEST", "DAILY")
            if (isActiveAction) step++
            entry to if (isActiveAction) step else 0
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Дата", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("%", modifier = Modifier.weight(0.8f), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("В потоке", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("Начисление", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
            if (wideScreen) {
                Text("Кошелек", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
        historyWithStep.forEach { (entry, step) ->
            val backgroundColor = when (entry.actionType) {
                "SUNDAY" -> Color(0x33E040FB)
                "CORRECTION" -> Color(0x33FF5252)
                "REINVEST", "START" -> Color(0x334CAF50)
                "MISSED" -> Color(0x33FF9800)
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
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
                Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                    Text(
                        String.format(Locale.US, "%.3f", entry.percent),
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
                Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                    Text(
                        String.format(Locale.US, "+%.2f", entry.dailyAccrual),
                        color = if (entry.actionType == "SUNDAY") Color.Gray else Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (wideScreen) {
                    Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                        Text(
                            String.format(Locale.US, "%.2f", entry.walletAmount),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
    }
}

/**
 * Карточка с текущей статистикой РП.
 * Показывает процент, сумму в потоке, начисление и кошелёк.
 *
 * @param entry Последняя запись истории (null если истории нет)
 */
@Composable
fun CurrentStatsCard(entry: GrowingFlowEntity?) {
    val wideScreen = isWideScreen()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("%", if (entry != null) String.format(Locale.US, "%.3f", entry.percent) else "0.000")
            StatItem("В потоке", if (entry != null) String.format(Locale.US, "%.2f", entry.inFlowAmount) else "0.00")
            if (wideScreen) {
                StatItem("Начисление", if (entry != null) String.format(Locale.US, "%.2f", entry.dailyAccrual) else "0.00")
            }
            StatItem("Кошелек", if (entry != null) String.format(Locale.US, "%.2f", entry.walletAmount) else "0.00")
        }
    }
}

/**
 * Элемент статистики: заголовок + значение.
 *
 * @param title Подпись (%, Поток, Начисл., Кошелек)
 * @param value Отформатированное значение
 */
@Composable
fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Строка кнопок действий для Потока Новичка.
 *
 * @param onReinvestClick Нажатие на Реинвест
 * @param onCorrectionClick Нажатие на Коррекция
 * @param onForecastClick Нажатие на Прогноз
 * @param onCycleEndClick Нажатие на Конец цикла
 */
@Composable
fun NoviceActionButtonsRow(
    onReinvestClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onForecastClick: () -> Unit,
    onCycleEndClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReinvestClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Реинвест", fontSize = 12.sp) }

            OutlinedButton(
                onClick = onCorrectionClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Коррекция", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onForecastClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Прогноз", fontSize = 12.sp) }

            OutlinedButton(
                onClick = onCycleEndClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Конец цикла", fontSize = 12.sp, color = FlowColors.PN_COLOR) }
        }
    }
}

/**
 * Контент экрана Потока Новичка.
 * Показывает кнопку старта (если истории нет) или полный интерфейс с кнопками и историей.
 *
 * @param lastEntry Последняя запись истории
 * @param history Вся история
 * @param onReinvestClick Нажатие на Старт/Реинвест
 * @param onCorrectionClick Нажатие на Коррекция
 * @param onForecastClick Нажатие на Прогноз
 * @param onCycleEndClick Нажатие на Конец цикла
 * @param onDailyButtonClick Нажатие на кнопку "Я сегодня нажал"
 */
@Composable
fun NoviceFlowContent(
    lastEntry: NoviceFlowEntity?,
    history: List<NoviceFlowEntity>,
    onReinvestClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onForecastClick: () -> Unit,
    onCycleEndClick: () -> Unit,
    onDailyButtonClick: () -> Unit
) {
    val hasHistory = history.isNotEmpty()

    if (!hasHistory) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onReinvestClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlowColors.PN_COLOR
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Думаю, стоит завести Поток Новичка!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    NoviceActionButtonsRow(
        onReinvestClick = onReinvestClick,
        onCorrectionClick = onCorrectionClick,
        onForecastClick = onForecastClick,
        onCycleEndClick = onCycleEndClick
    )

    Spacer(modifier = Modifier.height(12.dp))
    NoviceStatsCard(lastEntry)
    Spacer(modifier = Modifier.height(12.dp))

    val isSunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val today = Calendar.getInstance()
    val isActionDoneToday = history.any { entry ->
        val entryDate = Calendar.getInstance().apply { timeInMillis = entry.date }
        val isToday = today.get(Calendar.YEAR) == entryDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == entryDate.get(Calendar.DAY_OF_YEAR)
        isToday && entry.isButtonPressed && entry.actionType != "SUNDAY"
    }
    val isFlowZero = lastEntry?.inFlowAmount ?: 0.0 <= 0
    val isButtonDisabled = isSunday || isActionDoneToday || isFlowZero

    val buttonContainerColor = when {
        isFlowZero -> Color(0xFF333333)
        isSunday -> Color(0xFF9C27B0)
        else -> FlowColors.PN_COLOR
    }
    val buttonContentColor = when {
        isFlowZero -> Color.LightGray
        isSunday -> Color.White
        else -> Color.White
    }

    Button(
        onClick = onDailyButtonClick,
        enabled = !isButtonDisabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor,
            disabledContainerColor = Color(0xFF333333),
            disabledContentColor = Color.LightGray
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(50.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = when {
                isFlowZero -> "СДЕЛАЙТЕ РЕИНВЕСТ"
                isSunday -> "ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ"
                isActionDoneToday -> "НАЧИСЛЕНИЕ ВЫПОЛНЕНО"
                else -> "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = buttonContentColor
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    NoviceHistoryTable(history)
}

/**
 * Карточка с текущей статистикой ПН.
 *
 * @param entry Последняя запись истории (null если истории нет)
 */
@Composable
fun NoviceStatsCard(entry: NoviceFlowEntity?) {
    val wideScreen = isWideScreen()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("%", if (entry != null) String.format(Locale.US, "%.2f", entry.percent) else "0.00")
            StatItem("В потоке", if (entry != null) String.format(Locale.US, "%.2f", entry.inFlowAmount) else "0.00")
            if (wideScreen) {
                StatItem("Начисление", if (entry != null) String.format(Locale.US, "%.2f", entry.dailyAccrual) else "0.00")
            }
            StatItem("Кошелек", if (entry != null) String.format(Locale.US, "%.2f", entry.walletAmount) else "0.00")
        }
    }
}

/**
 * Таблица истории Потока Новичка.
 *
 * @param history Список записей истории
 */
@Composable
fun NoviceHistoryTable(history: List<NoviceFlowEntity>) {
    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    val wideScreen = isWideScreen()

    val historyWithStep = remember(history) {
        var step = 0
        history.map { entry ->
            val isActiveAction = entry.actionType in listOf("START", "REINVEST", "DAILY")
            if (isActiveAction) step++
            entry to if (isActiveAction) step else 0
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Дата", modifier = Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("В потоке", modifier = Modifier.weight(1.4f), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("Начисление", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
            if (wideScreen) {
                Text("Кошелек", modifier = Modifier.weight(1.2f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
        historyWithStep.forEach { (entry, step) ->
            val backgroundColor = when (entry.actionType) {
                "SUNDAY" -> Color(0x33E040FB)
                "CORRECTION", "PN_CORRECTION" -> Color(0x33FF5252)
                "REINVEST", "START", "PN_REINVEST", "PN_START" -> Color(0x334CAF50)
                "MISSED" -> Color(0x33FF9800)
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
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
                Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                    Text(
                        String.format(Locale.US, "+%.2f", entry.dailyAccrual),
                        color = if (entry.actionType == "SUNDAY") Color.Gray else Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (wideScreen) {
                    Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                        Text(
                            String.format(Locale.US, "%.2f", entry.walletAmount),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
    }
}

/**
 * Контент экрана Растущего Потока.
 *
 * @param lastEntry Последняя запись истории
 * @param history Вся история
 * @param onReinvestClick Нажатие на Старт/Реинвест
 * @param onCorrectionClick Нажатие на Коррекция
 * @param onForecastClick Нажатие на Прогноз
 * @param onBestDateClick Нажатие на Лучшая дата
 * @param onDailyButtonClick Нажатие на кнопку "Я сегодня нажал"
 */
@Composable
fun GrowingFlowContent(
    lastEntry: GrowingFlowEntity?,
    history: List<GrowingFlowEntity>,
    onReinvestClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onForecastClick: () -> Unit,
    onBestDateClick: () -> Unit,
    onDailyButtonClick: () -> Unit
) {
    val hasHistory = history.isNotEmpty()

    if (!hasHistory) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onReinvestClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlowColors.RP_COLOR
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Думаю, стоит завести Растущий Поток!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReinvestClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Реинвест", fontSize = 12.sp) }

            OutlinedButton(
                onClick = onCorrectionClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Коррекция", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onForecastClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Прогноз", fontSize = 12.sp) }

            OutlinedButton(
                onClick = onBestDateClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Лучшая дата", fontSize = 12.sp, color = FlowColors.RP_COLOR) }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    CurrentStatsCard(lastEntry)
    Spacer(modifier = Modifier.height(12.dp))

    val isSunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val today = Calendar.getInstance()
    val isActionDoneToday = history.any { entry ->
        val entryDate = Calendar.getInstance().apply { timeInMillis = entry.date }
        val isToday = today.get(Calendar.YEAR) == entryDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == entryDate.get(Calendar.DAY_OF_YEAR)
        isToday && entry.isButtonPressed && entry.actionType != "SUNDAY"
    }
    val isFlowZero = lastEntry?.inFlowAmount ?: 0.0 <= 0
    val isButtonDisabled = isSunday || isActionDoneToday || isFlowZero

    Button(
        onClick = onDailyButtonClick,
        enabled = !isButtonDisabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color(0xFF333333),
            disabledContentColor = Color.LightGray
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(50.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = when {
                isFlowZero -> "СДЕЛАЙТЕ РЕИНВЕСТ"
                isSunday -> "ВОСКРЕСЕНЬЕ - ВЫХОДНОЙ"
                isActionDoneToday -> "НАЧИСЛЕНИЕ ВЫПОЛНЕНО"
                else -> "Я СЕГОДНЯ НАЖАЛ НА КНОПКУ"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    HistoryTable(history)
}

/**
 * Заглушка для нереализованных потоков (Быстрый Поток, Накопительный Поток).
 * Выводится по центру экрана.
 *
 * @param flowName Название потока
 */
@Composable
fun PlaceholderFlowContent(flowName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "В разработке. Скоро будет!",
            color = Color.Gray,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
