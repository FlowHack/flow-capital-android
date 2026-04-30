package com.flowhack.flowcapital.data.logging

import android.content.Context
import android.os.Build
import com.flowhack.flowcapital.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Логгер приложения для записи логов в файл и вывода в консоль.
 * Использует библиотеку Timber для логирования.
 *
 * Особенности:
 * - В режиме DEBUG добавляет DebugTree для вывода в logcat
 * - Записывает логи в файл flowcapital_log.txt во внешнем хранилище
 * - Хранит последние 1000 записей в памяти
 */
object AppLogger {

    private const val MAX_LOG_ENTRIES = 1000

    private val logEntries = ConcurrentLinkedDeque<LogEntry>()

    private data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: String? = null
    )

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree())
        log("AppLogger", "Приложение запущено. Версия: ${BuildConfig.VERSION_NAME}, Build: ${BuildConfig.VERSION_CODE}")
    }

    private class FileLoggingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                android.util.Log.VERBOSE -> LogLevel.VERBOSE
                android.util.Log.DEBUG -> LogLevel.DEBUG
                android.util.Log.INFO -> LogLevel.INFO
                android.util.Log.WARN -> LogLevel.WARN
                android.util.Log.ERROR -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }

            val throwableStr = t?.let { getStackTraceString(it) }

            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag ?: "Unknown",
                message = message,
                throwable = throwableStr
            )

            logEntries.addLast(entry)

            while (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeFirst()
            }
        }

        private fun getStackTraceString(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.tag(tag).e(message)
        }
    }

    fun log(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    suspend fun exportLogToFile(context: Context, uri: android.net.Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext Result.failure(Exception("Не удалось создать файл"))

                outputStream.bufferedWriter().use { writer ->
                    writer.write("\uFEFF")

                    writer.write("═══════════════════════════════════════════════════════════════\n")
                    writer.write("                    LOG ФАЙЛ FLOWCAPITAL\n")
                    writer.write("═══════════════════════════════════════════════════════════════\n\n")

                    writer.write("═══ ИНФОРМАЦИЯ О УСТРОЙСТВЕ ═══\n")
                    writer.write("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    writer.write("Модель: ${Build.MODEL}\n")
                    writer.write("Устройство: ${Build.DEVICE}\n")
                    writer.write("Android версия: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    writer.write("Производитель: ${Build.MANUFACTURER}\n")
                    writer.write("Дата создания лога: ${dateFormat.format(Date())}\n")
                    writer.write("Всего записей: ${logEntries.size}\n")
                    writer.write("\n")

                    writer.write("═══ LOG ЗАПИСИ (от новых к старым) ═══\n")
                    writer.write("───────────────────────────────────────────────────────────────\n")

                    val sortedEntries = logEntries.toList().reversed()
                    for (entry in sortedEntries) {
                        val levelStr = when (entry.level) {
                            LogLevel.VERBOSE -> "V"
                            LogLevel.DEBUG -> "D"
                            LogLevel.INFO -> "I"
                            LogLevel.WARN -> "W"
                            LogLevel.ERROR -> "E"
                        }
                        val timeStr = dateFormat.format(Date(entry.timestamp))
                        writer.write("[$timeStr] $levelStr/${entry.tag}: ${entry.message}\n")
                        if (entry.throwable != null) {
                            writer.write(entry.throwable)
                            writer.write("\n")
                        }
                    }

                    writer.write("\n")
                    writer.write("═══════════════════════════════════════════════════════════════\n")
                    writer.write("                      КОНЕЦ LOG ФАЙЛА\n")
                    writer.write("═══════════════════════════════════════════════════════════════\n")
                }

                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    fun getLogCount(): Int = logEntries.size
}
