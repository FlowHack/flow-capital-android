package com.flowhack.flowcapital.data.proxy

import android.util.Base64
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Локальный прокси-форвардер для авторизованных HTTP-прокси.
 *
 * WebView (ProxyController) НЕ поддерживает логин/пароль в правиле прокси
 * (формат scheme://host:port без credentials — иначе «Invalid Proxy URL»).
 * Решение: WebView подключается к локальному серверу на 127.0.0.1, а этот
 * сервер добавляет заголовок Proxy-Authorization (Basic) и пересылает трафик
 * на удалённый прокси.
 *
 * Поддерживает:
 * - HTTPS через CONNECT (туннель в обе стороны);
 * - HTTP запросы (проксирование с добавлением авторизации).
 *
 * ВАЖНО: заголовки читаются ПОБАЙТНО из InputStream (без BufferedReader),
 * чтобы не потерять начало TLS-данных (ClientHello), которые WebView
 * отправляет сразу после CONNECT.
 *
 * @property remoteHost Хост удалённого прокси
 * @property remotePort Порт удалённого прокси
 * @property username Логин (опционально)
 * @property password Пароль (опционально)
 */
class LocalProxyServer(
    private val remoteHost: String,
    private val remotePort: Int,
    private val username: String?,
    private val password: String?
) {
    private var serverSocket: ServerSocket? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)

    /**
     * Запустить локальный прокси-сервер на случайном порту 127.0.0.1.
     *
     * @return Локальный порт, на который должен указывать WebView
     */
    fun start(): Int {
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        running.set(true)
        Thread {
            while (running.get()) {
                try {
                    val client = ss.accept()
                    executor.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) {
                        Timber.tag("LocalProxy").w("Ошибка accept: ${e.message}")
                    }
                }
            }
        }.start()
        Timber.tag("LocalProxy").d(
            "Локальный прокси запущен на 127.0.0.1:${ss.localPort} -> ${remoteHost}:${remotePort}"
        )
        return ss.localPort
    }

    /**
     * Остановить сервер и закрыть все соединения.
     */
    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdownNow()
        Timber.tag("LocalProxy").d("Локальный прокси остановлен")
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 60000
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // Читаем строку запроса и заголовки ПОБАЙТНО — буферизация
            // потеряла бы начало TLS-данных (ClientHello) от WebView.
            val requestLine = readLine(clientIn)
            if (requestLine.isNullOrBlank()) {
                client.close()
                return
            }

            val headers = mutableListOf<String>()
            var line = readLine(clientIn)
            while (line != null && line.isNotBlank()) {
                headers.add(line)
                line = readLine(clientIn)
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                client.close()
                return
            }
            val method = parts[0]
            val target = parts[1]

            // Соединение с удалённым прокси.
            val remote = Socket(remoteHost, remotePort)
            remote.soTimeout = 60000
            val remoteOut = BufferedOutputStream(remote.getOutputStream())
            val remoteIn = BufferedInputStream(remote.getInputStream())

            val authHeader = if (!username.isNullOrBlank()) {
                val credentials = "${username}:${password ?: ""}"
                val encoded = Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                "Proxy-Authorization: Basic $encoded\r\n"
            } else {
                ""
            }

            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS: передаём CONNECT на удалённый прокси, отвечаем клиенту 200.
                val connectRequest = buildString {
                    append("CONNECT $target HTTP/1.1\r\n")
                    append("Host: $target\r\n")
                    append(authHeader)
                    append("\r\n")
                }
                remoteOut.write(connectRequest.toByteArray(Charsets.UTF_8))
                remoteOut.flush()

                val statusLine = readLine(remoteIn)
                if (statusLine == null || !statusLine.contains("200")) {
                    Timber.tag("LocalProxy").w("CONNECT отклонён: $statusLine")
                    client.close()
                    remote.close()
                    return
                }
                // Пропускаем остальные заголовки ответа удалённого прокси.
                readHeaders(remoteIn)

                // Клиенту (WebView) сообщаем, что туннель установлен.
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()

                tunnel(client, remote, clientIn, clientOut, remoteIn, remoteOut)
            } else {
                // HTTP: форвардим запрос с авторизацией и Connection: close.
                val request = buildString {
                    append(requestLine).append("\r\n")
                    append(authHeader)
                    headers
                        .filterNot {
                            it.startsWith("Proxy-Connection:", ignoreCase = true) ||
                                it.startsWith("Connection:", ignoreCase = true)
                        }
                        .forEach { append(it).append("\r\n") }
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                remoteOut.write(request.toByteArray(Charsets.UTF_8))
                remoteOut.flush()

                // Пробрасываем тело запроса (POST и т.п.), если оно есть.
                val contentLength = headers
                    .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.toIntOrNull()
                    ?: 0
                if (contentLength > 0) {
                    val body = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val n = clientIn.read(body, readTotal, contentLength - readTotal)
                        if (n < 0) break
                        readTotal += n
                    }
                    if (readTotal > 0) {
                        remoteOut.write(body, 0, readTotal)
                        remoteOut.flush()
                    }
                }

                copyStream(remoteIn, clientOut)
                remote.close()
            }
        } catch (e: Exception) {
            Timber.tag("LocalProxy").w("Ошибка обработки клиента: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Туннель в обе стороны (для CONNECT/HTTPS).
     * Соединение живёт, пока клиент не закроет его — таймауты не нужны,
     * иначе SSL-handshake/долгие запросы обрываются (ERR_CONNECTION_RESET).
     * Используются переданные потоки (не новые), чтобы не потерять
     * буферизованные данные.
     */
    private fun tunnel(
        client: Socket,
        remote: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        remoteIn: InputStream,
        remoteOut: OutputStream
    ) {
        client.soTimeout = 0
        remote.soTimeout = 0

        val toRemote = Thread {
            try {
                pipe(clientIn, remoteOut)
            } catch (_: Exception) {
            }
            try {
                remote.close()
            } catch (_: Exception) {
            }
        }
        val toClient = Thread {
            try {
                pipe(remoteIn, clientOut)
            } catch (_: Exception) {
            }
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
        toRemote.start()
        toClient.start()
        try {
            toRemote.join()
        } catch (_: InterruptedException) {
        }
        try {
            toClient.join()
        } catch (_: InterruptedException) {
        }
        try {
            remote.close()
        } catch (_: Exception) {
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            output.flush()
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            output.flush()
        }
    }

    /**
     * Читает строку до \n из потока ПОБАЙТНО (без буферизации),
     * чтобы не потерять данные, следующие за строкой.
     */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) {
                return sb.toString().trimEnd('\r')
            }
            sb.append(b.toChar())
        }
    }

    private fun readHeaders(input: InputStream) {
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) return
        }
    }
}