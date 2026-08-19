package com.flowhack.flowcapital.ui.screens.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты чистых функций определения сайта по URL.
 */
class BrowserSitesTest {

    @Test
    fun detectSiteName_withPotokUrl_returnsPotokCash() {
        assertEquals("ПОТОКCASH", detectSiteName("https://potok.cash/cabinet"))
    }

    @Test
    fun detectSiteName_withSberkassaUrl_returnsSberkassa() {
        assertEquals("СБЕРКАССА", detectSiteName("https://sberkassa.site/account"))
    }

    @Test
    fun detectSiteName_withEidUrl_returnsEid() {
        assertEquals("E-ID", detectSiteName("https://e-id.cards/"))
    }

    @Test
    fun detectSiteName_withBlackbitUrl_returnsBlackbit() {
        assertEquals("BLACKBIT", detectSiteName("https://blackbit.exchange/"))
    }

    @Test
    fun detectSiteName_withErubUrl_returnsErub() {
        assertEquals("ERUB", detectSiteName("https://erub.site/"))
    }

    @Test
    fun detectSiteName_withUnknownUrl_returnsNull() {
        assertNull(detectSiteName("https://example.com/"))
    }

    @Test
    fun detectSiteName_withExtraQueryParams_stillDetectsSite() {
        assertEquals("ПОТОКCASH", detectSiteName("https://potok.cash/cabinet?ref=123"))
    }

    @Test
    fun siteIconRes_withKnownUrl_returnsIconResource() {
        val icon = siteIconRes("https://potok.cash/cabinet")
        assertEquals(sites.first { it.name == "ПОТОКCASH" }.iconRes, icon)
    }

    @Test
    fun siteIconRes_withUrlWithoutPath_returnsIconResource() {
        val icon = siteIconRes("https://potok.cash/")
        assertEquals(sites.first { it.name == "ПОТОКCASH" }.iconRes, icon)
    }

    @Test
    fun siteIconRes_withUnknownUrl_returnsNull() {
        assertNull(siteIconRes("https://example.com/"))
    }

    @Test
    fun sites_listIsNotEmptyAndUrlsUnique() {
        assertEquals(5, sites.size)
        assertEquals(sites.size, sites.map { it.url }.distinct().size)
    }
}