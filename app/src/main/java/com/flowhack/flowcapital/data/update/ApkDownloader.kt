package com.flowhack.flowcapital.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.flowhack.flowcapital.data.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Состояние скачивания APK файла.
 */
sealed class DownloadState {
    /** Ожидание начала скачивания */
    data object Idle : DownloadState()

    /** Скачивание в процессе
     * @param progress Процент выполнения (0-100)
     * @param downloadedBytes Скачанных байт
     * @param totalBytes Всего байт для скачивания (-1 если неизвестно)
     */
    data class Downloading(
        val progress: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1
    ) : DownloadState()

    /** Скачивание завершено успешно
     * @param file Скачанный файл
     */
    data class Success(val file: File) : DownloadState()

    /** Ошибка при скачивании или установке
     * @param message Сообщение об ошибке
     * @param canOpenBrowser Можно ли открыть браузер как запасной вариант
     */
    data class Error(
        val message: String,
        val canOpenBrowser: Boolean = true
    ) : DownloadState()
}

/**
 * Результат установки APK.
 */
sealed class InstallResult {
    /** Установка запущена успешно */
    data object InstallStarted : InstallResult()

    /** Требуется разрешение на установку из неизвестных источников
     * @param intent Intent для запроса разрешения
     */
    data class PermissionRequired(val intent: Intent) : InstallResult()

    /** Установка невозможна, нужен браузер
     * @param downloadUrl Ссылка для скачивания в браузере
     */
    data class NeedBrowser(val downloadUrl: String) : InstallResult()
}

/**
 * Результат поиска APK файла релиза на GitHub.
 *
 * @property downloadUrl Прямая ссылка на APK файл
 * @property fileName Имя файла для сохранения
 * @property fileSize Размер файла в байтах (-1 если неизвестно)
 */
data class ApkAsset(
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long = -1
)

/**
 * Сервис для скачивания и установки APK обновлений.
 *
 * Возможности:
 * - Поиск APK файла в релизе GitHub
 * - Скачивание с отображением прогресса
 * - Установка APK с запросом разрешений
 * - Автоматический переход в браузер при невозможности установки
 *
 * @param context Контекст приложения
 */
class ApkDownloader(private val context: Context) {

