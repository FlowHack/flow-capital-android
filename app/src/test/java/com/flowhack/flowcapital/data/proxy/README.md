# Тесты прокси-логики (data/proxy)

## Общая информация
Тесты покрывают чистую JVM-логику валидации прокси и расчёта бонусов E-currency.
Не требуют Android-зависимостей.

## Структура тестов

### ProxyValidatorTest.kt
Проверяет логику `ProxyValidator`:
1. **validateIpAddress_validIp_returnsTrue** - Валидные IP-адреса
2. **validateIpAddress_invalidIp_returnsFalse** - Невалидные IP-адреса
3. **validatePort_validPort_returnsTrue** - Валидные порты
4. **validatePort_invalidPort_returnsFalse** - Невалидные порты
5. **validateSocks5Proxy_validConfig_returnsValid** - Валидная SOCKS5 конфигурация
6. **validateSocks5Proxy_invalidIp_returnsError** - Невалидный IP в SOCKS5
7. **validateSocks5Proxy_missingCredentials_returnsErrors** - Отсутствие логина/пароля
8. **validateMtProtoProxy_validConfig_returnsValid** - Валидная MTProto конфигурация
9. **validateMtProtoProxy_missingSecret_returnsError** - Отсутствие ключа MTProto

### ProxyECurrencyBonusTest.kt
Проверяет логику `ProxyECurrencyBonus`:
1. **calculateBonus_amountAboveMillion_returnsDouble** - Бонус 200% при сумме >= 1 000 000
2. **calculateBonus_amountAt500k_returns175Percent** - Бонус 175% при сумме >= 500 000
3. **calculateBonus_amountAt100k_returns150Percent** - Бонус 150% при сумме >= 100 000
4. **calculateBonus_amountAt10k_returns100Percent** - Бонус 100% при сумме >= 10 000
5. **calculateBonus_amountAt5k_returns75Percent** - Бонус 75% при сумме >= 5 000
6. **calculateBonus_amountAt1k_returns50Percent** - Бонус 50% при сумме >= 1 000
7. **calculateBonus_amountBelow1k_returnsZero** - Бонус 0% при сумме < 1 000
8. **getTotalWithBonus_returnsAmountPlusBonus** - Итоговая сумма с бонусом

## Запуск тестов
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
cd /mnt/p/AndroidStudioProjects/flow-capital-android-app/flow-capital-android
./gradlew :app:testDebugUnitTest --no-daemon
```
