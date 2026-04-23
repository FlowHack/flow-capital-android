package com.example.flowcapital.data.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.flowcapital.BuildConfig
import com.example.flowcapital.data.logging.AppLogger
import com.example.flowcapital.data.settings.SettingsManager
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
    val body: String,
    val apkAsset: ApkAsset? = null
)

/**
 * Глобальный менеджер состояния обновлений.
 * Единый источник истины для всех компонентов приложения.
 */
object GlobalUpdateManager {
    val releaseState = MutableStateFlow<GitHubRelease?>(null)
    var hasDialogBeenShown = false
        private set

    fun markDialogShown() {
        hasDialogBeenShown = true
    }

    fun resetSession() {
        releaseState.value = null
        hasDialogBeenShown = false
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckResult()
    data object NoUpdate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Проверка обновлений на GitHub.
 * Ищет APK файл в последнем релизе.
 *
 * @param owner Владелец репозитория
 * @param repo Название репозитория
 * @return Результат проверки обновлений
 */
suspend fun checkForUpdate(owner: String, repo: String): UpdateCheckResult {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FlowCapital-Android")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode

            Timber.tag("UpdateChecker").d("Проверка обновлений: ответ=$responseCode")

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().readText()

                    val tagName = extractJsonValue(response, "tag_name")
                    val htmlUrl = extractJsonValue(response, "html_url")
                    val body = extractJsonBody(response)

                    if (tagName.isBlank()) {
                        Timber.tag("UpdateChecker").w("Не удалось прочитать tag_name из ответа")
                        return@withContext UpdateCheckResult.Error("Не удалось прочитать данные релиза")
                    }

                    val apkAsset = extractApkAsset(response)

                    val release = GitHubRelease(
                        tagName = tagName,
                        versionName = tagName.removePrefix("v"),
                        htmlUrl = htmlUrl,
                        body = body,
                        apkAsset = apkAsset
                    )

                    val currentVersion = BuildConfig.VERSION_NAME
                    Timber.tag("UpdateChecker").d("Текущая версия: $currentVersion, найдена: ${release.versionName}")
                    if (isNewerVersion(release.versionName, currentVersion)) {
                        UpdateCheckResult.UpdateAvailable(release)
                    } else {
                        UpdateCheckResult.NoUpdate
                    }
                }
                403 -> UpdateCheckResult.Error("Достигнут лимит запросов. Попробуйте позже.")
                404 -> UpdateCheckResult.Error("Релизы не найдены")
                else -> UpdateCheckResult.Error("Ошибка сервера: $responseCode")
            }
        } catch (e: Exception) {
            Timber.tag("UpdateChecker").e("Ошибка проверки обновлений: ${e.localizedMessage}")
            AppLogger.e("UpdateChecker", "Ошибка проверки обновлений: ${e.localizedMessage}", e)
            UpdateCheckResult.Error("Ошибка сети: ${e.localizedMessage ?: "Проверьте интернет"}")
        }
    }
}

/**
 * Извлекает информацию о APK файле из JSON ответа GitHub API.
 * Согласно ТЗ ищет файл вида "FlowCapital_v{version}.apk" или "FlowCapital*.apk"
 *
 * @param json JSON ответ от GitHub API
 * @return ApkAsset с информацией о файле, или null если не найден
 */
private fun extractApkAsset(json: String): ApkAsset? {
    val assetsStart = json.indexOf("\"assets\"")
    if (assetsStart == -1) return null

    val arrayStart = json.indexOf("[", assetsStart)
    val arrayEnd = findMatchingBracket(json, arrayStart)
    if (arrayStart == -1 || arrayEnd == -1) return null

    val assetsJson = json.substring(arrayStart + 1, arrayEnd)

    var pos = 0
    while (pos < assetsJson.length) {
        val objStart = assetsJson.indexOf("{", pos)
        if (objStart == -1) break

        val objEnd = findMatchingBracket(assetsJson, objStart)
        if (objEnd == -1) break

        val assetJson = assetsJson.substring(objStart, objEnd + 1)

        val name = extractJsonValue(assetJson, "name")
        val url = extractJsonValue(assetJson, "browser_download_url")
        val sizeStr = extractJsonValue(assetJson, "size")
        val size = sizeStr.toLongOrNull() ?: -1L

        if (name.isNotBlank() && url.isNotBlank() && name.endsWith(".apk", ignoreCase = true)) {
            if (name.contains("FlowCapital", ignoreCase = true)) {
                return ApkAsset(
                    downloadUrl = url,
                    fileName = name,
                    fileSize = size
                )
            }
        }

        pos = objEnd + 1
    }

    return null
}

