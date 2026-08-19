package com.flowhack.flowcapital.ui.screens.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.flowhack.flowcapital.data.proxy.ProxyConfig
import com.flowhack.flowcapital.data.proxy.ProxyStatus
import com.flowhack.flowcapital.data.proxy.ProxyStorage
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import androidx.webkit.ProxyConfig as WebKitProxyConfig

/**
 * Главный экран встроенного браузера.
 *
 * Отображает панель вкладок и активный WebView. Вкладки хранятся в
 * [BrowserViewModel] с живыми WebView, поэтому переключение между сайтами
 * не сбрасывает состояние страниц. Поддерживает прокси (SOCKS5, MTProto),
 * перемещаемую кнопку обновления и управление куками.
 *
 * @param viewModel ViewModel вкладок браузера
 */
@SuppressLint("SetJavaScriptEnabled", "DEPRECATION")
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val proxyStorage = remember { ProxyStorage(context) }
    val proxies by proxyStorage.proxiesFlow.collectAsState(initial = emptyList())

    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val activeTab = tabs.find { it.id == activeTabId }

    val savedOffsetX by settingsManager.browserFabOffsetXFlow.collectAsState(initial = 400)
    val savedOffsetY by settingsManager.browserFabOffsetYFlow.collectAsState(initial = 16)

    var offsetX by remember { mutableFloatStateOf(savedOffsetX.toFloat()) }
    var offsetY by remember { mutableFloatStateOf(savedOffsetY.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(savedOffsetX, savedOffsetY) {
        offsetX = savedOffsetX.toFloat()
        offsetY = savedOffsetY.toFloat()
    }

    val siteName = activeTab?.let { detectSiteName(it.url) }

    val enabledProxies = remember(proxies, siteName) {
        if (siteName == null) emptyList()
        else proxies.filter {
            it.status == ProxyStatus.CONNECTED && siteName in it.enabledForSites
        }.sortedBy { it.pingMs ?: Int.MAX_VALUE }
    }

    LaunchedEffect(enabledProxies, siteName) {
        val selectedProxy = enabledProxies.firstOrNull()
        applyProxyToWebView(selectedProxy)
    }

    DisposableEffect(Unit) {
        onDispose {
            clearProxy()
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tabs.isNotEmpty()) {
            BrowserTabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelect = viewModel::selectTab,
                onClose = viewModel::closeTab
            )
        }

        if (activeTab != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                key(activeTab.id) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            // Отцепляем WebView от прежнего родителя (поворот экрана,
                            // повторное использование вкладки), чтобы избежать
                            // исключения "child already has a parent".
                            (activeTab.webView.parent as? ViewGroup)?.removeView(activeTab.webView)
                            activeTab.webView
                        }
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 16.dp)
                            .offset {
                                IntOffset(offsetX.toInt(), offsetY.toInt())
                            }
                    ) {
                        FloatingActionButton(
                            onClick = { activeTab.webView.reload() },
                            containerColor = if (isDragging > 0) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .size(56.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { isDragging = 1f },
                                        onDragEnd = {
                                            isDragging = 0f
                                            scope.launch {
                                                settingsManager.saveBrowserFabOffset(
                                                    offsetX.toInt(),
                                                    offsetY.toInt()
                                                )
                                            }
                                        },
                                        onDragCancel = { isDragging = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            offsetX += dragAmount.x
                                            offsetY += dragAmount.y
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить страницу"
                            )
                        }
                    }
                }
            }
        } else {
            BrowserEmptyPlaceholder()
        }
    }
}

/**
 * Стартовый экран-заглушка браузера.
 *
 * Показывается, когда открытых вкладок нет. Поясняет пользователю, что для
 * выбора сайта нужно удержать кнопку «Браузер» в нижней панели.
 */
@Composable
private fun BrowserEmptyPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Выберите сайт",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Удерживайте кнопку «Браузер» в нижней панели, чтобы открыть список сайтов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun applyProxyToWebView(proxy: ProxyConfig?) {
    if (proxy == null) {
        clearProxy()
        return
    }

    val isProxyOverrideSupported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    if (!isProxyOverrideSupported) {
        Timber.tag("BrowserScreen").w("PROXY_OVERRIDE не поддерживается на этом устройстве")
        return
    }

    try {
        val proxyConfig = WebKitProxyConfig.Builder()
            .addProxyRule("socks://${proxy.server}:${proxy.port}")
            .build()

        val executor = Executors.newSingleThreadExecutor()
        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            executor
        ) {
            Timber.tag("BrowserScreen").d("Прокси применён: ${proxy.server}:${proxy.port}")
        }
    } catch (e: Exception) {
        Timber.tag("BrowserScreen").e("Ошибка применения прокси: ${e.message}")
    }
}

private fun clearProxy() {
    val isProxyOverrideSupported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    if (!isProxyOverrideSupported) {
        return
    }

    try {
        val executor = Executors.newSingleThreadExecutor()
        ProxyController.getInstance().clearProxyOverride(executor) {
            Timber.tag("BrowserScreen").d("Прокси очищен")
        }
    } catch (e: Exception) {
        Timber.tag("BrowserScreen").e("Ошибка очистки прокси: ${e.message}")
    }
}
