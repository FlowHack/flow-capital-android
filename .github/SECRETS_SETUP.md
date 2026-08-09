# Настройка GitHub Secrets для CI/CD

Для работы release-сборки (подписанный APK) нужно настроить 4 секрета в GitHub.

## Как добавить Secrets

1. Открой репозиторий на GitHub.
2. Перейди: **Settings** → **Secrets and variables** → **Actions**.
3. Нажми **New repository secret** для каждого секрета ниже.

## Список секретов

### 1. `RELEASE_KEYSTORE_BASE64` (обязательно)
Keystore-файл в виде base64-строки.

**Что такое base64 и зачем он нужен?**
GitHub Secrets хранят только текст. Файл `release-keystore.jks` — бинарный (нечитаемый). base64 превращает бинарный файл в текстовую строку, которую можно вставить в Secret. В CI эта строка декодируется обратно в файл.

**Как получить base64-строку:**
Открой терминал в корне проекта (где лежит `release-keystore.jks`) и выполни:

```bash
base64 -w0 release-keystore.jks
```

Выведется одна длинная строка. Скопируй её целиком и вставь в Secret `RELEASE_KEYSTORE_BASE64`.

> **Важно:** строка может быть очень длинной (несколько тысяч символов). GitHub Secrets поддерживает до 64 КБ — этого достаточно.

### 2. `KEYSTORE_PASSWORD` (обязательно)
Пароль keystore. Возьми из файла `gradle.properties` (локально, в корне проекта) — значение `KEYSTORE_PASSWORD`.

### 3. `KEY_ALIAS` (обязательно)
Алиас ключа. Возьми из `gradle.properties` — значение `KEY_ALIAS` (например `flow-capital`).

### 4. `KEY_ALIAS_PASSWORD` (обязательно)
Пароль ключа. Возьми из `gradle.properties` — значение `KEY_ALIAS_PASSWORD`.

## Проверка

После добавления всех 4 секретов запусти release-сборку вручную:
**Actions** → **Release** → **Run workflow** → **Run workflow**.

Если всё настроено верно — соберётся подписанный APK и создастся GitHub Release.

## Безопасность

- Никогда не коммить `release-keystore.jks` и пароли в git (они уже в `.gitignore`).
- Secrets видны только в CI, в логах они маскируются.
