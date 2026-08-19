package com.flowhack.flowcapital.data.proxy

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowhack.flowcapital.data.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.proxyDataStore by preferencesDataStore(name = "proxy_settings")

/**
 * Хранилище прокси-конфигураций.
 * Управляет сохранением и загрузкой списка прокси из DataStore.
 *
 * @property context Контекст приложения
 */
class ProxyStorage(private val context: Context) {

    companion object {
        private val PROXIES_KEY = stringPreferencesKey("saved_proxies")

        private const val MAX_PROXIES = 3
    }

    private val dataStore = context.proxyDataStore

    val proxiesFlow: Flow<List<ProxyConfig>> = dataStore.data.map { preferences ->
        val jsonString = preferences[PROXIES_KEY] ?: "[]"
        parseProxiesFromJson(jsonString)
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private fun parseProxiesFromJson(jsonString: String): List<ProxyConfig> {
        return try {
            val jsonArray = JSONArray(jsonString)
            (0 until jsonArray.length()).mapNotNull { index ->
                try {
                    val obj = jsonArray.getJSONObject(index)
                    ProxyConfig(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = runCatching {
                            ProxyType.valueOf(obj.optString("type", "HTTP"))
                        }.getOrDefault(ProxyType.HTTP),
                        server = obj.optString("server", ""),
                        port = obj.optInt("port", 0),
                        username = obj.optString("username", "").takeIf { it.isNotBlank() && it != "null" },
                        password = obj.optString("password", "").takeIf { it.isNotBlank() && it != "null" },
                        status = ProxyStatus.valueOf(obj.optString("status", "DISCONNECTED")),
                        pingMs = if (obj.has("pingMs") && !obj.isNull("pingMs")) obj.getInt("pingMs") else null,
                        enabledForSites = obj.optJSONArray("enabledForSites")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }.toSet()
                        } ?: emptySet()
                    )
                } catch (e: Exception) {
                    AppLogger.e("ProxyStorage", "Ошибка парсинга прокси", e)
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ProxyStorage", "Ошибка парсинга JSON массива прокси", e)
            emptyList()
        }
    }

    private fun proxiesToJson(proxies: List<ProxyConfig>): String {
        val jsonArray = JSONArray()
        proxies.forEach { proxy ->
            val obj = JSONObject().apply {
                put("id", proxy.id)
                put("type", proxy.type.name)
                put("server", proxy.server)
                put("port", proxy.port)
                put("username", proxy.username ?: JSONObject.NULL)
                put("password", proxy.password ?: JSONObject.NULL)
                put("status", proxy.status.name)
                put("pingMs", if (proxy.pingMs != null) proxy.pingMs else JSONObject.NULL)
                put("enabledForSites", JSONArray(proxy.enabledForSites.toList()))
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    /**
     * Сохранить список прокси (максимум 3).
     * @param proxies Список прокси-конфигураций
     */
    suspend fun saveProxies(proxies: List<ProxyConfig>) {
        if (proxies.size > MAX_PROXIES) return
        dataStore.edit { preferences ->
            preferences[PROXIES_KEY] = proxiesToJson(proxies)
        }
    }

    /**
     * Добавить новый прокси (если не превышен лимит).
     * @param proxy Прокси-конфигурация
     */
    suspend fun addProxy(proxy: ProxyConfig) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PROXIES_KEY] ?: "[]"
            val currentList = parseProxiesFromJson(currentJson).toMutableList()
            if (currentList.size < MAX_PROXIES) {
                currentList.add(proxy)
                preferences[PROXIES_KEY] = proxiesToJson(currentList)
            }
        }
    }

    /**
     * Обновить существующий прокси (по id).
     * @param proxy Прокси-конфигурация с обновлёнными полями
     */
    suspend fun updateProxy(proxy: ProxyConfig) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PROXIES_KEY] ?: "[]"
            val currentList = parseProxiesFromJson(currentJson).toMutableList()
            val index = currentList.indexOfFirst { it.id == proxy.id }
            if (index != -1) {
                currentList[index] = proxy
                preferences[PROXIES_KEY] = proxiesToJson(currentList)
            }
        }
    }

    /**
     * Удалить прокси по идентификатору.
     * @param proxyId Идентификатор прокси
     */
    suspend fun removeProxy(proxyId: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PROXIES_KEY] ?: "[]"
            val currentList = parseProxiesFromJson(currentJson).toMutableList()
            currentList.removeAll { it.id == proxyId }
            preferences[PROXIES_KEY] = proxiesToJson(currentList)
        }
    }

    /**
     * Получить сырой JSON всех прокси (для экспорта).
     */
    suspend fun getProxiesJson(): String {
        return dataStore.data.first()[PROXIES_KEY] ?: "[]"
    }

    /**
     * Сохранить сырой JSON всех прокси (для импорта).
     */
    suspend fun saveProxiesJson(json: String) {
        dataStore.edit { prefs ->
            prefs[PROXIES_KEY] = json
        }
    }

    /**
     * Обновить статус и пинг прокси.
     * @param proxyId Идентификатор прокси
     * @param status Новый статус
     * @param pingMs Время пинга в мс (опционально)
     */
    suspend fun updateProxyStatus(proxyId: String, status: ProxyStatus, pingMs: Int? = null) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PROXIES_KEY] ?: "[]"
            val currentList = parseProxiesFromJson(currentJson).toMutableList()
            val index = currentList.indexOfFirst { it.id == proxyId }
            if (index != -1) {
                currentList[index] = currentList[index].copy(status = status, pingMs = pingMs)
                preferences[PROXIES_KEY] = proxiesToJson(currentList)
            }
        }
    }

    /**
     * Обновить список сайтов для прокси.
     * @param proxyId Идентификатор прокси
     * @param enabledForSites Набор URL сайтов
     */
    suspend fun updateProxySites(proxyId: String, enabledForSites: Set<String>) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PROXIES_KEY] ?: "[]"
            val currentList = parseProxiesFromJson(currentJson).toMutableList()
            val index = currentList.indexOfFirst { it.id == proxyId }
            if (index != -1) {
                currentList[index] = currentList[index].copy(enabledForSites = enabledForSites)
                preferences[PROXIES_KEY] = proxiesToJson(currentList)
            }
        }
    }
}