/**
 * Поиск закрывающей скобки для объекта или массива в JSON.
 * Использует ручной парсинг с отслеживанием depth (вложенность скобок)
 * и состояния inString (внутри кавычек для правильной обработки вложенных структур).
 *
 * @param json JSON строка
 * @param start Позиция открывающей скобки ({ или [)
 * @return Позиция закрывающей скобки или -1 если не найдена
 */
private fun findMatchingBracket(json: String, start: Int): Int {
    if (start < 0 || start >= json.length) return -1

    val opening = json[start]
    val closing = when (opening) {
        '{' -> '}'
        '[' -> ']'
        else -> return -1
    }

    var depth = 0
    var inString = false
    var escape = false

    for (i in start until json.length) {
        val c = json[i]

        when {
            escape -> {
                escape = false
            }
            c == '\\' && inString -> {
                escape = true
            }
            c == '"' -> {
                inString = !inString
            }
            !inString -> {
                when (c) {
                    opening -> depth++
                    closing -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
    }
    return -1
}

private fun extractJsonValue(json: String, field: String): String {
    val searchFor = "\"$field\""
    val fieldIdx = json.indexOf(searchFor)
    if (fieldIdx == -1) return ""
    
    val colonIdx = json.indexOf(":", fieldIdx)
    if (colonIdx == -1) return ""
    
    var i = colonIdx + 1
    while (i < json.length && json[i] == ' ') i++
    if (i >= json.length || json[i] != '"') return ""
    i++ // пропуск открывающей кавычки
    
    val result = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        when {
            json.substring(i).startsWith("\\n") -> { result.append('\n'); i += 2 }
            json.substring(i).startsWith("\\\"") -> { result.append('"'); i += 2 }
            c == '"' -> return result.toString()
            else -> { result.append(c); i++ }
        }
    }
    return result.toString()
}

private fun extractJsonBody(json: String): String {
    val bodyStart = json.indexOf("body")
    if (bodyStart == -1) return ""
    
    val colonIdx = json.indexOf(":", bodyStart)
    if (colonIdx == -1) return ""
    
    var i = colonIdx + 1
    while (i < json.length && json[i] == ' ') i++
    if (i >= json.length || json[i] != '"') return ""
    i++ // пропуск открывающей кавычки
    
    val result = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        when {
            json.substring(i).startsWith("\\n") -> { result.append('\n'); i += 2 }
            json.substring(i).startsWith("\\r") -> { i += 2 }
            json.substring(i).startsWith("\\\"") -> { result.append('"'); i += 2 }
            c == '"' -> return result.toString()
            else -> { result.append(c); i++ }
        }
    }
    return result.toString()
}

private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
    try {
        val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

        val maxSize = maxOf(newParts.size, currentParts.size)

        for (i in 0 until maxSize) {
            val newPart = newParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                newPart > currentPart -> return true
                newPart < currentPart -> return false
            }
        }
        return false
    } catch (e: Exception) {
        return false
    }
}

