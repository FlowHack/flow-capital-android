package com.flowhack.flowcapital.data.logging

import android.content.Context
import android.os.Build
import com.flowhack.flowcapital.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
 * - Постоянно пишет логи в файл flowcapital_log.txt во внутреннем хранилище
 * - При превышении максимального размера файл усекается (остаются последние записи)
 * - Хранит последние 1000 записей в памяти для быстрого доступа
 */
object AppLogger {

    private const val MAX_LOG_ENTRIES = 1000

    /** Максимальный размер файла логов (8 МБ). */
    private const val MAX_LOG_FILE_SIZE = 8L * 1024 * 1024

    /** Имя файла логов во внутреннем хранилище. */
    private const val LOG_FILE_NAME = "flowcapital_log.txt"

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

    /** Файл логов во внутреннем хранилище (инициализируется в init). */
    private var logFile: File? = null

    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        logFile = File(context.filesDir, LOG_FILE_NAME)
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

            // Постоянная запись в файл (синхронно, т.к. Timber вызывается из разных потоков).
            appendToFile(entry)
        }

        private fun getStackTraceString(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }

    /**
     * Дописать запись в файл логов. При превышении максимального размера файл усекается.
     * Выполняется синхронно, чтобы не терять записи при параллельных вызовах.
     */
    private fun appendToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                // Усечение: оставляем только последние записи, чтобы файл не разрастался.
                truncateFile(file)
            }
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val levelStr = when (entry.level) {
                LogLevel.VERBOSE -> "V"
                LogLevel.DEBUG -> "D"
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
            }
            val timeStr = dateFormat.format(Date(entry.timestamp))
            val line = "[$timeStr] $levelStr/${entry.tag}: ${entry.message}\n" +
                (entry.throwable?.let { "$it\n" } ?: "")
            file.appendText(line)
        } catch (e: Exception) {
            // Не логируем через Timber, чтобы избежать рекурсии.
            android.util.Log.e("AppLogger", "Ошибка записи лога в файл: ${e.message}")
        }
    }

    /**
     * Усечь файл логов: оставить только последние записи (примерно половину размера),
     * чтобы файл не превышал максимальный размер.
     */
    private fun truncateFile(file: File) {
        try {
            val lines = file.readLines()
            // Оставляем последние ~60% строк, чтобы после усечения файл был заметно меньше лимита.
            val keepCount = (lines.size * 0.6).toInt().coerceAtLeast(1)
            val kept = lines.takeLast(keepCount)
            file.writeText(kept.joinToString("\n") + "\n")
        } catch (e: Exception) {
            android.util.Log.e("AppLogger", "Ошибка усечения файла логов: ${e.message}")
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

    /**
     * Экспортировать лог в выбранный пользователем файл (uri).
     * Читает записи из файла логов (или из памяти, если файл недоступен)
     * и записывает их в указанный uri. Используется функцией «Сообщить об ошибке».
     */
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

                    // Читаем записи из файла логов (если доступен), иначе из памяти.
                    val fileEntries = readLogFileEntries()
                    val sortedEntries = if (fileEntries.isNotEmpty()) fileEntries.reversed() else logEntries.toList().reversed()
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
                Timber.tag("AppLogger").e(e, "Ошибка экспорта логов")
                Result.failure(e)
            }
        }
    }

    /**
     * Прочитать записи логов из файла (для экспорта). Парсит строки файла в [LogEntry].
     * Если файл недоступен или пуст — возвращает пустой список.
     */
    private fun readLogFileEntries(): List<LogEntry> {
        val file = logFile ?: return emptyList()
        return try {
            if (!file.exists()) return emptyList()
            file.readLines().mapNotNull { line ->
                parseLogLine(line)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Разобрать строку лога вида "[dd.MM.yyyy HH:mm:ss] L/TAG: message" в [LogEntry].
     * При неудачном разборе возвращает null.
     */
    private fun parseLogLine(line: String): LogEntry? {
        return try {
            if (!line.startsWith("[")) return null
            val closeBracket = line.indexOf(']')
            if (closeBracket <= 0) return null
            val timeStr = line.substring(1, closeBracket)
            val rest = line.substring(closeBracket + 1).trim()
            if (rest.length < 3) return null
            val levelChar = rest[0]
            val level = when (levelChar) {
                'V' -> LogLevel.VERBOSE
                'D' -> LogLevel.DEBUG
                'I' -> LogLevel.INFO
                'W' -> LogLevel.WARN
                'E' -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }
            val afterLevel = rest.substring(2).trim() // убираем "L/"
            val separator = afterLevel.indexOf(": ")
            val tag = if (separator > 0) afterLevel.substring(0, separator) else "Unknown"
            val message = if (separator > 0) afterLevel.substring(separator + 2) else afterLevel
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.parse(timeStr)?.time ?: System.currentTimeMillis()
            LogEntry(timestamp = timestamp, level = level, tag = tag, message = message)
        } catch (e: Exception) {
            null
        }
    }

    fun getLogCount(): Int = logEntries.size
}
