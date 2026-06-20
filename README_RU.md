# Техническая документация VOID Auditor

## 1. Обзор проекта
**VOID Auditor** — автономное приложение для Android, предназначенное для аудита безопасности, управления устройствами и анализа системы. Работает через **Shizuku API**, не требует root-прав или подключения к ПК.

## 2. Основная архитектура
- **Язык:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Привилегии:** Shizuku API 13
- **AI:** Google Gemini API (модели Flash / Flash Lite / 1.5 Flash)
- **Сборка:** Gradle + AGP

## 3. Ключевые функции

### A. Аудит безопасности
- Проверка SELinux, ADB, Accessibility Services
- Анализ опасных разрешений
- Детектирование банковских троянов
- Поиск подозрительных приложений

### B. Менеджер приложений (APPS)
- Фильтрация и просмотр установленных пакетов
- Пакетная заморозка/разморозка
- Принудительная остановка и очистка данных
- Запуск Activity по имени пакета

### C. AI Ассистент (Gemini)
- Автоматический аудит устройства в один клик
- Анализ логов и поиск IOC
- Оценка уровня риска (Низкий/Средний/Высокий/Критический)
- Поддержка русского языка

### D. Файловая система (FS)
- Просмотр файлов и каталогов
- Аудит памяти через `du`
- Чтение текстовых файлов и конфигураций

### E. Исполнитель скриптов (Scripts)
- Запуск Bash и Python скриптов через Shizuku
- Встроенные шаблоны: Security Audit, Lockdown, Network Scan
- Сохранение и экспорт скриптов

### F. Терминал (Local Shell)
- Интерактивный shell с Shizuku-привилегиями
- Встроенная шпаргалка по командам

### G. Резервное копирование (Backup)
- Извлечение APK установленных приложений
- Отслеживание статуса операций

## 4. Философия дизайна (Cyberpunk Edition)
- **Тема:** Тёмная по умолчанию с неоновыми акцентами
- **Шрифты:** IBM Plex Mono / Space Mono для данных, Syne для заголовков
- **Отклик:** Тактильная отдача, анимация сканирования

## 5. Протоколы безопасности
- Подтверждение деструктивных операций
- Полная опора на модель разрешений Shizuku
- Логирование каждой команды с меткой времени

## 6. Сборка и развертывание
```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

## 7. Структура проекта
```
android/app/src/main/java/com/kuzyamond/voidauditor/
├── core/
│   └── ShizukuExecutor.kt
├── AIAssistantScreen.kt
├── AppManagerScreen.kt
├── ActivityLauncherScreen.kt
├── BackupScreen.kt
├── ConnectScreen.kt
├── DashboardScreen.kt
├── FilesScreen.kt
├── TerminalScreen.kt
├── ScriptsScreen.kt
├── MainActivity.kt
└── ShizukuManager.kt
```
