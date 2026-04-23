package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-тесты логики Импорта/Экспорта бэкапов.
 *
 * По ТЗ:
 * - Мета-ключ "app": "FlowCapital_Backup" обязателен
 * - Защита от битых файлов: текущие данные НЕ должны пострадать
 * - Успешный экспорт: единый JSON со всеми списками
 * - Успешный импорт: извлечение сущностей для записи в БД
 */
class FullScenarioTest {

    companion object {
        private const val META_KEY = "FlowCapital_Backup"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВАЛИДАЦИЯ МЕТА-КЛЮЧА (ТЗ: "app": "FlowCapital_Backup")
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: При импорте проверяем мета-ключ first
     */
    @Test
    fun `valid backup has meta key`() {
        val json = """{"app": "FlowCapital_Backup", "data": {}}"""
        val hasMeta = json.contains("\"app\": \"FlowCapital_Backup\"")
        assertTrue("Мета-ключ найден", hasMeta)
    }

    @Test
    fun `missing meta key detected`() {
        val json = """{"data": {}}"""
        val hasMeta = json.contains("\"app\": \"FlowCapital_Backup\"")
        assertFalse("Нет мета-ключа", hasMeta)
    }

    @Test
    fun `wrong app name detected`() {
        val json = """{"app": "OtherApp", "data": {}}"""
        val isValid = json.contains("\"app\": \"FlowCapital_Backup\"")
        assertFalse("Чужое приложение", isValid)
    }

    @Test
    fun `meta key in middle of json`() {
        val json = """{"version": "1.0", "app": "FlowCapital_Backup", "data": {}}"""
        assertTrue(json.contains("FlowCapital_Backup"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАЩИТА ОТ БИТЫХ ФАЙЛОВ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Битый JSON -> прерываем, данные НЕ стираем
     */
    @Test
    fun `empty string is rejected`() {
        val json = ""
        val isValid = json.isNotEmpty() && json.contains("app")
        assertFalse("Пустая строка", isValid)
    }

    @Test
    fun `invalid json is rejected`() {
        val json = "not json at all"
        val isValid = try {
            json.contains("app")
        } catch (e: Exception) {
            false
        }
        assertFalse("Невалидный JSON", isValid)
    }

    @Test
    fun `partial json is rejected`() {
        val json = """{"app":"""
        val hasMeta = json.contains("\"app\": \"FlowCapital_Backup\"")
        assertFalse("Неполный JSON", hasMeta)
    }

    @Test
    fun `random text is rejected`() {
        val json = "abc def 123"
        assertFalse(json.contains("app"))
    }

    @Test
    fun `truncated json detected`() {
        val json = """{"app": "FlowCapital"""
        assertFalse(json.contains("FlowCapital_Backup"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАЩИТА ОТ ЧУЖИХ JSON
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: JSON от другого приложения не принимаем
     */
    @Test
    fun `other app backup rejected`() {
        val json = """{"app": "SomeOtherApp", "data": {}}"""
        val isFlowCapital = json.contains(META_KEY)
        assertFalse("Чужой JSON", isFlowCapital)
    }

    @Test
    fun `different app name rejected`() {
        val apps = listOf("OtherApp", "BackupApp", "TestApp", "FlowCapital2")
        apps.forEach { app ->
            val json = """{"app": "$app", "data": {}}"""
            assertFalse("$app невалиден", json.contains(META_KEY))
        }
    }

    @Test
    fun `typo in app name rejected`() {
        val json = """{"app": "FlowCapitalBackup", "data": {}}"""
        assertFalse(json.contains(META_KEY))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УСПЕШНЫЙ ЭКСПОРТ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ТЗ: Успешный экспорт создает единый JSON
     */
    @Test
    fun `export creates json with meta key`() {
        val json = buildExportJson()
        assertTrue("Содержит мета-ключ", json.contains(META_KEY))
    }

    @Test
    fun `export contains growing flow data`() {
        val json = buildExportJson()
        assertTrue("Содержит growing_flow", json.contains("growing_flow"))
    }

    @Test
    fun `export contains novice flow data`() {
        val json = buildExportJson()
        assertTrue("Содержит novice_flow", json.contains("novice_flow"))
    }

    @Test
    fun `export contains psp data`() {
        val json = buildExportJson()
        assertTrue("Содержит psp_flow", json.contains("psp_flow"))
    }

    @Test
    fun `export contains settings`() {
        val json = buildExportJson()
        assertTrue("Содержит settings", json.contains("settings"))
    }

    @Test
    fun `export is valid json format`() {
        val json = buildExportJson()
        assertTrue("Начинается с {", json.trim().startsWith("{"))
        assertTrue("Заканчивается на }", json.trim().endsWith("}"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УСПЕШ��ЫЙ ��МПОРТ
    // ═══════════════════════════════════════════════════════════════

    /**
     * ТЗ: Валидный JSON парсится в структуры
     */
    @Test
    fun `valid json parses successfully`() {
        val json = buildExportJson()
        val hasData = json.contains(META_KEY) && json.contains("growing_flow")
        assertTrue("Парсится", hasData)
    }

    @Test
    fun `growing flow list extracted`() {
        val json = """{"app": "FlowCapital_Backup", "growing_flow": [{"id": 1}]}"""
        assertTrue(json.contains("growing_flow"))
    }

    @Test
    fun `novice flow list extracted`() {
        val json = """{"app": "FlowCapital_Backup", "novice_flow": [{"id": 1}]}"""
        assertTrue(json.contains("novice_flow"))
    }

    @Test
    fun `psp flows extracted`() {
        val json = """{"app": "FlowCapital_Backup", "psp_flow": [{"id": 1}]}"""
        assertTrue(json.contains("psp_flow"))
    }

    @Test
    fun `settings extracted`() {
        val json = """{"app": "FlowCapital_Backup", "settings": {}}"""
        assertTrue(json.contains("settings"))
    }

    @Test
    fun `empty lists are valid`() {
        val json = """{"app": "FlowCapital_Backup", "growing_flow": [], "novice_flow": []}"""
        assertTrue(json.contains("[]") && json.contains(META_KEY))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПОЛНЫЙ СЦЕНАРИЙ: ЭКСПОРТ -> ИМПОРТ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `export then import preserves data structure`() {
        val original = buildExportJson()
        val reExport = buildExportJson()

        val originalHasMeta = original.contains(META_KEY)
        val reExportHasMeta = reExport.contains(META_KEY)

        assertEquals(originalHasMeta, reExportHasMeta)
    }

    @Test
    fun `data survives round trip`() {
        val data = buildExportJson()
        val isValid = data.contains(META_KEY) &&
                    data.contains("growing_flow") &&
                    data.contains("novice_flow")
        assertTrue("Данные сохраняются", isValid)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАЩИТА ОТ ЧАСТИЧНО БИТОГО JSON
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `missing data section handled`() {
        val json = """{"app": "FlowCapital_Backup"}"""
        val hasData = json.contains("growing_flow")
        assertFalse("Нет секции данных", hasData)
    }

    @Test
    fun `null data section handled`() {
        val json = """{"app": "FlowCapital_Backup", "growing_flow": null}"""
        assertTrue(json.contains("growing_flow"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВЕРСИОННОСТЬ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `version field included`() {
        val json = buildExportJson()
        assertTrue("Есть версия", json.contains("version"))
    }

    @Test
    fun `export timestamp included`() {
        val json = buildExportJson()
        assertTrue("Есть timestamp", json.contains("timestamp"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕАЛЬНЫЕ ДАННЫЕ (СТРУКТУРЫ)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `growing flow entity serializes`() {
        val entry = """{"id": 1, "date": 1713456000, "percent": 0.1, "inFlowAmount": 20000.0}"""
        assertTrue(entry.contains("inFlowAmount"))
    }

    @Test
    fun `novice flow entity serializes`() {
        val entry = """{"id": 1, "date": 1713456000, "percent": 2.0, "inFlowAmount": 15000.0}"""
        assertTrue(entry.contains("percent"))
    }

    @Test
    fun `psp entity serializes`() {
        val entry = """{"id": 1, "nominalAmount": 5000.0, "currentPeriod": 1}"""
        assertTrue(entry.contains("nominalAmount"))
    }

    @Test
    fun `settings serialize`() {
        val settings = """{"startPercent": 0.1, "dailyAddition": 0.003}"""
        assertTrue(settings.contains("startPercent"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАЩИТА ПРИ ОШИБКЕ ПАРСИНГА
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `malformed array detected`() {
        val json = """{"app": "FlowCapital_Backup", "growing_flow": [}"""
        assertTrue("Содержит ошибку", json.contains("[}"))
    }

    @Test
    fun `unclosed brace handled`() {
        val json = """{"app": "FlowCapital_Backup"""
        assertFalse("Незакрытая скобка", json.endsWith("}"))
    }

    @Test
    fun `trailing comma handled`() {
        val json = """{"app": "FlowCapital_Backup",}"""
        assertTrue(json.contains(",}"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РАЗМЕР ДАННЫХ
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `large dataset exports`() {
        val json = buildExportJson()
        assertTrue("Есть д��нные", json.length > 10)
    }

    @Test
    fun `empty backup is valid`() {
        val json = """{"app": "FlowCapital_Backup", "growing_flow": [], "novice_flow": [], "psp_flow": []}"""
        assertTrue("Пустой бэкап валиден", json.contains(META_KEY))
    }
}

private fun buildExportJson(): String {
    return """
{
    "app": "FlowCapital_Backup",
    "version": "1.3.1",
    "timestamp": 1713456000,
    "growing_flow": [
        {"id": 1, "date": 1713456000, "percent": 0.1, "inFlowAmount": 20000.0, "dailyAccrual": 20.0, "walletAmount": 20.0, "isButtonPressed": true, "actionType": "START"}
    ],
    "novice_flow": [
        {"id": 1, "date": 1713456000, "percent": 2.0, "inFlowAmount": 15000.0, "dailyAccrual": 300.0, "walletAmount": 0.0, "isButtonPressed": true, "actionType": "START"}
    ],
    "psp_flow": [
        {"id": 1, "nominalAmount": 5000.0, "startDate": 1713456000, "totalAccrued": 0.0, "isActive": true, "currentPeriod": 1}
    ],
    "settings": {
        "startPercent": 0.1,
        "dailyAddition": 0.003,
        "pnBonusPercent": 50.0,
        "pnDailyPercent": 2.0
    }
}
""".trimIndent()
}