package com.flowhack.flowcapital.ui.screens.browser

import com.flowhack.flowcapital.R

/**
 * Данные веб-сайтов для меню браузера.
 *
 * @param name Отображаемое имя сайта
 * @param url Адрес сайта
 * @param iconRes Ресурс иконки сайта
 */
data class WebSite(val name: String, val url: String, val iconRes: Int)

/** Список доступных сайтов в меню браузера. */
val sites = listOf(
    WebSite("ПОТОКCASH", "https://potok.cash/cabinet", R.drawable.logo_potok),
    WebSite("СБЕРКАССА", "https://sberkassa.site/account", R.drawable.logo_sberkassa),
    WebSite("E-ID", "https://e-id.cards/", R.drawable.logo_eid),
    WebSite("BLACKBIT", "https://blackbit.exchange/", R.drawable.logo_blackbit),
    WebSite("ERUB", "https://erub.site/", R.drawable.logo_erub)
)

/**
 * Определить имя сайта по URL.
 *
 * @param url Адрес сайта
 * @return Имя сайта или null, если сайт не распознан
 */
fun detectSiteName(url: String): String? = when {
    url.contains("potok.cash") -> "ПОТОКCASH"
    url.contains("sberkassa") -> "СБЕРКАССА"
    url.contains("e-id") -> "E-ID"
    url.contains("blackbit") -> "BLACKBIT"
    url.contains("erub") -> "ERUB"
    else -> null
}

/**
 * Получить ресурс иконки сайта по URL.
 *
 * @param url Адрес сайта
 * @return Ресурс иконки или null, если сайт не распознан
 */
fun siteIconRes(url: String): Int? = sites.find { url.startsWith(it.url) }?.iconRes
