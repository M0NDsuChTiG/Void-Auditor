# 🌑 VOID Auditor

> **Мобильный Zero Trust Forensics Toolkit** для Android  
> Shizuku + AI Governance + Risk Aggregator + Banking Deep Scan

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://github.com/M0NDsuChTiG/Void-Auditor)
[![Shizuku](https://img.shields.io/badge/Shizuku-Enabled-8A2BE2)](https://shizuku.rikka.app/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://github.com/M0NDsuChTiG/Void-Auditor/actions/workflows/android-build.yml/badge.svg)](https://github.com/M0NDsuChTiG/Void-Auditor/actions)

**VOID Auditor** — профессиональный инструмент для аудита безопасности Android-устройств **без root-прав**. Использует Shizuku API и Gemini AI с жёсткой governance-моделью.

---

### ✨ Ключевые Возможности

- **Zero Trust Execution** — Capability System + Policy Engine + обязательное подтверждение пользователя
- **Security Dashboard** — автоматический Risk Score (0–100) с рекомендациями
- **Banking Deep Scan** — специализированный анализ финансовых приложений (Unibank, Sima, LeoPay и др.)
- **AI Governance Layer** — Gemini работает только как Advisor (Intent Proposal System)
- **Structured Audit Trail** — полный forensic timeline всех действий
- **CyberHack UI** — современный тёмный интерфейс с цветовой индикацией рисков

---

### 🚀 Быстрая Установка

Скачать последнюю версию:

[![Download APK](https://img.shields.io/badge/Download-Latest_APK-34A853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)

Или собрать из исходников:

```bash
git clone https://github.com/M0NDsuChTiG/Void-Auditor.git
cd Void-Auditor/android
./gradlew assembleDebug
```

---

### 📋 Поддерживаемые Устройства

| Android | Статус | Shizuku | Примечание |
|---------|--------|---------|------------|
| 8.0+ (API 26) | ✅ Полная | Полная | Рекомендуется |
| 11+ | ✅ Отличная | Полная | Scoped Storage |
| 14–15 | ✅ Полная | Полная | Тестировано |

---

### 🛠 Основные Функции

| Функция | Описание |
|---------|----------|
| **Security Dashboard** | Общий уровень риска устройства + автоматический анализ |
| **Device Audit** | 12+ системных команд с AI-анализом (SELinux, ADB Wi-Fi, Accessibility, банковские приложения и т.д.) |
| **Banking Deep Scan** | Поиск опасных разрешений, WebView, Deep Links, persistent services в финансовых приложениях |
| **AI Assistant** | Governed forensic reasoning: только Intent Proposal → Policy Engine → Human Confirmation |
| **Audit Trail (TRACE)** | Полная история всех команд с метками времени, риском и статусом |
| **Apps Manager** | Force-stop, disable, clear data, batch-операции с подтверждением |

---

### 🛡️ Протоколы Безопасности

- Все деструктивные действия требуют явного подтверждения
- API-ключи Gemini хранятся в EncryptedSharedPreferences (AES-256-GCM)
- Логи аудита — только в приватной директории приложения
- Биометрия/PIN при запуске (опционально)

---

### 📁 Структура Проекта

```
android/app/src/main/java/com/kuzyamond/voidauditor/
├── core/
│   ├── ShizukuExecutor.kt
│   ├── Capability.kt
│   ├── PolicyEngine.kt
│   ├── SecurityModule.kt
│   └── ai/
├── AIAssistantScreen.kt
├── DashboardScreen.kt
├── AppManagerScreen.kt
├── AuditScreen.kt
└── ...
```

---

<div align="center">

Разработано с ❤️ для Android Security Researchers

[GitHub Issues](https://github.com/M0NDsuChTiG/Void-Auditor/issues) · [Telegram](https://t.me/EthicalHackingCS)

</div>
