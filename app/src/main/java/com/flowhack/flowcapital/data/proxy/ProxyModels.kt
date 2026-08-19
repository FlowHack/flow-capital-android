package com.flowhack.flowcapital.data.proxy

import java.net.Proxy

/**
 * Тип прокси-сервера.
 */
enum class ProxyType {
    /** HTTP прокси. Поддерживает и HTTPS-сайты через CONNECT. */
    HTTP
}

/**
 * Статус подключения к прокси.
 */
enum class ProxyStatus {
    /** Не подключено */
    DISCONNECTED,
    /** Подключение */
    CONNECTING,
    /** Подключено */
    CONNECTED,
    /** Недоступно */
    UNAVAILABLE
}

/**
 * Конфигурация прокси-сервера.
 *
 * @property id Уникальный идентификатор
 * @property type Тип прокси (HTTP)
 * @property server Адрес сервера
 * @property port Порт сервера
 * @property username Логин (опционально)
 * @property password Пароль (опционально)
 * @property status Статус подключения
 * @property pingMs Время отклика в миллисекундах
 * @property enabledForSites Список сайтов для которых включён прокси
 */
data class ProxyConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ProxyType = ProxyType.HTTP,
    val server: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val status: ProxyStatus = ProxyStatus.DISCONNECTED,
    val pingMs: Int? = null,
    val enabledForSites: Set<String> = emptySet()
) {
    fun toProxy(): Proxy {
        return Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(server, port))
    }
}

/**
 * Результат валидации прокси-конфигурации.
 *
 * @property isValid true если ошибок нет
 * @property errors Список сообщений об ошибках
 */
data class ProxyValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Валидатор параметров прокси.
 * Проверяет IP-адреса, порты и обязательные поля для разных типов прокси.
 */
object ProxyValidator {

    /**
     * Проверить корректность IP-адреса (4 октета от 0 до 255).
     * @param ip Строка IP-адреса
     * @return true если адрес валидный
     */
    fun validateIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    /**
     * Проверить корректность порта (1-65535).
     * @param port Строка порта
     * @return true если порт валидный
     */
    fun validatePort(port: String): Boolean {
        if (port.isBlank()) return false
        val portNum = port.toIntOrNull() ?: return false
        return portNum in 1..65535
    }

    /**
     * Проверить конфигурацию HTTP прокси.
     * Логин и пароль опциональны (многие HTTP прокси работают без авторизации).
     * @param server IP-адрес сервера
     * @param port Порт
     * @param username Логин (опционально)
     * @param password Пароль (опционально)
     * @return Результат валидации со списком ошибок
     */
    fun validateHttpProxy(
        server: String,
        port: String,
        username: String?,
        password: String?
    ): ProxyValidationResult {
        val errors = mutableListOf<String>()

        if (!validateIpAddress(server)) {
            errors.add("Неверный формат IP адреса")
        }

        if (!validatePort(port)) {
            errors.add("Порт должен быть от 1 до 65535")
        }

        return ProxyValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    }

/**
 * Калькулятор бонусов eRub.
 * Использует прогрессивную шкалу: чем больше сумма, тем выше бонус.
 */
object ProxyERubBonus {
    /**
     * Рассчитать бонус для указанной суммы.
     * @param amount Сумма взноса
     * @return Сумма бонуса (не включая исходную сумму)
     */
    fun calculateBonus(amount: Double): Double {
        return when {
            amount >= 1_000_000 -> amount * 2.00
            amount >= 500_000 -> amount * 1.75
            amount >= 100_000 -> amount * 1.50
            amount >= 50_000 -> amount * 1.25
            amount >= 10_000 -> amount * 1.00
            amount >= 5_000 -> amount * 0.75
            amount >= 1_000 -> amount * 0.50
            else -> 0.0
        }
    }

    /**
     * Получить итоговую сумму с учётом бонуса.
     * @param amount Сумма взноса
     * @return Исходная сумма + бонус
     */
    fun getTotalWithBonus(amount: Double): Double {
        return amount + calculateBonus(amount)
    }
}
