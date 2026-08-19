package com.flowhack.flowcapital.ui.screens.browser

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * ViewModel вкладок браузера.
 *
 * Держит по одному ЖИВОМУ [WebView] на вкладку. WebView сохраняет состояние
 * страницы (прокрутку, введённые данные, состояние форм), пока он жив, поэтому
 * переключение между вкладками не сбрасывает состояние сайта.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Вкладка браузера.
     *
     * @param id Уникальный идентификатор вкладки
     * @param url Адрес сайта
     * @param title Отображаемое название вкладки
     * @param webView Живой WebView вкладки
     */
    data class BrowserTab(
        val id: Long,
        val url: String,
        val title: String,
        val webView: WebView
    )

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())

    /** Список открытых вкладок. */
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)

    /** Идентификатор активной вкладки. */
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    private var nextId = 0L

    private var initialUrlOpened = false

    private val appContext get() = getApplication<Application>()

    /**
     * Открыть вкладку с URL из внешнего Intent.
     *
     * Выполняется один раз за время жизни ViewModel (защита от повторного
     * открытия при повороте экрана). Если вкладка с таким URL уже открыта —
     * просто активируется.
     *
     * @param url Адрес сайта или null, если URL не передан
     */
    fun openInitialUrl(url: String?) {
        if (url == null || initialUrlOpened) return
        initialUrlOpened = true
        openTab(url)
    }

    /**
     * Открыть вкладку с указанным URL.
     *
     * Если вкладка с таким URL уже существует — активирует её, иначе создаёт новую.
     *
     * @param url Адрес сайта
     */
    fun openTab(url: String) {
        val existing = _tabs.value.find { it.url == url }
        if (existing != null) {
            selectTab(existing.id)
            return
        }

        val webView = WebView(appContext).apply {
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Timber.tag("BrowserViewModel").e(
                        "Ошибка загрузки ${view?.url}: код=${error?.errorCode} " +
                            "описание=${error?.description}"
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Timber.tag("BrowserViewModel").e(
                        "HTTP-ошибка ${errorResponse?.statusCode} для ${request?.url}"
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.tag("BrowserViewModel").d("Страница загружена: $url")
                }
            }
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

        val tab = BrowserTab(
            id = nextId++,
            url = url,
            title = detectSiteName(url) ?: url,
            webView = webView
        )
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
    }

    /**
     * Активировать вкладку по идентификатору.
     *
     * @param id Идентификатор вкладки
     */
    fun selectTab(id: Long) {
        if (_tabs.value.any { it.id == id }) {
            _activeTabId.value = id
        }
    }

    /**
     * Закрыть вкладку по идентификатору.
     *
     * Если закрывается активная вкладка — активируется соседняя. При закрытии
     * последней вкладки список становится пустым (показывается стартовый
     * экран-заглушка). WebView уничтожается после того, как Compose уберёт его
     * из композиции.
     *
     * @param id Идентификатор вкладки
     */
    fun closeTab(id: Long) {
        val tabs = _tabs.value
        val tab = tabs.find { it.id == id } ?: return
        val remaining = tabs.filterNot { it.id == id }

        if (_activeTabId.value == id) {
            if (remaining.isNotEmpty()) {
                val index = tabs.indexOfFirst { it.id == id }
                val neighbor = if (index > 0) tabs[index - 1] else tabs[index + 1]
                _activeTabId.value = neighbor.id
            } else {
                _activeTabId.value = null
            }
        }

        _tabs.value = remaining
        // Даём Compose убрать WebView из композиции перед уничтожением.
        tab.webView.post { tab.webView.destroy() }
    }

    override fun onCleared() {
        _tabs.value.forEach { it.webView.destroy() }
        super.onCleared()
    }
}
