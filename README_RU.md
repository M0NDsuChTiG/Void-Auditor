# VOID Auditor

> **Zero Trust мобильный форензик-инструмент** для Android
> Shizuku · AI Governance · Risk Aggregator · Cache Cleaner · Network Scan

[![Лицензия: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Платформа](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://github.com/M0NDsuChTiG/Void-Auditor)
[![Shizuku](https://img.shields.io/badge/Shizuku-Enabled-8A2BE2)](https://shizuku.rikka.app/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Release](https://img.shields.io/github/v/release/M0NDsuChTiG/Void-Auditor)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)
[![CI](https://github.com/M0NDsuChTiG/Void-Auditor/actions/workflows/android-build.yml/badge.svg)](https://github.com/M0NDsuChTiG/Void-Auditor/actions)

**VOID Auditor** — профессиональный **no-root** инструмент аудита безопасности и форензики для Android.
Использует API [Shizuku](https://shizuku.rikka.app/) для shell-возможностей и опционально Google Gemini
для аналитики в строгой модели **governance** (AI никогда не выполняет команды напрямую).

🌐 Лендинг: [m0ndsuchtig.github.io/Void-Auditor](https://m0ndsuchtig.github.io/Void-Auditor/)

---

## Возможности

| Модуль | Описание |
|--------|-------------|
| **Security Dashboard** | Риск-скоринг 0–100, находки по ADB Wi‑Fi, Accessibility, SELinux, оверлеям, банковским приложениям |
| **Cache Cleaner** | Скан внешнего кэша, dry-run, 3-факторное подтверждение (`PURGE`), честный баннер `EXTERNAL_CACHE_ONLY` |
| **NET_SCAN** | Поиск хостов подсети: один Shizuku-батч параллельных пингов (~3с для /24), MAC через `/proc/net/arp`, общие порты |
| **WIFI_ADB** | Честный статус через `getprop`; enable-путь проверяет активность порта или сообщает `SETPROP_FAILED` / `VERIFY_FAILED` |
| **AI Forensics** | Gemini только как советник: Intent Proposal → Policy Engine → подтверждение человеком |
| **Banking Deep Scan** | Разрешения, WebView, deep links, постоянные сервисы финансовых пакетов |
| **Apps / Activities** | Force-stop, disable, очистка данных, лаунчер Activity (с подтверждением деструктивных операций) |
| **Audit Trail (TRACE)** | Структурированная хронология команд, рисков, решений, exit-кодов |
| **Terminal & Scripts** | Shell через Shizuku + готовые скрипты (audit, lockdown, cleanup, network) |
| **File Manager** | Просмотр/копирование через Shizuku там, где позволяет политика |

### Модель безопасности

- **Zero Trust исполнение**: типизированные capabilities + Policy Engine + подтверждение для высокорисковых действий
- **AI только советник** — никакого прямого shell от модели
- Ключи Gemini API хранятся в **EncryptedSharedPreferences** (AES-256-GCM)
- Аудит-логи в **приватной** директории приложения (не в общедоступном `/sdcard`)
- Опциональный биометрический гейт / гейт по PIN при запуске

---

## Требования

| Требование | Примечания |
|-------------|--------|
| **Android 8.0+** (API 26) | minSdk 26, targetSdk 35 |
| **Shizuku v13+** | Должен быть запущен (USB ADB или Wireless Debugging), приложению выдано разрешение |
| **Опционально** | Ключ Gemini API для AI-функций |

**Ограничения (by design / платформа):**

- Полная видимость внутреннего `/data/data/*/cache` обычно требует **root**; без root UI показывает **EXTERNAL_CACHE_ONLY** и предлагает `pm trim-caches`, где применимо
- `ENABLE_WIFI_ADB` через `setprop` часто блокируется SELinux на no-root устройствах — приложение честно сообщает об ошибке вместо фейкового «enabled»

---

## Загрузка

[![Скачать APK](https://img.shields.io/badge/Download-v1.4.3_APK-34A853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)

**Последний релиз:** [v1.4.3](https://github.com/M0NDsuChTiG/Void-Auditor/releases/tag/v1.4.3)

```text
Asset: Void-Auditor-v1.4.3.apk
sha256: 87aa672200646d29ce92a0b7ecbd21866025822bd1b91054192f9607176fee6f
```

Или установка с ПК:

```bash
adb install -r Void-Auditor-v1.4.3.apk
```

---

## Сборка из исходников

```bash
git clone https://github.com/M0NDsuChTiG/Void-Auditor.git
cd Void-Auditor/android
# Требуется JDK 21 (CI использует 21)
./gradlew :app:assembleDebug
```

Вывод APK:

```text
android/app/build/outputs/apk/debug/Void-Auditor-v<версия>.apk
```

> ⚠️ **Требование сборки:** нужен **JDK 21 (Java 21)** — CI и локальная сборка рассчитаны на него. Проверьте `java -version` и при необходимости укажите JDK 21 явно:
>
> ```bash
> JAVA_HOME=/путь/к/jdk-21 ./gradlew assembleDebug
> ```

---

## Быстрый старт

1. Установите **Shizuku** (GitHub / Play) и **запустите** его (pair через Wireless Debugging или USB).
2. Установите VOID Auditor и откройте → выдайте разрешение **Shizuku** при запросе.
3. Опционально: введите **ключ Gemini API** на AI-экране (хранится зашифрованно).
4. Запустите **Security Dashboard** или скан **CACHE** → используйте dry-run перед очисткой.
5. **CONN** → проверьте статус WIFI_ADB; **NET_SCAN** → сканируйте текущую подсеть.

---

## История версий

### v1.4.3

- **Permission Audit (PERMS)** — новый модуль: аудит установленных приложений по **реально выданным** опасным разрешениям, парсится из `dumpsys package` (форматы One UI `requested`/`runtime` и инлайн AOSP). Реестр из 33 опасных разрешений, сгруппированных по чувствительности с весами риска (SMS / микрофон / локация / журнал звонков = 3, контакты / камера / телефон = 2, storage / календарь = 1, special app-ops = 2–3). По умолчанию — только сторонние приложения; режим **ALL** помечает системные пакеты тегом `[SYS]` и предупреждает, что там высокие баллы ожидаемы. Фильтры риска (LOW / MEDIUM / HIGH / CRITICAL), раскрывающиеся карточки, живой прогресс скана, экспорт отчёта в `Downloads/VOID_Auditor_Reports/` через MediaStore.
- **Юнит-тесты парсера** — 7 тестов: оба формата dumpsys, веса риска, SYS-флаг.
- **Структура документации** — техническая документация (`README_TECH.md`) и полевые отчёты (`PERMISSION_AUDIT_REPORT.md`, `VOID_Auditor_Report_NET_SCAN.md`) перенесены в [`docs/`](docs/README.md) с автоматизированной CI-проверкой ссылок (`docs-links.yml`).
- **Убран мёртвый Capacitor-каркас** — `android/settings.gradle` больше не подключает неиспользуемые проекты `:capacitor-android` / `:capacitor-haptics` / `:capacitor-cordova-android-plugins`; unqualified-задачи Gradle (`testDebugUnitTest`, `lint`) больше не падают с `invalid source release: 21` (латентный брейкер CI).
- **Документация** — [`docs/PERMISSION_AUDIT_REPORT.md`](docs/PERMISSION_AUDIT_REPORT.md): отчёт кампании минимизации (7 приложений, баллы до/после, отзывы по фактам использования).

### v1.4.2

- **Надёжность сканирования портов (NET_SCAN)**: 256 одновременных коннектов переполняли SYN-очередь роутера и молча теряли реально открытые порты (замеры: 31% успеха при 256, ~92% при 32). Теперь 32 параллельных коннекта с таймаутом 150мс, до 4 повторных проходов для потерянных SYN и адаптивная пауза между порциями. Проверено на устройстве: полный скан 65535 портов находит порты, которые старый сканер пропускал. `COMMON_PORTS` расширен портами **53 (DNS)** и **445 (SMB)**.
- **Логирование команд на всех экранах**: `ShizukuExecutor.init()` теперь вызывается глобально — строки `[CMD]`/`[RESULT]` появляются в консоли любого экрана (раньше — только после открытия AI-экрана).
- **Модели AI исправлены**: линейка Gemini 2.x выключена Google (2.0 удалены 1 июня 2026; 2.5 «no longer available to new users» → `API_ERR_404`). Переключено на **`gemini-3-flash-preview`** / **`gemini-3.1-flash-lite-preview`** (проверено HTTP 200 по ключу), с авто-миграцией сохранённой настройки модели.
- **Доступность (TalkBack)**: имена для всех кнопок-иконок (Send, Refresh, Save, Export, Back up, Info), контраст неактивных вкладок/`[COPY]`/`[CLEAR]`/чипов (WCAG 1.4.3), кнопки `+`/`−` высоты консоли как альтернатива drag (WCAG 2.5.7).

### v1.4.1

- **AI Governance (Intent Proposal)** — Gemini только советник: `PROPOSAL_JSON` (whitelisted `capabilityId`) → sanitize/parse/validate → диалог `AI_PROPOSAL_REVIEW` → PolicyEngine → CapabilityExecutor → AuditLogger (`ActorType.AI`). Деструктивные операции вне whitelist по дизайну.
- **Ключ Gemini API** в EncryptedSharedPreferences (AES-256-GCM) с автоматической миграцией из legacy prefs.
- **Закрыта command injection** в сетевом сканере — строгая валидация IPv4 перед shell-интерполяцией (`generateTargets` / `scanHosts`).
- **Gemini 429 UX** — один user-bubble на запрос, одна автоматическая попытка без рекурсии.
- **Banking deep scan** — детект опасных разрешений по-настоящему работает (regex вместо поиска подстроки).
- **Экспорт отчёта** через MediaStore (scoped storage, API 29+) с legacy-фолбэком.
- **Сборка/CI** — переезд на JDK 21, jetifier отключён, удалён мёртвый Capacitor-layout, юнит-тесты в CI.

---

## Архитектура (кратко)

```text
UI (Compose)
  → слой Capability / intent
  → PolicyEngine (allow / deny / confirm)
  → ShizukuExecutor (shell, таймауты, CommandResult)
  → AuditLogger / GlobalLog
```

AI-путь:

```text
Санитизированный контекст → Gemini (только анализ)
  → IntentProposal (неисполняемый)
  → Validator + Policy
  → Подтверждение человеком
  → Исполнение capability
```

---

## Скриншоты

Смотрите папку [`screenshots/`](screenshots/).

---

## Документация

📚 **Индекс:** [docs/README.md](docs/README.md) — все технические документы и отчёты в одном месте

| Документ | Описание |
|----------|-----------|
| [README_TECH.md](docs/README_TECH.md) | Техническая документация — архитектура, capabilities, PolicyEngine, Shizuku-слой, AI governance |
| [PERMISSION_AUDIT_REPORT.md](docs/PERMISSION_AUDIT_REPORT.md) | Отчёт кампании минимизации разрешений — 7 third-party приложений, баллы риска до/после, отзывы по фактам использования (`pm revoke` / `appops`) |
| [VOID_Auditor_Report_NET_SCAN.md](docs/VOID_Auditor_Report_NET_SCAN.md) | Отчёт по NET_SCAN — замеры надёжности порт-скана (параллельность 1–256, потери SYN-очереди), баннеры сервисов, полный скан 65535 портов |

---

## Безопасность / ответственное использование

Инструмент предназначен только для **авторизованного** аудита устройств и лабораторной работы.
Сообщайте об уязвимостях приватно — см. [SECURITY.md](SECURITY.md).

Не используйте VOID Auditor для доступа к устройствам или данным без разрешения.

---

## Лицензия

[MIT](LICENSE)

---

## Ссылки

- [Релизы](https://github.com/M0NDsuChTiG/Void-Auditor/releases)
- [Issues](https://github.com/M0NDsuChTiG/Void-Auditor/issues)
- [Политика безопасности](SECURITY.md)
- [Английский README](README.md)
- [Чек-лист релиза](RELEASE_CHECKLIST.md)
- Telegram: [EthicalHackingCS](https://t.me/EthicalHackingCS)

**Стек:** Kotlin · Jetpack Compose · Material 3 · Shizuku API 13 · Gemini (опционально) · minSdk 26 · targetSdk 35