    companion object {
        /** Тег для логирования */
        private const val TAG = "ApkDownloader"

        /** Название папки для хранения APK */
        private const val APK_FOLDER = "apk_updates"

        /** Таймаут подключения в миллисекундах */
        private const val TIMEOUT_MS = 30000

        /** Размер буфера для чтения */
        private const val BUFFER_SIZE = 8192

        /** Заголовки для API GitHub */
        private val GITHUB_HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "FlowCapital-Android",
            "X-GitHub-Api-Version" to "2022-11-28"
        )
    }

    /** Текущее состояние скачивания */
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /** Скачанный файл */
    private var downloadedFile: File? = null

    init {
        Timber.tag(TAG).d("ApkDownloader инициализирован")
    }

    /**
     * Поиск APK файла в последнем релизе GitHub.
     *
     * @param owner Владелец репозитория
     * @param repo Название репозитория
     * @return ApkAsset с информацией о файле, или null если не найден
     */
    suspend fun findApkAsset(owner: String, repo: String): ApkAsset? = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("Ищу APK в релизе: %s/%s", owner, repo)

        try {
            val apiUrl = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val connection = apiUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            GITHUB_HEADERS.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val responseCode = connection.responseCode
            Timber.tag(TAG).d("Ответ API GitHub: %d", responseCode)

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Timber.tag(TAG).e("Ошибка API GitHub: %d", responseCode)
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            // Извлечение assets из JSON ответа
            val assets = extractAssetsFromJson(response)

            // Ищем APK файл (ищем asset с расширением .apk)
            val apkAsset = assets.find { it.name.endsWith(".apk", ignoreCase = true) }

            if (apkAsset != null) {
                Timber.tag(TAG).d("Найден APK: %s (%d bytes)", apkAsset.name, apkAsset.size)
            } else {
                Timber.tag(TAG).w("APK файл не найден в релизе. Assets: %s", assets.map { it.name })
            }

            apkAsset?.let {
                ApkAsset(
                    downloadUrl = it.url,
                    fileName = it.name,
                    fileSize = it.size
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка поиска APK")
            null
        }
    }

    /**
     * Структура для разбора asset из JSON.
     */
    private data class AssetInfo(val name: String, val url: String, val size: Long)

    /**
     * Извлечение списка assets из JSON ответа GitHub API.
     * Используем простой парсинг без внешних библиотек.
     */
    private fun extractAssetsFromJson(json: String): List<AssetInfo> {
        val assets = mutableListOf<AssetInfo>()

        // Ищем секцию "assets": [...]
        val assetsStart = json.indexOf("\"assets\"")
        if (assetsStart == -1) return assets

        val arrayStart = json.indexOf("[", assetsStart)
        val arrayEnd = findMatchingBracket(json, arrayStart)
        if (arrayStart == -1 || arrayEnd == -1) return assets

        val assetsJson = json.substring(arrayStart + 1, arrayEnd)

        // Разделение на отдельные asset'ы
        var pos = 0
        while (pos < assetsJson.length) {
            val objStart = assetsJson.indexOf("{", pos)
            if (objStart == -1) break

            val objEnd = findMatchingBracket(assetsJson, objStart)
            if (objEnd == -1) break

            val assetJson = assetsJson.substring(objStart, objEnd + 1)

            // Извлекаем name, browser_download_url и size
            val name = extractJsonString(assetJson, "name")
            val url = extractJsonString(assetJson, "browser_download_url")
            val sizeStr = extractJsonString(assetJson, "size")
            val size = sizeStr.toLongOrNull() ?: -1L

            if (name.isNotBlank() && url.isNotBlank()) {
                assets.add(AssetInfo(name, url, size))
            }

            pos = objEnd + 1
        }

        return assets
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

    /**
     * Извлечение строки из JSON.
     */
    private fun extractJsonString(json: String, field: String): String {
        val searchFor = "\"$field\""
        val fieldIdx = json.indexOf(searchFor)
        if (fieldIdx == -1) return ""
        val colonIdx = json.indexOf(":", fieldIdx)
        if (colonIdx == -1) return ""

        var i = colonIdx + 1
        while (i < json.length && json[i] == ' ') i++
        if (i >= json.length || json[i] != '"') return ""
        i++

        val result = StringBuilder()
        var escape = false
        while (i < json.length) {
            val c = json[i]
            when {
                escape -> {
                    when (c) {
                        'n' -> result.append('\n')
                        'r' -> {}
                        't' -> result.append('\t')
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        'u' -> {
                            if (i + 4 < json.length) {
                                val hex = json.substring(i + 1, i + 5)
                                try {
                                    result.append(hex.toInt(16).toChar())
                                    i += 4
                                } catch (_: Exception) {
                                    result.append(c)
                                }
                            }
                        }
                        else -> result.append(c)
                    }
                    escape = false
                }
                c == '\\' -> {
                    escape = true
                }
                c == '"' -> {
                    return result.toString()
                }
                else -> {
                    result.append(c)
                }
            }
            i++
        }
        return result.toString()
    }

    /**
     * Скачивание APK файла.
     *
     * @param downloadUrl Ссылка на файл
     * @param fileName Имя файла для сохранения
     */
    suspend fun downloadApk(downloadUrl: String, fileName: String) = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("Начинаю скачивание: %s", downloadUrl)

        // Сбрасываем состояние
        _downloadState.value = DownloadState.Downloading()
        downloadedFile = null

        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Timber.tag(TAG).e("Ошибка скачивания: %d", responseCode)
                _downloadState.value = DownloadState.Error("Ошибка скачивания: $responseCode")
                return@withContext
            }

            val totalBytes = connection.contentLengthLong
            val apkDir = File(context.cacheDir, APK_FOLDER)
            if (!apkDir.exists()) {
                apkDir.mkdirs()
            }

            // Удаляем старые APK файлы
            apkDir.listFiles()?.forEach { it.delete() }

            val outputFile = File(apkDir, fileName)
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        _downloadState.value = DownloadState.Downloading(
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                }
            }

            connection.disconnect()

            downloadedFile = outputFile
            _downloadState.value = DownloadState.Success(outputFile)
            Timber.tag(TAG).d("Скачивание завершено: %s (%d bytes)", outputFile.absolutePath, downloadedBytes)

            // Логируем в файл
            AppLogger.log(TAG, "APK скачан успешно: $fileName (${downloadedBytes} bytes)")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка скачивания APK")
            _downloadState.value = DownloadState.Error("Ошибка: ${e.localizedMessage ?: "Неизвестная ошибка"}")
            AppLogger.log(TAG, "Ошибка скачивания APK: ${e.localizedMessage}")
        }
    }

    /**
     * Проверка возможности установки из неизвестных источников.
     */
    fun canInstallUnknownApps(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Получение Intent для запроса разрешения на установку.
     */
    fun getInstallPermissionIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Запрос разрешения на установку из неизвестных источников.
     * Использует Activity Result API для корректной обработки результата.
     *
     * @param intent Intent для запроса разрешения
     */
    fun requestInstallPermission(intent: Intent) {
        try {
            context.startActivity(intent)
            AppLogger.log(TAG, "Запрошено разрешение на установку из неизвестных источников")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось открыть настройки для разрешения")
            AppLogger.e(TAG, "Не удалось открыть настройки для разрешения", e)
        }
    }

    /**
     * Попытка установки скачанного APK.
     *
     * @param releaseUrl Ссылка на релиз для fallback
     * @return Результат установки
     */
    fun installApk(releaseUrl: String): InstallResult {
        val file = downloadedFile

        if (file == null || !file.exists()) {
            Timber.tag(TAG).e("Файл APK не найден для установки")
            return InstallResult.NeedBrowser(releaseUrl)
        }

        if (!canInstallUnknownApps()) {
            Timber.tag(TAG).w("Нет разрешения на установку из неизвестных источников")
            return InstallResult.PermissionRequired(
                getInstallPermissionIntent()
            )
        }

        try {
            val uri = getApkUri(file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
                Timber.tag(TAG).d("Установка APK запущена")
                AppLogger.log(TAG, "Установка APK запущена пользователем")
                return InstallResult.InstallStarted
            } else {
                Timber.tag(TAG).e("Не найден activity для установки APK")
                AppLogger.log(TAG, "Не найден activity для установки APK")
                return InstallResult.NeedBrowser(releaseUrl)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка установки APK")
            AppLogger.log(TAG, "Ошибка установки APK: ${e.localizedMessage}")
            return InstallResult.NeedBrowser(releaseUrl)
        }
    }

    /**
     * Получение URI для APK через FileProvider.
     */
    private fun getApkUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Открытие ссылки на релиз в браузере (fallback).
     *
     * @param releaseUrl Ссылка на релиз
     * @param activity Activity для запуска Intent (nullable для non-Compose контекста)
     */
    fun openInBrowser(releaseUrl: String, activity: Activity? = null) {
        Timber.tag(TAG).d("Открываю браузер: %s", releaseUrl)
        AppLogger.log(TAG, "Открытие ссылки на релиз в браузере")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось открыть браузер")
        }
    }

    /**
     * Сброс состояния скачивания.
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
        downloadedFile = null
        Timber.tag(TAG).d("Состояние сброшено")
    }

    /**
     * Проверка, можно ли установить APK без браузера.
     */
    fun canInstallWithoutBrowser(): Boolean {
        return canInstallUnknownApps()
    }
}
