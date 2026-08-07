# Тесты бизнес-логики прогнозов (data/forecast)

## Общая информация
Тесты покрывают основные алгоритмы расчета потоков согласно ТЗ.
Все тесты написаны на Kotlin с использованием JUnit.

## Структура тестов

### GrowingFlowForecastTest.kt (Растущий поток - РП)
Проверяет логику `calculateFlowForecast`:
1. **start_newFlow_createsStartRecordWithCorrectValues** - Старт потока создает запись START с правильными значениями
2. **start_newFlowOnWeekday_createsDailyOnSameDay** - В день старта (не воскресенье) сразу происходит начисление (DAILY)
3. **start_newFlowOnSunday_createsSundayRecord** - В воскресенье начисление не происходит, создается SUNDAY
4. **dailyAddition_percentGrowsEachDay** - Процент растет на dailyAddition при каждом нажатии
5. **forecast_stopsWhenInFlowBecomesZero** - Прогноз останавливается при inFlow <= 0
6. **existingFlow_dailyStartsNextDay** - Для действующего потока DAILY начинается только со следующего дня
7. **accrual_calculatedCorrectly** - Начисление правильно рассчитывается как inFlow * (percent / 100)
8. **sundayInMainLoop_createsSundayWithoutAccrual** - В основном цикле воскресенье создаёт SUNDAY без начисления
9. **accrualGreaterThanRemainingInFlow_limitsAccrualToInFlow** - Ветка minOf: начисление ограничивается остатком потока
10. **zeroInFlow_createsOnlyStartRecord** - Вход с inFlow=0 создаёт только START
11. **zeroPercent_createsNoAccrual** - Вход с percent=0 не создаёт начислений
12. **existingFlowOnSunday_createsStartAndSunday** - Действующий поток в воскресенье создаёт START и SUNDAY

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
9. **compoundInterest_reinvestAppliesBonusCorrectly** - Точная арифметика реинвеста с бонусом
10. **compoundInterestDisabled_noReinvestRecords** - При compoundInterest=false реинвест не происходит
11. **sundayInMainLoop_createsSundayWithoutAccrual** - В основном цикле воскресенье создаёт SUNDAY без начисления
12. **zeroInFlow_createsOnlyStartRecord** - Вход с inFlow=0 создаёт только PN_START
13. **zeroPercent_createsNoAccrual** - Вход с dailyPercent=0 не создаёт начислений

### PspFlowForecastTest.kt (Премиум Стартовый Поток - ПСП)
Проверяет логику `calculatePspForecast` и `calculatePspPeriodEndDate` (якорный алгоритм):
1. **forecast_createsExactly20Periods** - Прогноз создает ровно 20 периодов
2. **forecast_periodsAreSequential** - Периоды идут последовательно от 1 до 20
3. **forecast_accrualCalculatedCorrectly** - Расчет начисления для периода (nominal * percent / 100)
4. **forecast_startDay1_generatesCorrectDates** - Якорный алгоритм при startDay=1 (якоря [1, 15])
5. **forecast_startDay24_generatesCorrectDates** - Якорный алгоритм при startDay=24 (якоря [10, 24])
6. **forecast_startDay31_edgeDay_handledCorrectly** - Clamp дня 31 -> 28
7. **forecast_yearTransition_handledCorrectly** - Переход через границу года
8. **forecast_totalAccruedAccumulates** - "Всего начислено" (totalAccrued) накапливается с каждым периодом
9. **forecast_lastPeriodIsCompleted** - Последний период (20-й) имеет isCompleted = true
10. **forecast_missingCoefficientDefaultsToZero** - Если коэффициент для периода не найден, используется 0.0
11. **forecast_nominalPreservedInAllPeriods** - Номинал сохраняется для каждого периода
12. **periodEndDate_anchorDays_algorithm** - Прямая проверка якорного алгоритма
13. **periodEndDate_startDay29_31_clampedTo28** - Clamp дней 29, 31 -> 28
14. **periodEndDate_startDay30_clampedTo28** - Clamp дня 30 -> 28
15. **forecast_startDateOfPeriods_isPreviousEndDate** - startDate каждого периода = endDate предыдущего
16. **periodEndDate_startDay14_boundary_usesDay2** - Граница startDay=14 (ветка <=14)
17. **periodEndDate_startDay15_boundary_usesDay1** - Граница startDay=15 (ветка >14)

### MissedDaysCalculatorTest.kt (Генерация пропусков)
Проверяет логику `MissedDaysCalculator.checkDayForGrowingFlow` и `checkDayForNoviceFlow`:
1. **growingFlow_sundayWithoutRecord_shouldCreateSunday** - В воскресенье без SUNDAY создаётся SUNDAY
2. **growingFlow_sundayWithExistingRecord_shouldStopSundayCheck** - При наличии SUNDAY проверка останавливается
3. **growingFlow_weekdayWithoutDailyOrMissed_shouldCreateMissed** - В будний день без записей создаётся MISSED
4. **growingFlow_weekdayWithDaily_shouldStopMissedCheck** - При наличии DAILY проверка пропусков останавливается
5. **growingFlow_dayWithStart_shouldBreak** - День со START прерывает цикл
6. **growingFlow_startDaySundayWithoutSundayRecord_shouldCreateSundayAndBreak** - START+воскресенье создаёт SUNDAY и прерывает
7. **noviceFlow_sundayWithoutRecord_shouldCreateSunday** - SUNDAY для ПН
8. **noviceFlow_weekdayWithoutDailyOrMissed_shouldCreateMissed** - MISSED для ПН
9. **noviceFlow_dayWithStart_shouldBreak** - break для ПН
10. **noviceFlow_weekdayWithDaily_shouldStopMissedCheck** - stop missed для ПН
11. **growingFlow_firstIterationSunday_shouldCreateSunday** - isFirstIteration в воскресенье создаёт SUNDAY
12. **growingFlow_weekdayWithMissedButNoDaily_shouldStopMissedCheck** - MISSED без DAILY останавливает проверку
13. **growingFlow_startDayWithDaily_shouldNotCreateMissed** - START с DAILY не создаёт MISSED
14. **growingFlow_startDaySundayWithExistingSunday_shouldStopSundayCheck** - START с SUNDAY останавливает проверку
15. **growingFlow_firstIterationSundayWithExistingSunday_shouldStopSundayCheck** - isFirstIteration с SUNDAY останавливает проверку
16. **growingFlow_firstIterationWeekday_returnsEmptyResult** - isFirstIteration в будний день - пустой результат
17. **growingFlow_sundayWithNeedMissedCheck_returnsEmptyResult** - Воскресенье при needMissedCheck=true - пустой результат
18. **growingFlow_weekdayWithNoChecks_returnsEmptyResult** - Будний день без проверок - пустой результат

## Запуск тестов
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
cd /mnt/p/AndroidStudioProjects/flow-capital-android-app/flow-capital-android
./gradlew :app:testDebugUnitTest --no-daemon
```
