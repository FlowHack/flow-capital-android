# Тесты экрана браузера

## BrowserSitesTest
Проверяет чистые функции определения сайта по URL:

- `detectSiteName_withPotokUrl_returnsPotokCash` — URL ПОТОКCASH распознаётся как «ПОТОКCASH».
- `detectSiteName_withSberkassaUrl_returnsSberkassa` — URL СБЕРКАССА распознаётся как «СБЕРКАССА».
- `detectSiteName_withEidUrl_returnsEid` — URL E-ID распознаётся как «E-ID».
- `detectSiteName_withBlackbitUrl_returnsBlackbit` — URL BLACKBIT распознаётся как «BLACKBIT».
- `detectSiteName_withErubUrl_returnsErub` — URL ERUB распознаётся как «ERUB».
- `detectSiteName_withUnknownUrl_returnsNull` — неизвестный URL возвращает null.
- `detectSiteName_withExtraQueryParams_stillDetectsSite` — URL с query-параметрами всё равно распознаётся.
- `siteIconRes_withKnownUrl_returnsIconResource` — для известного URL возвращается ресурс иконки.
- `siteIconRes_withUnknownUrl_returnsNull` — для неизвестного URL возвращается null.
- `sites_listIsNotEmptyAndUrlsUnique` — список сайтов не пуст, URL уникальны.