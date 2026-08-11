# SESSION.md — Cache Scanner refactor

## Цель
Рефакторинг `CacheScanner` + `PathSanitizer`: вынести `SAFE_SUFFIX` в `PathSanitizer`, переписать `CacheScanner` с `buildNameExpr()` + `discoverCacheDirs()`, убрать дублирование.

## Изменённые файлы

### 1. `PathSanitizer.kt`
- `SAFE_SUFFIX` → `internal val SAFE_SUFFIX` (теперь доступен из `CacheScanner`)
- Удалён мёртвый метод `buildFindNameExpr()` (заменён на `buildNameExpr()` в `CacheScanner`)

### 2. `build.gradle`
- `versionName "1.2"` (бамп)

### 3. `CacheScanner.kt`
- Константы вынесены в `companion object`:
  - `MIN_DEPTH = 2`, `QUICK_MAX_DEPTH = 3`, `FULL_MAX_DEPTH = 4`, `DEEP_MAX_DEPTH = 4`
  - `SUFFIX_NAME_REGEX = Regex("^[a-zA-Z0-9_]+$")` — для отсеивания невалидных имён из `SAFE_SUFFIX`
- Добавлен `buildNameExpr()`:
  - Фильтрует `PathSanitizer.SAFE_SUFFIX` через `SUFFIX_NAME_REGEX`
  - Генерирует `\( -false -o -name "cache" -o -name "code_cache" -o ... \)`
- Добавлен `discoverCacheDirs(capability)`:
  - QUICK/FULL: корни `/data/data` + `/sdcard/Android/data`
  - DEEP: ещё `/data/user_de/0`
  - Глубина: QUICK=3, FULL=4, DEEP=4
  - Строит `find`-команду через `buildNameExpr()`
  - Дедупликация: `distinctBy` с заменой `/data/user/0/` → `/data/data/` и `/storage/emulated/0/` → `/sdcard/`
- `scan()` теперь вызывает `discoverCacheDirs()` вместо старого `startScan()`
- Методы `inspectDir()` и `formatSize()` остались без изменений
