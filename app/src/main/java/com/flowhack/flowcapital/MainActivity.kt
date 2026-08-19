@file:Suppress("UNUSED_VALUE", "SpellCheckingInspection")

package com.flowhack.flowcapital

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import com.flowhack.flowcapital.data.update.UpdateChecker
import com.flowhack.flowcapital.notifications.rescheduleSavedReminders
import com.flowhack.flowcapital.ui.screens.browser.BrowserScreen
import com.flowhack.flowcapital.ui.screens.calculator.CalculatorScreen
import com.flowhack.flowcapital.ui.screens.settings.SettingsScreen
import com.flowhack.flowcapital.ui.theme.FlowCapitalTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Главная Activity приложения.
 * Инициализирует splash screen, проверяет обновления и отображает основной интерфейс.
 */
class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    /**
     * URL сайта, переданный через внешний Intent (например, при открытии ссылки
     * на один из поддерживаемых сайтов через системный выбор приложения).
     * Используется для открытия вкладки «Браузер» с нужным адресом.
     */
    private var pendingBrowserUrl by mutableStateOf<String?>(null)

    /**
     * Лаунчер пакетного запроса runtime-разрешений при первом запуске.
     * - POST_NOTIFICATIONS (Android 13+) — для push-уведомлений
     * - SCHEDULE_EXACT_ALARM (Android 13+) — для точных будильников
     * Если SCHEDULE_EXACT_ALARM не дано — AlarmManager использует inexact fallback
     * (setAndAllowWhileIdle), кнопка разрешения остаётся в Настройках.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // POST_NOTIFICATIONS доступна с API 33, SCHEDULE_EXACT_ALARM — с API 31.
        // Гарды по версии SDK: на старых устройствах ключей в карте просто нет.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            if (!notificationsGranted) {
                AppLogger.d("MainActivity", "POST_NOTIFICATIONS — отказано")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactAlarmGranted = permissions[Manifest.permission.SCHEDULE_EXACT_ALARM] ?: false
            if (!exactAlarmGranted) {
                AppLogger.d("MainActivity", "SCHEDULE_EXACT_ALARM — отказано (будет fallback на inexact)")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        AppLogger.init(this)
        settingsManager = SettingsManager(this)

        pendingBrowserUrl = extractBrowserUrl(intent)

        val scope = CoroutineScope(Dispatchers.Main)
        settingsManager.initializePspCache(scope)
        settingsManager.initializeERubCache(scope)

        scope.launch {
            settingsManager.initializeDefaults()
            settingsManager.setSkippedVersion(null)
            rescheduleSavedReminders(this@MainActivity, settingsManager)
        }

        requestPermissionsOnFirstLaunch()

        setContent {
            val darkTheme by settingsManager.darkThemeFlow.collectAsState(initial = true)
            FlowCapitalTheme(darkTheme = darkTheme) {
                val defaultEntryTab by settingsManager.defaultEntryTabFlow.collectAsState(initial = 1)
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(500)
                    showSplash = false
                }

                if (showSplash) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_splashscreen),
                            contentDescription = null,
                            modifier = Modifier.size(300.dp)
                        )
                    }
                } else {
                    UpdateChecker(
                        owner = "FlowHack",
                        repo = "flow-capital-android",
                        settingsManager = settingsManager
                    )
                    MainScreen(defaultEntryTab = defaultEntryTab, initialBrowserUrl = pendingBrowserUrl)
                }
            }
        }
    }

    /**
     * Обработка нового Intent при уже запущенном приложении (например, повторное
     * открытие ссылки на поддерживаемый сайт через системный выбор приложения).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBrowserUrl = extractBrowserUrl(intent)
    }

    /**
     * Извлечь URL поддерживаемого сайта из Intent.
     * Принимаются только http/https ссылки; остальные игнорируются.
     *
     * @param intent Входящий Intent
     * @return URL сайта или null, если ссылка не подходит
     */
    private fun extractBrowserUrl(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val scheme = data.scheme
        if (scheme != "https" && scheme != "http") return null
        return data.toString()
    }

    /**
     * Запросить runtime-разрешения при запуске (Android 13+).
     * POST_NOTIFICATIONS запрашивается всегда.
     * SCHEDULE_EXACT_ALARM не запрашивается здесь, так как это special app access,
     * который не работает через requestPermissions. Пользователь должен выдать его
     * вручную через системные настройки (кнопка в Настройках приложения).
     */
    private fun requestPermissionsOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}

/** Данные веб-сайтов для меню браузера */
data class WebSite(val name: String, val url: String, val iconRes: Int)

