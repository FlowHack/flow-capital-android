# Тесты бизнес-логики прогнозов (data/forecast)

## Общая информация
Тесты покрывают основные алгоритмы расчета потоков согласно ТЗ. 
Все тесты написаны на Kotlin с использованием JUnit.

## Структура тестов

### GrowingFlowForecastTest.kt (Растущий поток - РП)
Проверяет логику `calculateFlowForecast`:
1. **start_newFlow_createsStartRecordWithCorrectValues** - Старт потока создает запись START с правильными значениями
2. **start_newFlowOnWeekday_createsDailyOnSameDay** - В день старта (не воскресенье) сразу происходит начисление (DAILY)
3. **start_newFlowOnSunday_createsSundayInsteadOfDaily** - В воскресенье начисление не происходит, создается SUNDAY
4. **dailyAddition_percentGrowsEachDay** - Процент растет на dailyAddition при каждом нажатии
5. **forecast_stopsWhenInFlowBecomesZero** - Прогноз останавливается при inFlow <= 0
6. **existingFlow_dailyStartsNextDay** - Для действующего потока DAILY начинается только со следующего дня
7. **sunday_doesNotCreateDailyRecord** - SUNDAY запись не увеличивает активные действия
8. **accrual_calculatedCorrectly** - Начисление правильно рассчитывается как inFlow * (percent / 100)

### NoviceFlowForecastTest.kt (Поток Новичка - ПН)
Проверяет логику `calculateNoviceFlowForecast`:
1. **start_newFlow_createsPnStartRecord** - Старт потока создает PN_START запись
2. **start_newFlowOnWeekday_createsDailyOnSameDay** - В день старта (не воскресенье) сразу происходит начисление (PN_DAILY)
3. **start_newFlowOnSunday_createsSundayRecord** - В воскресенье начисление не происходит, создается SUNDAY
4. **daily_percentRemainsFixed** - Процент фиксированный (не растет как в РП)
5. **compoundInterest_reinvestWhenWalletReachesThreshold** - Сложный процент: реинвест при накоплении кошелька
6. **existingFlow_dailyStartsNextDay** - Для действующего потока DAILY начинается только со следующего дня
7. **accrual_calculatedCorrectly** - Начисление рассчитывается как inFlow * (dailyPercent / 100)
8. **forecast_stopsWhenInFlowBecomesZero** - Прогноз останавливается при inFlow <= 0

### PspFlowForecastTest.kt (Премиум Стартовый Поток - ПСП)
Проверяет логику `calculatePspForecast`:
1. **forecast_createsExactly20Periods** - Прогноз создает ровно 20 периодов
2. **forecast_periodsAreSequential** - Периоды идут последовательно от 1 до 20
3. **forecast_accrualCalculatedCorrectly** - Расчет начисления для периода (nominal * percent / 100)
4. **forecast_periodEndDateCalculatedCorrectly** - Дата окончания периода рассчитывается как startDate + (periodNum * 14 дней)
5. **forecast_totalAccruedAccumulates** - "Всего начислено" (totalAccrued) накапливается с каждым периодом
6. **forecast_lastPeriodIsCompleted** - Последний период (20-й) имеет isCompleted = true
7. **forecast_missingCoefficientDefaultsToZero** - Если коэффициент для периода не найден, используется 0.0
8. **forecast_nominalPreservedInAllPeriods** - Номинал сохраняется для каждого периода

## Запуск тестов
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
cd /mnt/p/AndroidStudioProjects/flow-capital-android-app/flow-capital-android
./gradlew :app:testDebugUnitTest --no-daemon
```

## Исправления в коде (сделано в рамках Т1)
1. **GrowingFlowForecast.kt**: Исправлена логика начислений - поле `dailyAccrual` теперь содержит начисление за текущий день, а не следующий
2. **GrowingFlowForecast.kt**: Добавлена обработка воскресений при старте (создание SUNDAY записи)
3. **NoviceFlowForecast.kt**: Аналогично исправлена логика начислений
4. **NoviceFlowForecast.kt**: Добавлена обработка воскресений при старте

## Исправления в коде (сделано в рамках Т1)
1. **GrowingFlowForecast.kt**: Исправлена логика начислений - поле `dailyAccrual` теперь содержит начисление за текущий день, а не следующий
2. **GrowingFlowForecast.kt**: Добавлена обработка воскресений при старте (создание SUNDAY записи)
3. **NoviceFlowForecast.kt**: Аналогично исправлена логика начислений
4. **NoviceFlowForecast.kt**: Добавлена обработка воскресений при старте
5. **MissedDaysCalculator.kt**: Создан чистый класс с бизнес-логикой генерации пропущенных дней (без зависимостей от БД)
6. **FlowViewModel.kt**: Использует MissedDaysCalculator для упрощения сложных функций generateMissedDays*