/**
 * Парсит Markdown-подобный текст в AnnotatedString.
 * Поддерживает: заголовки (#, ##, ###), буллиты (-, *), нумерованные списки,
 * жирный (**), курсив (*), цитаты (>), многострочные блоки цитат.
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            when {
                line.startsWith("###") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(line.removePrefix("###").trim())
                    pop()
                    if (i < lines.size - 1) appendLine()
                }
                line.startsWith("##") -> {
                    if (i > 0) appendLine()
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(line.removePrefix("##").trim())
                    pop()
                    if (i < lines.size - 1) appendLine()
                }
                line.startsWith("#") -> {
                    if (i > 0) appendLine()
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(line.removePrefix("#").trim())
                    pop()
                    if (i < lines.size - 1) appendLine()
                }
                line.startsWith(">") -> {
                    var quoteLine = line.removePrefix(">")
                    while (i + 1 < lines.size && lines[i + 1].startsWith(">")) {
                        i++
                        quoteLine += "\n" + lines[i].removePrefix(">").trim()
                    }
                    pushStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append("│ ")
                    parseInlineMarkdown(quoteLine.trim())
                    pop()
                    if (i < lines.size - 1) appendLine()
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.substring(2)
                    append("• ")
                    parseInlineMarkdown(content)
                    if (i < lines.size - 1) appendLine()
                }
                line.matches(Regex("^\\d+\\..*")) -> {
                    parseInlineMarkdown(line)
                    if (i < lines.size - 1) appendLine()
                }
                line.isBlank() -> {
                    if (i < lines.size - 1) appendLine()
                }
                else -> {
                    parseInlineMarkdown(line)
                    if (i < lines.size - 1) appendLine()
                }
            }
            i++
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val endIdx = text.indexOf("**", i + 2)
                if (endIdx != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, endIdx))
                    pop()
                    i = endIdx + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val endIdx = text.indexOf("*", i + 1)
                if (endIdx != -1 && !text.startsWith("**", endIdx)) {
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(text.substring(i + 1, endIdx))
                    pop()
                    i = endIdx + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    settingsManager: SettingsManager? = null,
    apkDownloader: ApkDownloader? = null,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onSkipVersion: (String) -> Unit,
    onSkipAutoUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val checkOnStart by settingsManager?.checkUpdateOnStartFlow?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    val downloadState by apkDownloader?.downloadState?.collectAsState() ?: remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Доступно обновление!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Новая версия: ${release.versionName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Текущая версия: ${BuildConfig.VERSION_NAME}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Что нового:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = parseMarkdown(release.body),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = !checkOnStart,
                        onCheckedChange = { checked ->
                            scope.launch {
                                settingsManager?.setCheckUpdateOnStart(!checked)
                            }
                        }
                    )
                    Text(
                        text = "Не проверять автоматически",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (downloadState) {
                    is DownloadState.Downloading -> {
                        val progress = (downloadState as DownloadState.Downloading).progress
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Скачивается: $progress%",
                                fontSize = 12.sp
                            )
                        }
                    }
                    is DownloadState.Success -> {
                        Text(
                            text = "Обновление скачано",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (apkDownloader != null) {
                                    val result = apkDownloader.installApk(release.htmlUrl)
                                    when (result) {
                                        is InstallResult.InstallStarted -> {}
                                        is InstallResult.PermissionRequired -> {
                                            apkDownloader.requestInstallPermission(result.intent)
                                        }
                                        is InstallResult.NeedBrowser -> {
                                            apkDownloader.openInBrowser(result.downloadUrl)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Установить")
                        }
                    }
                    is DownloadState.Error -> {
                        val errorMessage = (downloadState as DownloadState.Error).message
                        Text(
                            text = "Ошибка: $errorMessage",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (apkDownloader != null) {
                                    release.apkAsset?.let { asset ->
                                        scope.launch {
                                            apkDownloader.downloadApk(asset.downloadUrl, asset.fileName)
                                        }
                                    } ?: run {
                                        scope.launch {
                                            val asset = apkDownloader.findApkAsset("FlowHack", "flow-capital-android")
                                            if (asset != null) {
                                                apkDownloader.downloadApk(asset.downloadUrl, asset.fileName)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Повторить")
                        }
                    }
                    is DownloadState.Idle -> {
                        Button(
                            onClick = {
                                if (apkDownloader != null) {
                                    release.apkAsset?.let { asset ->
                                        scope.launch {
                                            apkDownloader.downloadApk(asset.downloadUrl, asset.fileName)
                                        }
                                    } ?: run {
                                        scope.launch {
                                            val asset = apkDownloader.findApkAsset("FlowHack", "flow-capital-android")
                                            if (asset != null) {
                                                apkDownloader.downloadApk(asset.downloadUrl, asset.fileName)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Установить")
                        }
                    }
                }

                TextButton(
                    onClick = {
                        onSkipVersion(release.versionName)
                        onDismiss()
                    }
                ) {
                    Text("Закрыть окно", color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Перейти на страницу релиза",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onOpenRelease() }
                )
            }
        }
    }
}

@Composable
fun UpdateChecker(
    owner: String = "FlowHack",
    repo: String = "flow-capital-android",
    settingsManager: SettingsManager? = null,
    onCheckComplete: ((UpdateCheckResult) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUpdateDialog by remember { mutableStateOf(false) }

    val release by GlobalUpdateManager.releaseState.collectAsState()

    LaunchedEffect(Unit) {
        if (settingsManager != null && GlobalUpdateManager.releaseState.value == null) {
            val checkOnStart = settingsManager.checkUpdateOnStartFlow.first()
            val result = checkForUpdate(owner, repo)
            onCheckComplete?.invoke(result)

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val skippedVersion = settingsManager.skippedVersionFlow.first()
                    if (skippedVersion != result.release.versionName) {
                        GlobalUpdateManager.releaseState.value = result.release
                        if (checkOnStart) {
                            showUpdateDialog = true
                            GlobalUpdateManager.markDialogShown()
                        }
                    }
                }
                is UpdateCheckResult.Error, is UpdateCheckResult.NoUpdate -> {}
            }
        }
    }

    if (showUpdateDialog && settingsManager != null) {
        val context = LocalContext.current
        val apkDownloader = remember { ApkDownloader(context) }
        val currentRelease = release
        if (currentRelease != null) {
            UpdateDialog(
                release = currentRelease,
                settingsManager = settingsManager,
                apkDownloader = apkDownloader,
                onDismiss = {
                    showUpdateDialog = false
                },
                onOpenRelease = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentRelease.htmlUrl))
                    context.startActivity(intent)
                    showUpdateDialog = false
                },
                onSkipVersion = { version ->
                    scope.launch {
                        settingsManager.setSkippedVersion(version)
                    }
                },
                onSkipAutoUpdate = {
                    scope.launch {
                        settingsManager.setSkipAutoUpdate(true)
                    }
                }
            )
        }
    }
}

@Composable
fun InstallUpdateDialog(
    release: GitHubRelease,
    downloadState: DownloadState,
    onInstallRequest: () -> Unit,
    onBrowserRequest: () -> Unit,
    onPermissionDenied: () -> Unit,
    onDismiss: () -> Unit,
    onSkipVersion: (String) -> Unit,
    onSkipAutoUpdate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Доступно обновление!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Новая версия: ${release.versionName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Текущая версия: ${BuildConfig.VERSION_NAME}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Что нового:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = parseMarkdown(release.body),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (downloadState) {
                    is DownloadState.Idle -> {
                        Button(
                            onClick = onInstallRequest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Установить")
                        }
                    }
                    is DownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Загрузка: ${downloadState.progress}%",
                                fontSize = 14.sp
                            )
                        }
                    }
                    is DownloadState.Success -> {
                        Text(
                            text = "Загрузка завершена!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onInstallRequest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Установить")
                        }
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = "Ошибка: ${downloadState.message}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onInstallRequest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Повторить")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onBrowserRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Скачать вручную (${release.htmlUrl})")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { onSkipVersion(release.versionName) }) {
                        Text("Пропустить")
                    }
                    TextButton(onClick = onSkipAutoUpdate) {
                        Text("Больше не спрашивать")
                    }
                }
            }
        }
    }
}