/** Список доступных сайтов в меню */
val sites = listOf(
    WebSite("ПОТОКCASH", "https://potok.cash/cabinet", R.drawable.logo_potok),
    WebSite("СБЕРКАССА", "https://sberkassa.site/account", R.drawable.logo_sberkassa),
    WebSite("E-ID", "https://e-id.cards/", R.drawable.logo_eid),
    WebSite("BLACKBIT", "https://blackbit.exchange/", R.drawable.logo_blackbit),
    WebSite("ERUB", "https://erub.site/", R.drawable.logo_erub)
)

/**
 * Элемент нижней навигации.
 *
 * @param route Маршрут экрана
 * @param title Название вкладки
 * @param icon Иконка
 */
sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    data object Browser : BottomNavItem("browser", "Браузер", Icons.Default.Language)
    data object Calculator : BottomNavItem("calculator", "Расчёты", Icons.Default.AccountBalanceWallet)
    data object Settings : BottomNavItem("settings", "Настройки", Icons.Default.Settings)
}

/**
 * Главный экран приложения с нижней навигацией.
 * Содержит три вкладки: Браузер, Расчёты, Настройки.
 * Вкладка Браузер поддерживает меню по долгому нажатию для выбора сайта.
 * @param defaultEntryTab Вкладка для открытия по умолчанию (0=Браузер, 1=Расчёты, 2=Настройки)
 * @param initialBrowserUrl URL сайта для открытия во вкладке «Браузер» (если передан через Intent)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(defaultEntryTab: Int = 1, initialBrowserUrl: String? = null) {
    val navController = rememberNavController()
    var currentWebUrl by remember { mutableStateOf(initialBrowserUrl ?: sites[0].url) }
    var showBrowserMenu by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Определяем начальный маршрут на основе настройки или переданного URL
    val startRoute = remember(defaultEntryTab, initialBrowserUrl) {
        if (initialBrowserUrl != null) {
            BottomNavItem.Browser.route
        } else {
            when (defaultEntryTab) {
                0 -> BottomNavItem.Browser.route
                1 -> BottomNavItem.Calculator.route
                2 -> BottomNavItem.Settings.route
                else -> BottomNavItem.Calculator.route
            }
        }
    }

    // Callback для открытия URL из настроек
    val openBrowserUrl: (String) -> Unit = { url ->
        currentWebUrl = url
        navController.navigate(BottomNavItem.Browser.route) {
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {

                // Вкладка БРАУЗЕР
                val isBrowser = currentRoute == BottomNavItem.Browser.route
                NavigationBarItem(
                    selected = isBrowser,
                    onClick = {
                        navController.navigate(BottomNavItem.Browser.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Box(contentAlignment = Alignment.BottomStart) {
                            Icon(
                                imageVector = BottomNavItem.Browser.icon,
                                contentDescription = null,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        navController.navigate(BottomNavItem.Browser.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onLongClick = { if (isBrowser) showBrowserMenu = true }
                                )
                            )
                            // Выпадающее меню с сайтами
                            DropdownMenu(
                                expanded = showBrowserMenu,
                                onDismissRequest = { showBrowserMenu = false },
                                offset = DpOffset(x = (-40).dp, y = (-8).dp)
                            ) {
                                sites.forEach { site ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = site.iconRes),
                                                contentDescription = null,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        text = { Text(site.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            currentWebUrl = site.url
                                            showBrowserMenu = false
                                            navController.navigate(BottomNavItem.Browser.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    label = { Text(BottomNavItem.Browser.title) }
                )

                // Вкладка РАСЧЁТЫ
                NavigationBarItem(
                    selected = currentRoute == BottomNavItem.Calculator.route,
                    onClick = {
                        navController.navigate(BottomNavItem.Calculator.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(BottomNavItem.Calculator.icon, null) },
                    label = { Text(BottomNavItem.Calculator.title) }
                )

                // Вкладка НАСТРОЙКИ
                NavigationBarItem(
                    selected = currentRoute == BottomNavItem.Settings.route,
                    onClick = {
                        navController.navigate(BottomNavItem.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(BottomNavItem.Settings.icon, null) },
                    label = { Text(BottomNavItem.Settings.title) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, startRoute, Modifier.padding(innerPadding)) {
            composable(BottomNavItem.Browser.route) { BrowserScreen(currentWebUrl) }
            composable(BottomNavItem.Calculator.route) { CalculatorScreen() }
            composable(BottomNavItem.Settings.route) { SettingsScreen(onOpenBrowserUrl = openBrowserUrl) }
        }
    }
}
