# Тесты менеджера настроек (data/settings)

## Общая информация
Тесты покрывают реальную логику `SettingsManager` через DataStore на временном файле
(без Android-зависимостей). Используется `PreferenceDataStoreFactory.create` с `File`.

## Структура тестов

### SettingsManagerTest.kt
Проверяет логику `SettingsManager`:
1. **savePercentages_writesStartAndDailyPercent** - Сохранение стартового и ежедневного процента РП
2. **savePnPercentages_writesBonusAndDailyPercent** - Сохранение бонусного и дневного процента ПН
3. **addReminder_addsTimeToReminders** - Добавление напоминания
4. **addReminder_limitsToFiveEntries** - Лимит 5 напоминаний
5. **removeReminder_deletesFromRemindersAndAlarms** - Удаление напоминания из обоих списков
6. **addPspReminder_limitsToFiveEntries** - Лимит 5 напоминаний ПСП
7. **setRpVip_enablesVipModeAndUpdatesCoefficients** - Включение РП VIP обновляет коэффициенты
8. **setRpVip_disablesVipModeAndRestoresDefaults** - Выключение РП VIP восстанавливает дефолты
9. **initializeDefaults_setsDefaultValuesForMissingKeys** - Инициализация дефолтов
10. **getECurrencyBonusPercent_returnsBonusForThreshold** - Расчёт бонуса E-currency по порогам
11. **setDarkTheme_savesDarkThemeSetting** - Сохранение тёмной темы
12. **setCheckUpdateOnStart_savesSetting** - Сохранение настройки проверки обновлений
13. **setSmartNotifications_savesSetting** - Сохранение умных уведомлений
14. **savePspCoefficients_updatesFlowAndPersists** - Сохранение коэффициентов ПСП
15. **saveBrowserFabOffset_savesOffsets** - Сохранение смещения FAB

## Запуск тестов
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
cd /mnt/p/AndroidStudioProjects/flow-capital-android-app/flow-capital-android
./gradlew :app:testDebugUnitTest --no-daemon
```
