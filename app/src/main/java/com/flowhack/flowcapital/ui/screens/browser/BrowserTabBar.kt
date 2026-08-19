package com.flowhack.flowcapital.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowhack.flowcapital.ui.screens.browser.BrowserViewModel.BrowserTab
import kotlinx.coroutines.launch

/**
 * Панель вкладок браузера.
 *
 * Отображает горизонтальный ряд вкладок (иконка сайта + название + крестик
 * закрытия). Активная вкладка выделена. При переполнении полосы появляются
 * стрелки для листания вкладок влево/вправо.
 *
 * @param tabs Список вкладок
 * @param activeTabId Идентификатор активной вкладки
 * @param onSelect Обработчик выбора вкладки
 * @param onClose Обработчик закрытия вкладки
 */
@Composable
fun BrowserTabBar(
    tabs: List<BrowserTab>,
    activeTabId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Стрелка влево — появляется, когда есть вкладки слева за пределами экрана.
        if (listState.canScrollBackward) {
            IconButton(onClick = {
                val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                scope.launch { listState.animateScrollToItem(target) }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Листать вкладки влево"
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
        ) {
            items(tabs.size, key = { tabs[it].id }) { index ->
                val tab = tabs[index]
                val isActive = tab.id == activeTabId
                BrowserTabChip(
                    tab = tab,
                    isActive = isActive,
                    onClick = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) }
                )
            }
        }

        // Стрелка вправо — появляется, когда есть вкладки справа за пределами экрана.
        if (listState.canScrollForward) {
            IconButton(onClick = {
                val target = (listState.firstVisibleItemIndex + 1)
                    .coerceAtMost((tabs.size - 1).coerceAtLeast(0))
                scope.launch { listState.animateScrollToItem(target) }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Листать вкладки вправо"
                )
            }
        }
    }
}

/**
 * Отдельная вкладка-чип в панели вкладок.
 *
 * @param tab Вкладка
 * @param isActive Признак активной вкладки
 * @param onClick Обработчик выбора вкладки
 * @param onClose Обработчик закрытия вкладки
 */
@Composable
private fun BrowserTabChip(
    tab: BrowserTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val background = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconRes = siteIconRes(tab.url)
        if (iconRes != null) {
            // Светлая круглая подложка, чтобы тёмные логотипы (например, BlackBit)
            // были видны на любом фоне вкладки.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = tab.title,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть вкладку",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
