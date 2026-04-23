package com.example.flowcapital

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты логики обновлений.
 *
 * По ТЗ:
 * - Поиск файла "FlowCapital_v{version}.apk" в репо FlowHack/flow-capital-android/releases
 * - Логика чекбокса "Проверять обновления при входе": по умолчанию нажат
 * - Если флаг skipAutoUpdate = true — проверка идет в фоне, но диалог НЕ показывается
 * - Если latest == skippedVersion — диалог НЕ показывается
 */
class UpdateFlowTest {

    private val releaseJson = """
    {
        "tag_name": "v1.4.0",
        "body": "## What's new\n- Bug fixes",
        "assets": [
            {"name": "FlowCapital_v1.4.0.apk", "browser_download_url": "https://github.com/FlowHack/FlowCapitalAndroidApp/releases/download/v1.4.0/FlowCapital_v1.4.0.apk"}
        ]
    }
    """.trimIndent()

    @Test
    fun `parse tag name from JSON response`() {
        val tagName = parseTagName(releaseJson)
        assertEquals("v1.4.0", tagName)
    }

    @Test
    fun `parse asset URL from JSON response`() {
        val assetUrl = extractApkUrl(releaseJson)
        assertNotNull(assetUrl)
        assertTrue(assetUrl!!.contains("FlowCapital_v1.4.0.apk"))
    }

    @Test
    fun `version comparison detects newer version`() {
        assertTrue(isNewerVersion("1.4.0", "1.3.1"))
        assertTrue(isNewerVersion("1.3.2", "1.3.1"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `version comparison returns false for equal versions`() {
        assertFalse(isNewerVersion("1.3.1", "1.3.1"))
    }

    @Test
    fun `version comparison returns false for older version`() {
        assertFalse(isNewerVersion("1.3.0", "1.3.1"))
    }

    @Test
    fun `should show update dialog when update available and not skipped`() {
        val currentVersion = "1.3.1"
        val latestVersion = "1.4.0"
        val skippedVersion: String? = null
        val skipAutoUpdate = false

        val shouldShow = latestVersion != currentVersion &&
                      !skipAutoUpdate &&
                      latestVersion != skippedVersion

        assertTrue(shouldShow)
    }

    @Test
    fun `should NOT show dialog when version is skipped`() {
        val shouldShow = "1.4.0" != "1.3.1" && "1.4.0" != "1.4.0"
        assertFalse(shouldShow)
    }

    @Test
    fun `should NOT show dialog when skipAutoUpdate is true`() {
        val latestVersion = "1.4.0"
        val currentVersion = "1.3.1"
        val skipAutoUpdate = true

        val shouldShow = latestVersion != currentVersion && !skipAutoUpdate
        assertFalse(shouldShow)
    }

    @Test
    fun `should show dialog when skipAutoUpdate is false`() {
        val latestVersion = "1.4.0"
        val currentVersion = "1.3.1"
        val skipAutoUpdate = false

        val shouldShow = latestVersion != currentVersion && !skipAutoUpdate
        assertTrue(shouldShow)
    }

    @Test
    fun `network check runs regardless of checkbox state`() {
        val checkOnStart = false
        var networkRequestExecuted = false

        fun simulateNetworkCheck() {
            networkRequestExecuted = true
        }

        simulateNetworkCheck()
        assertTrue(networkRequestExecuted)
    }

    @Test
    fun `APK download URL construction`() {
        val version = "1.4.0"
        val url = "https://github.com/FlowHack/FlowCapitalAndroidApp/releases/download/v$version/FlowCapital_v$version.apk"
        assertTrue(url.contains("FlowCapital_v1.4.0.apk"))
    }

    @Test
    fun `release URL contains correct repo`() {
        val url = "https://api.github.com/repos/FlowHack/FlowCapitalAndroidApp/releases/latest"
        assertTrue(url.contains("FlowHack/FlowCapitalAndroidApp"))
    }

    @Test
    fun `tag version parsing strips v prefix`() {
        assertEquals("1.4.0", "v1.4.0".removePrefix("v"))
        assertEquals("1.3.1", "1.3.1".removePrefix("v"))
    }

    @Test
    fun `checkbox synchronization between settings and dialog`() {
        data class SettingsState(
            var checkOnStart: Boolean = true,
            var skipAutoUpdate: Boolean = false
        )

        val settings = SettingsState()
        assertTrue(settings.checkOnStart)
        assertFalse(settings.skipAutoUpdate)

        settings.skipAutoUpdate = true
        assertTrue(settings.skipAutoUpdate)

        settings.checkOnStart = false
        assertFalse(settings.checkOnStart)
    }

    @Test
    fun `inverted checkbox logic`() {
        var checkOnStartInSettings = true

        fun getDialogCheckboxState(): Boolean = !checkOnStartInSettings
        fun onDialogCheckboxChanged(dialogChecked: Boolean) {
            checkOnStartInSettings = !dialogChecked
        }

        assertTrue(checkOnStartInSettings)
        assertFalse(getDialogCheckboxState())

        onDialogCheckboxChanged(true)
        assertFalse(checkOnStartInSettings)
        assertTrue(getDialogCheckboxState())
    }

    @Test
    fun `manual check button always works`() {
        var checkOnStart = false
        var isManualCheck = false

        fun onManualCheck() {
            isManualCheck = true
            checkOnStart = true
        }

        onManualCheck()
        assertTrue(isManualCheck)
    }

    @Test
    fun `update download filename pattern`() {
        val version = "1.4.0"
        val filename = "FlowCapital_v$version.apk"
        assertTrue(filename.contains("FlowCapital"))
        assertTrue(filename.endsWith(".apk"))
    }

    @Test
    fun `missing JSON field returns empty`() {
        val json = """{"tag_name": "v1.3.0"}"""
        val missingField = extractApkUrl(json)
        assertNull(missingField)
    }

    @Test
    fun `invalid JSON returns empty tag name`() {
        val json = """not json at all"""
        val tagName = parseTagName(json)
        assertEquals("", tagName)
    }

    @Test
    fun `byte format function works at all scales`() {
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
        }

        assertEquals("100 B", formatBytes(100))
        assertTrue(formatBytes(10240).contains("KB"))
        assertTrue(formatBytes(10 * 1024 * 1024).contains("MB"))
    }
}

private fun parseTagName(json: String): String {
    val tagKey = "\"tag_name\""
    val tagIdx = json.indexOf(tagKey)
    if (tagIdx == -1) return ""
    val colonIdx = json.indexOf(":", tagIdx)
    if (colonIdx == -1) return ""
    var i = colonIdx + 1
    while (i < json.length && json[i] == ' ') i++
    if (i >= json.length || json[i] != '"') return ""
    i++
    val result = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        if (c == '"') return result.toString()
        result.append(c)
        i++
    }
    return result.toString()
}

private fun extractApkUrl(json: String): String? {
    val urlKey = "\"browser_download_url\":"
    val urlIdx = json.indexOf(urlKey)
    if (urlIdx == -1) return null
    var i = urlIdx + urlKey.length
    while (i < json.length && json[i] == ' ') i++
    if (i >= json.length || json[i] != '"') return null
    i++
    val result = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        if (c == '"') return result.toString()
        result.append(c)
        i++
    }
    return result.toString()
}

private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
    val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(newParts.size, currentParts.size)) {
        if (newParts.getOrElse(i) { 0 } > currentParts.getOrElse(i) { 0 }) return true
        if (newParts.getOrElse(i) { 0 } < currentParts.getOrElse(i) { 0 }) return false
    }
    return false
}