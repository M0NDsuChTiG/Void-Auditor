# PERMISSION AUDIT — итоговый отчёт кампании минимизации

**Дата:** 2026-08-15
**Устройство:** Samsung SM-A115F (Android 11+, One UI)
**Модуль:** VOID Auditor → PERMS (Permission Audit, плитка в сетке HOME)
**Метод:** аудит 371 пакета через `dumpsys package` (только реально выданные разрешения, granted=true), скоринг по весам чувствительности; минимизация — только по факту фактического использования (`appops`), не по списку.

---

## 1. Общие цифры устройства (Scope: ALL, 371 приложений)

| Метрика | До кампании | После кампании |
|---|---|---|
| CRITICAL / HIGH | **69** | **65** |
| MEDIUM | 47 | **50** |
| LOW | 255 | **256** |
| Third-party в CRITICAL/HIGH | **5** | **2** |

Изменение на устройстве сделано напрямую через `pm revoke` / `settings` (через adb). Код приложения не менялся.

## 2. Трансформация 7 подозрительных third-party-приложений

| Приложение | Источник | Было | Стало | Отозвано |
|---|---|---|---|---|
| `com.topstep.fitcloudpro` (FitCloud Pro) | Play Store | **CRITICAL 33** | **CRITICAL 25** | SEND_SMS, READ_CALL_LOG, READ_CONTACTS; NotificationListener отключён |
| `org.telegram.messenger` (Telegram) | Play Store | **CRITICAL 19** | **CRITICAL 10** | FINE/COARSE_LOCATION, READ_PHONE_NUMBERS, READ_PHONE_STATE |
| `com.vivaldi.browser` (Vivaldi) | Play Store | **CRITICAL 13** | **MEDIUM 3** | FINE/COARSE_LOCATION, CAMERA, RECORD_AUDIO |
| `com.linkedin.android` (LinkedIn) | Play Store | **HIGH 7** | **MEDIUM 3** | RECORD_AUDIO, WRITE_EXTERNAL_STORAGE |
| `com.webmoney.my` (WebMoney) | Play Store | **HIGH 7** | **LOW 2** | FINE/COARSE_LOCATION (ни разу не использовались) |
| `im.vector.app` (Element) | Obtainium (sideload) | **HIGH 7** | **MEDIUM 5** | COARSE_LOCATION |
| `com.craxiom.networksurvey` (Craxiom) | F-Droid | **HIGH 7** | **HIGH 7** | — (оправдано, см. ниже) |

---

## 3. Детали по каждому приложению

### 3.1 FitCloud Pro — `com.topstep.fitcloudpro` (33 → 25, CRITICAL)
Фитнес-браслет TopStep. **Самое опасное приложение на устройстве.**

- **Что отозвано:** `SEND_SMS` (отправка SMS), `READ_CALL_LOG` (журнал звонков), `READ_CONTACTS`; отключён `MyNotificationListenerService` (читал **все уведомления всех приложений**, включая банковские OTP).
- **Оставлено:** `READ_SMS`/`RECEIVE_SMS` — зеркалирование входящих SMS на браслет (осознанный выбор пользователя).
- **Red flags, обнаруженные при разборе:**
  - `MySmsBroadcastReceiver` на `SMS_RECEIVED` с **mPriority = 2147483647** (Integer.MAX_VALUE) — максимальный приоритет перехвата, классический паттерн OTP-троянов.
  - `READ_ICC_SMS` (SMS с SIM) — не отзывается отдельно: это switch-op, управляемый `READ_SMS` (который оставлен). `WRITE_ICC_SMS` уже заблокирован.
  - Китайские SDK: ShareSDK (Tencent QQ), Sina Weibo.
  - Процесс агрессивно держится в фоне: `SCHEDULE_EXACT_ALARM` + `RUN_ANY_IN_BACKGROUND`, перезапускается после force-stop.
  - Приложение **активно читало** SMS + call log + phone state (appops: 20 минут до разбора).
- **Остаточный риск:** 25 (CRITICAL) — в основном за счёт зеркалирования SMS и QUERY_ALL_PACKAGES. Снизить дальше можно только отказавшись от функции зеркалирования или удалив приложение.

### 3.2 Telegram — `org.telegram.messenger` (19 → 10, CRITICAL-граница)
- **Что отозвано:** `FINE/COARSE_LOCATION` (не использовались 60 дней), `READ_PHONE_NUMBERS`, `READ_PHONE_STATE` (удобство входа; автоввод SMS-кода не затронут — Telegram использует Google `SMS_RETRIEVED`).
- **Оставлено (активно используется):** `CAMERA` (1 день), `RECORD_AUDIO` (8 часов — голосовые/звонки), `READ/WRITE_CONTACTS` (синк, чтение 2 часа назад), `READ_EXTERNAL_STORAGE`.
- **Заметки:** SMS-разрешений нет вовсе (вход по SMS через `SMS_RETRIEVED`), нет `READ_CALL_LOG`/`CALL_PHONE`, overlay запрещён. Остаток 10 = камера+микрофон (5) + контакты (4) + storage (1) — всё реально используемые функции. Ниже CRITICAL — только через отказ от активного синка контактов.

