# Тесты уведомлений (notifications)

## ReminderGroupingTest.kt
Проверяет чистую функцию группировки умных напоминаний БП/СБП по времени последнего нажатия (`ReminderGrouping.withPressTimes`):

1. **withPressTimes_empty_returnsEmpty** - Пустой список -> пустой результат
2. **withPressTimes_single_returnsOneCluster** - Одиночное событие -> один кластер
3. **withPressTimes_withinMinute_mergesIntoOneCluster** - События в пределах ±1 минуты объединяются в один кластер
4. **withPressTimes_exactlyMinute_mergesIntoOneCluster** - Разница ровно в минуту -> один кластер
5. **withPressTimes_moreThanMinute_splitsIntoClusters** - Разница более минуты -> разные кластеры
6. **withPressTimes_cascade_mergesChain** - Каскадная цепочка событий (каждое в пределах минуты от соседа) -> один кластер
7. **withPressTimes_unsortedInput_sortsBeforeClustering** - Несортированный вход сортируется перед кластеризацией

## Запуск тестов
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
cd /mnt/p/AndroidStudioProjects/flow-capital-android-app/flow-capital-android
./gradlew :app:testDebugUnitTest --no-daemon
```
