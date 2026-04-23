package com.example.flowcapital.data.proxy

import java.net.Proxy

/**
 * Тип прокси-сервера.
 */
enum class ProxyType {
    /** SOCKS5 прокси */
    SOCKS5,
    /** MTProto прокси */
    MTPROTO
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
 * @property type Тип прокси (SOCKS5 или MTPROTO)
 * @property server Адрес сервера
 * @property port Порт сервера
 * @property username Логин (для SOCKS5)
 * @property password Пароль (для SOCKS5)
 * @property secret Секретный ключ (для MTPROTO)
 * @property status Статус подключения
 * @property pingMs Время отклика в миллисекундах
 * @property enabledForSites Список сайтов для которых включён прокси
 */
data class ProxyConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ProxyType = ProxyType.SOCKS5,
    val server: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null,
    val status: ProxyStatus = ProxyStatus.DISCONNECTED,
    val pingMs: Int? = null,
    val enabledForSites: Set<String> = emptySet()
) {
    fun toProxy(): Proxy {
        return Proxy(Proxy.Type.SOCKS, java.net.InetSocketAddress(server, port))
    }
}

data class ProxyValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

object ProxyValidator {

    fun validateIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    fun validatePort(port: String): Boolean {
        if (port.isBlank()) return false
        val portNum = port.toIntOrNull() ?: return false
        return portNum in 1..65535
    }

    fun validateSocks5Proxy(
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

        if (username.isNullOrBlank()) {
            errors.add("Логин обязателен")
        }

        if (password.isNullOrBlank()) {
            errors.add("Пароль обязателен")
        }

        return ProxyValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validateMtProtoProxy(
        server: String,
        port: String,
        secret: String?
    ): ProxyValidationResult {
        val errors = mutableListOf<String>()

        if (!validateIpAddress(server)) {
            errors.add("Неверный формат IP адреса")
        }

        if (!validatePort(port)) {
            errors.add("Порт должен быть от 1 до 65535")
        }

        if (secret.isNullOrBlank()) {
            errors.add("Ключ обязателен")
        }

        return ProxyValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

object ProxyECurrencyBonus {
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

    fun getTotalWithBonus(amount: Double): Double {
        return amount + calculateBonus(amount)
    }
}
