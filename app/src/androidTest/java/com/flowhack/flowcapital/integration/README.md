# Интеграционные тесты FlowCapital (androidTest)

## Описание

Данная папка содержит интеграционные тесты для приложения FlowCapital. В отличие от юнит-тестов (`src/test/`), которые тестируют изолированную бизнес-логику, интеграционные тесты проверяют реальную связку компонентов системы:

- **Room Database (DAO)** — реальная работа с базой данных (in-memory)
- **Репозитории** — связка forecast-функция -> репозиторий -> БД
- **Взаимодействие слоев** — Repository, DAO, Entity

## Структура тестов

### 1. BaseIntegrationTest.kt
Базовый класс для всех интеграционных тестов.
- Создает in-memory базу Room для изоляции тестов
- Инициализирует все DAO: `GrowingFlowDao`, `NoviceFlowDao`, `NoviceFlowsDao`, `PremiumStartFlowDao`, `PremiumStartPeriodDao`
- Очищает базу после каждого теста

### 2. GrowingFlowIntegrationTest.kt (РП - Растущий Поток)
Проверяет связку `calculateFlowForecast` -> `GrowingFlowRepository` -> БД:

| Тест | Описание |
|------|----------|
| forecast_savesAllRecordsToDb | Прогноз РП сохраняется в БД через репозиторий |
| getLastEntry_returnsMostRecentRecord | getLastEntry возвращает последнюю запись |
| getEntriesForDateRange_returnsRecordsInRange | getEntriesForDateRange возвращает записи за диапазон |
| getLastEntryBeforeDate_returnsRecordBeforeDate | getLastEntryBeforeDate возвращает запись до даты |
| getFirstStartEntry_returnsFirstStartRecord | getFirstStartEntry возвращает первую START |
| getLastPressEntry_returnsLastPressedRecord | getLastPressEntry возвращает запись с нажатой кнопкой |
| clearHistory_removesAllRecords | clearHistory очищает все записи |
| updateEntry_updatesExistingRecord | updateEntry обновляет запись |

### 3. NoviceFlowIntegrationTest.kt (ПН - Поток Новичка)
Проверяет связку `calculateNoviceFlowForecast` -> `NoviceFlowRepository` -> БД:

| Тест | Описание |
|------|----------|
| forecast_savesAllRecordsToDb | Прогноз ПН сохраняется в БД через репозиторий |
| getLastEntry_returnsMostRecentRecord | getLastEntry возвращает последнюю запись |
| getAllEntries_returnsAllRecords | getAllEntries возвращает все записи |
| getEntriesForDateRange_returnsRecordsInRange | getEntriesForDateRange возвращает записи за диапазон |
| getLastEntryBeforeDate_returnsRecordBeforeDate | getLastEntryBeforeDate возвращает запись до даты |
| getFirstStartEntry_returnsFirstStartRecord | getFirstStartEntry возвращает первую PN_START |
| getLastPressEntry_returnsLastPressedRecord | getLastPressEntry возвращает запись с нажатой кнопкой |
| clearHistory_removesAllRecords | clearHistory очищает все записи |
| updateEntry_updatesExistingRecord | updateEntry обновляет запись |

### 4. PremiumStartIntegrationTest.kt (ПСП - Премиум Старт)
Проверяет связку `PremiumStartFlowRepository` -> БД с якорным алгоритмом дат (`calculatePspPeriodEndDate`):

| Тест | Описание |
|------|----------|
| createPspFlow_createsFlowInDb | Создание потока ПСП сохраняется в БД |
| getFlowsCount_returnsNumberOfFlows | getFlowsCount возвращает количество потоков |
| createPspPeriods_usesAnchorAlgorithm | Создание периодов с якорным алгоритмом дат |
| getCurrentPeriod_returnsIncompletePeriod | getCurrentPeriod возвращает незавершённый период |
| getPeriodByNumber_returnsCorrectPeriod | getPeriodByNumber возвращает период по номеру |
| updatePeriod_updatesExistingPeriod | updatePeriod обновляет период |
| deleteFlow_removesFlowAndPeriods | deleteFlow удаляет поток и его периоды |
| clearAll_removesAllFlowsAndPeriods | clearAll очищает все потоки и периоды |
| updateFlow_updatesExistingFlow | updateFlow обновляет поток |

### 5. SettingsIntegrationTest.kt (NoviceFlowsDao v2)
Проверяет работу `NoviceFlowsDao` (таблица novice_flows, Entity v2):

| Тест | Описание |
|------|----------|
| insertNoviceFlow_savesToDb | Вставка потока новичка v2 сохраняется в БД |
| getAllFlows_returnsAllFlows | getAllFlows возвращает все потоки |
| getFlowsCount_returnsNumberOfFlows | getFlowsCount возвращает количество потоков |
| update_updatesExistingFlow | update обновляет поток |
| deleteById_removesFlow | deleteById удаляет поток |
| clearAll_removesAllFlows | clearAll очищает все потоки |

## Запуск тестов

### Запуск всех интеграционных тестов:
```bash
./gradlew :app:connectedAndroidTest
```

### Запуск конкретного класса:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.flowhack.flowcapital.integration.GrowingFlowIntegrationTest
```

### Запуск на эмуляторе/устройстве:
1. Запустите эмулятор или подключите устройство
2. Убедитесь, что `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` в build.gradle.kts
3. Запустите тесты через IDE или команду выше

## Требования

- **Android SDK** — minSdk 28
- **Эмулятор или устройство** — тесты запускаются на реальном Android (не JVM)
- **Room Testing** — `androidx.room:room-testing` (добавлено в build.gradle.kts)

## Покрытие

### Что покрыто:
✅ РП (Растущий Поток) — репозиторий + forecast (8 тестов)  
✅ ПН (Поток Новичка) — репозиторий + forecast (9 тестов)  
✅ ПСП (Премиум Старт) — репозиторий + якорный алгоритм (9 тестов)  
✅ NoviceFlowsDao v2 — таблица novice_flows (6 тестов)  

**Всего: 32 интеграционных теста**

### Что НЕ покрыто (трудно тестируемые компоненты):
❌ Браузер (WebView) — требует эмуляцию браузера  
❌ Прокси (ProxyController) — требует системных настроек  
❌ Уведомления (WorkManager) — требует интеграции с системой  
❌ Импорт/Экспорт — требует работы с файловой системой  
❌ Обновления (GitHub API) — требует сетевых запросов  

## Важные замечания

1. **In-memory база** — тесты используют in-memory Room, данные не сохраняются между тестами
2. **Изоляция** — каждый тест начинается с чистой базы данных
3. **runBlocking** — тесты используют `runBlocking` для синхронного выполнения корутин
4. **Якорный алгоритм ПСП** — даты периодов рассчитываются через `calculatePspPeriodEndDate` (2 периода в месяц, clamp дня к 28)

## Добавление новых тестов

1. Создайте новый файл в папке `integration/`
2. Наследуйтесь от `BaseIntegrationTest` (для доступа к DAO)
3. Используйте `@Test` аннотации
4. Для тестов репозиториев создавайте репозиторий в `setUp()` из DAO