### 3.3 Vivaldi — `com.vivaldi.browser` (13 → 3, MEDIUM)
- **Что отозвано:** `FINE/COARSE_LOCATION` (не использовались 157 дней), `CAMERA` (218 дней), `RECORD_AUDIO` (239 дней). При необходимости сайты заново спросят разрешение.
- **Не отзывается:** `QUERY_ALL_PACKAGES` — install-time разрешение из манифеста (Vivaldi в allowlist-политике Google), `pm revoke` → *«not a changeable permission type»*. Снять можно только удалением приложения.
- **Заметки:** Chromium-форк, остаток 3 = QUERY_ALL_PACKAGES (2) + storage (1).

### 3.4 LinkedIn — `com.linkedin.android` (7 → 3, MEDIUM)
- **Что отозвано:** `RECORD_AUDIO` (44 дня, 23 сек — одно голосовое сообщение), `WRITE_EXTERNAL_STORAGE` (легаси на Android 13+, запись идёт через MediaStore/SAF).
- **Оставлено:** `CAMERA` — использована 4 дня назад (QR-вход / фото).

### 3.5 WebMoney — `com.webmoney.my` (7 → 2, LOW)
- **Что отозвано:** `FINE/COARSE_LOCATION` — **ни разу не использовались** (в appops нет ни одной записи доступа).
- **Оставлено:** `CAMERA` — QR-платежи, ядро кошелька (использована 95 дней назад).

### 3.6 Element — `im.vector.app` (7 → 5, MEDIUM)
- **Что отозвано:** `COARSE_LOCATION` (сужение).
- **Оставлено:** `FINE_LOCATION` (шаринг геопозиции в комнатах, 33 дня), `CAMERA` (фото, 33 дня) — приложение используется, дальнейшие отзывы ломают функции Matrix.
- **Заметки:** поставлен через Obtainium (sideload), не Play Store.

### 3.7 Craxiom Network Survey — `com.craxiom.networksurvey` (7 → 7, HIGH, без изменений)
- **Ничего не отозвано:** `FINE_LOCATION` + `WIFI_SCAN` + `MONITOR_HIGH_POWER_LOCATION` (использованы 14 дней назад) — **это и есть функция приложения** (геотегированный сетевой survey). Open source, установлен из F-Droid.
- Оправданный HIGH — не IOC.

---

## 4. Ключевые методические выводы

1. **Резать по факту, а не по списку.** appops показывает реальное использование: у WebMoney локация «allow» без единого доступа, у Telegram — активная камера/микрофон/контакты. Отзыв неиспользуемого — бесплатное снижение риска; отзыв используемого — поломка функции.
2. **QUERY_ALL_PACKAGES не отзывается** в runtime (install-time). Единственный способ — удаление приложения.
3. **Switch-appops** (READ_ICC_SMS ← READ_SMS) не блокируются отдельно: «deny» молча игнорируется, управление через родительское разрешение.
4. **Системные CRITICAL (com.android.\*, GMS, com.samsung.\*) — легитимный шум**, а не IOC. Их трогать нельзя — сломается устройство. Для угроз-хантинга важен third-party скоуп.
5. **Реальный угроз-паттерн** (FitCloud): MAX-priority SMS-receiver + NotificationListener + активное чтение SMS/call log в фоне — единственный с такой комбинацией на устройстве.

## 5. Как восстановить (при необходимости)

| Приложение | Разрешение | Путь восстановления |
|---|---|---|
| Telegram | Локация | Поделиться геопозицией в чате / People Nearby → система переспросит |
| Telegram | Контакты | Settings → Privacy → Sync contacts |
| Vivaldi | Камера/микрофон/локация | Любой сайт запросит заново при использовании |
| LinkedIn | Микрофон | Голосовое сообщение / звонок → переспросит |
| WebMoney | Локация | Настройки WebMoney → гео-функции |
| Element | Локация | Шаринг геопозиции в комнате |
| FitCloud | SMS/call log | Settings → Приложения → FitCloud Pro → Разрешения (при возврате к функциям браслета) |

## 6. Артефакты

- Финальный отчёт скана: `Downloads/VOID_Auditor_Reports/Permission_Audit_20260815_195639.txt` (371 приложение, полный перечень с группами и весами).
- Инструмент: модуль **PERMS** (Permission Audit) приложения VOID Auditor v1.4.2+ — парсер `dumpsys package` (One UI + AOSP форматы), реестр 33 опасных разрешений, SYS-тег для системных пакетов, экспорт через MediaStore.
- Юнит-тесты парсера: `PermissionAuditTest` (7 тестов, покрытие обоих форматов dumpsys, весов и SYS-флага).
