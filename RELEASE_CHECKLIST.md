# Release checklist

Проверено на реальном выпуске **v1.4.2** (2026-08-15). Каждый шаг — с ожидаемым результатом и способом проверки.

## 0. Предрелиз (код)

- [ ] Бамп версии в `android/app/build.gradle` (`versionName` / `versionCode`), закоммить `Release vX.Y.Z: bump version and release notes`
- [ ] Обновить секцию «Release notes (recent)» в `README.md` (и release-тело — из неё)
- [ ] Локальная сборка на JDK 21:
  ```bash
  cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    ./gradlew compileDebugKotlin testDebugUnitTest assembleRelease --console=plain
  # ожидание: BUILD SUCCESSFUL
  ```
- [ ] Lint (CI-шаг, который мы не гоняем на релизной сборке локально):
  ```bash
  ./gradlew lint   # ожидание: BUILD SUCCESSFUL, 0 ошибок
  ```
- [ ] Фиксированная точка: APK `android/app/build/outputs/apk/release/Void-Auditor-v<ver>.apk`
  - [ ] Записать `sha256sum` (идёт в README и релиз)

## 1. Устройство (опционально, но желательно)

- [ ] `adb install -r <apk>` → `Success`
- [ ] Проверка версии: `dumpsys package com.kuzyamond.voidauditor | grep version`
- [ ] Запуск: `am start -W` → `Status: ok`; процесс жив (`pidof`); 0 `FATAL EXCEPTION` в logcat

## 2. CI (ожидание: зелёный)

- [ ] Пуш в `main` → воркфлоу **Android Build** (JDK 21, assembleDebug → test → lint) и **Pages**
- [ ] Проверка: `https://github.com/M0NDsuChTiG/Void-Auditor/actions` — оба **completed / success**

> ⚠️ **Известный блокер (2026-08):** «The job was not started because your account is locked
> due to a billing issue» — раннеры не стартуют, если у аккаунта GitHub проблемы с биллингом.
> Это **не** проблема кода: локально всё собирается. Чинится в GitHub → Settings → Billing.
> Pages-деплой при этом работает.

## 3. Публикация

- [ ] Тег: `git tag v<ver> && git push origin v<ver>`
- [ ] `main` + тег в remote (проверка: `git ls-remote --tags origin | grep v<ver>`)
- [ ] Релиз через API (или `gh release create`):
  ```bash
  curl -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" -H "Content-Type: application/json" \
    --data @release_payload.json \
    https://api.github.com/repos/M0NDsuChTiG/Void-Auditor/releases
  # payload: {"tag_name":"v<ver>","name":"VOID Auditor v<ver>","body":"<notes>","draft":false}
  ```
- [ ] Загрузка APK-ассета:
  ```bash
  curl -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @<apk> \
    "https://uploads.github.com/repos/M0NDsuChTiG/Void-Auditor/releases/<id>/assets?name=Void-Auditor-v<ver>.apk"
  ```
- [ ] Проверка: `https://github.com/M0NDsuChTiG/Void-Auditor/releases/tag/v<ver>` → 200;
  прямой APK → 206/200; `sha256sum` скачанного == локальному

## 4. Пост-релиз (документация)

- [ ] `README.md` Download: бейдж, «Latest release», asset, sha256, `adb install` → commit
- [ ] Лендинг `docs/index.html`: все вхождения версии (hero/footer/i18n en+ru) + кнопка
      Direct APK на `releases/download/v<ver>/...` → commit → push
- [ ] Pages: дождаться пересборки (~45–60 c), проверить на живой странице
      вхождения новой версии и кнопку

## 5. Проверка целостности (единый ключ)

| Звено | Ожидание |
|---|---|
| Локальный APK == ассет релиза | sha256 совпадает, `cmp` IDENTICAL |
| README sha256 == ассет | совпадает |
| versionName в APK == тег | совпадает |
| Тег указывает на `main` | `target_commitish` = main |

---

## Автоматизация (следующие шаги)

- Добавить в CI шаг `assembleRelease` + upload ассета в релиз через `GITHUB_TOKEN`
  (когда упадёт блокировка биллинга) — релиз станет однокомандным.
- Синхронизировать `README.md` / `README_RU.md` / `README _New.md` (сейчас версии разъехались).
