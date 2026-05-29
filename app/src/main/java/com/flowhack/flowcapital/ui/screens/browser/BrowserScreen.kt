package com.flowhack.flowcapital.ui.screens.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
 * Отображает WebView с указанным URL, поддерживает прокси (SOCKS5, MTProto),
 * перемещаемую кнопку обновления и управление куками.
 *
 * @param url URL для загрузки в WebView
 */
@SuppressLint("SetJavaScriptEnabled", "DEPRECATION")
@Composable
fun BrowserScreen(url: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val proxyStorage = remember { ProxyStorage(context) }
    val proxies by proxyStorage.proxiesFlow.collectAsState(initial = emptyList())

    val savedOffsetX by settingsManager.browserFabOffsetXFlow.collectAsState(initial = 400)
    val savedOffsetY by settingsManager.browserFabOffsetYFlow.collectAsState(initial = 16)

    var offsetX by remember { mutableFloatStateOf(savedOffsetX.toFloat()) }
    var offsetY by remember { mutableFloatStateOf(savedOffsetY.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(savedOffsetX, savedOffsetY) {
        offsetX = savedOffsetX.toFloat()
        offsetY = savedOffsetY.toFloat()
    }

    val siteName = when {
        url.contains("potok.cash") -> "ПОТОКCASH"
        url.contains("sberkassa") -> "СБЕРКАССА"
        url.contains("e-id") -> "E-ID"
        else -> null
    }

    val enabledProxies = remember(proxies, siteName) {
        if (siteName == null) emptyList()
        else proxies.filter {
            it.status == ProxyStatus.CONNECTED && siteName in it.enabledForSites
        }.sortedBy { it.pingMs ?: Int.MAX_VALUE }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            webViewClient = WebViewClient()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            loadUrl(url)
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView },
            update = { view ->
                if (view.url != url) {
                    view.loadUrl(url)
                }
            }
        )

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
                    onClick = { webView.reload() },
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
                                        settingsManager.saveBrowserFabOffset(offsetX.toInt(), offsetY.toInt())
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
