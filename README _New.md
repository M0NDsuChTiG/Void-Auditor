# VOID Auditor

> **Zero Trust mobile forensics toolkit** for Android  
> Shizuku · AI Governance · Risk Aggregator · Cache Cleaner · Network Scan

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://github.com/M0NDsuChTiG/Void-Auditor)
[![Shizuku](https://img.shields.io/badge/Shizuku-Enabled-8A2BE2)](https://shizuku.rikka.app/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Release](https://img.shields.io/github/v/release/M0NDsuChTiG/Void-Auditor)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)
[![CI](https://github.com/M0NDsuChTiG/Void-Auditor/actions/workflows/android-build.yml/badge.svg)](https://github.com/M0NDsuChTiG/Void-Auditor/actions)

**VOID Auditor** is a professional **no-root** Android security audit and forensics tool.  
It uses the [Shizuku](https://shizuku.rikka.app/) API for shell-level capabilities and optional Google Gemini for advisory analysis under a strict **governance** model (AI never executes commands directly).

🌐 Landing page: [m0ndsuchtig.github.io/Void-Auditor](https://m0ndsuchtig.github.io/Void-Auditor/)

---

## Features

| Module | Description |
|--------|-------------|
| **Security Dashboard** | Risk score 0–100, findings for ADB Wi‑Fi, Accessibility, SELinux, overlays, banking apps |
| **Cache Cleaner** | Scan external cache, dry-run, 3-factor confirm (`PURGE`), selective purge, honest `EXTERNAL_CACHE_ONLY` banner |
| **NET_SCAN** | Subnet host discovery: one Shizuku batch of parallel pings (~3s for /24), MAC via `/proc/net/arp`, common ports |
| **WIFI_ADB** | Honest status via `getprop`; enable path verifies port is active or reports `SETPROP_FAILED` / `VERIFY_FAILED` |
| **AI Forensics** | Gemini as advisor only: Intent Proposal → Policy Engine → human confirmation |
| **Banking Deep Scan** | Permissions, WebView, deep links, persistent services on finance-related packages |
| **Apps / Activities** | Force-stop, disable, clear data, activity launcher (with confirmation for destructive ops) |
| **Audit Trail (TRACE)** | Structured timeline of commands, risk, decisions, exit codes |
| **Terminal & Scripts** | Shell via Shizuku + preset scripts (audit, lockdown, cleanup, network) |
| **File Manager** | Browse/copy via Shizuku where policy allows |

### Security model

- **Zero Trust execution**: typed capabilities + Policy Engine + confirmation for high-risk actions  
- **AI is advisory only** — no direct shell from the model  
- Gemini API keys stored with **EncryptedSharedPreferences** (AES-256-GCM)  
- Audit logs in the **app-private** directory (not world-readable `/sdcard`)  
- Optional biometric / device credential gate at launch  

---

## Requirements

| Requirement | Notes |
|-------------|--------|
| **Android 8.0+** (API 26) | minSdk 26, targetSdk 35 |
| **Shizuku v13+** | Must be started (USB ADB or Wireless Debugging) and permission granted to VOID Auditor |
| **Optional** | Gemini API key for AI features |

**Limits (by design / platform):**

- Full internal `/data/data/*/cache` visibility usually needs **root**; without root the UI shows **EXTERNAL_CACHE_ONLY** and offers `pm trim-caches` where applicable  
- `ENABLE_WIFI_ADB` via `setprop` is often blocked by SELinux on non-root devices — the app reports failure honestly instead of fake “enabled”

---

## Download

[![Download APK](https://img.shields.io/badge/Download-v1.3.1_APK-34A853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)

**Latest release:** [v1.3.1](https://github.com/M0NDsuChTiG/Void-Auditor/releases/tag/v1.3.1)

```text
Asset: Void-Auditor-v1.3.1.apk
sha256: c6a7648456cc88dd885cf6d51f1563c19fc7912c4b7d913a196bfd7bf0d0130b
```

Or install from a PC:

```bash
adb install -r Void-Auditor-v1.3.1.apk
```

---

## Build from source

```bash
git clone https://github.com/M0NDsuChTiG/Void-Auditor.git
cd Void-Auditor/android
# JDK 17+ recommended (CI uses 17; some environments use 21)
./gradlew :app:assembleDebug
```

APK output:

```text
android/app/build/outputs/apk/debug/Void-Auditor-v<version>.apk
```

---

## Quick start

1. Install **Shizuku** from GitHub / Play and **start** it (pair Wireless Debugging or USB).  
2. Install VOID Auditor and open it → grant **Shizuku** permission when prompted.  
3. Optional: enter a **Gemini API key** in the AI screen (stored encrypted).  
4. Run **Security Dashboard** or **CACHE** scan → use dry-run before purge.  
5. **CONN** → check WIFI_ADB status; **NET_SCAN** → scan the current subnet.

---

## Release notes (recent)

### v1.3.1

- **WIFI_ADB honesty:** after `setprop` + restart `adbd`, success only if `getprop service.adb.tcp.port` matches; otherwise `VERIFY_FAILED`  
- `delay(800)` before verify to reduce false negatives on slow devices  
- Unit tests for SETPROP / VERIFY paths  

### v1.3

- Cache Cleaner E2E (dry-run → phrase confirm → purge → rescan)  
- NET_SCAN batch ping (one Shizuku IPC for the whole /24)  
- MAC resolution via Shizuku `cat /proc/net/arp`  
- Scan state retained across tab switches (`CacheScanStorage`)  

---

## Architecture (short)

```text
UI (Compose)
  → Capability / intent layer
  → PolicyEngine (allow / deny / confirm)
  → ShizukuExecutor (shell, timeouts, CommandResult)
  → AuditLogger / GlobalLog
```

AI path:

```text
Sanitized context → Gemini (analysis only)
  → IntentProposal (non-executable)
  → Validator + Policy
  → Human confirmation
  → Capability execution
```

---

## Screenshots

See the [`screenshots/`](screenshots/) folder in the repository.

---

## Security / responsible use

This tool is intended for **authorized** device audit and lab work only.  
Report vulnerabilities privately — see [SECURITY.md](SECURITY.md).

Do **not** use VOID Auditor to access devices or data without permission.

---

## License

[MIT](LICENSE)

---

## Links

- [Releases](https://github.com/M0NDsuChTiG/Void-Auditor/releases)  
- [Issues](https://github.com/M0NDsuChTiG/Void-Auditor/issues)  
- [Security policy](SECURITY.md)  
- [Russian README](README_RU.md) (if present)  
- Telegram: [@kuzyamond](https://t.me/kuzyamond)

**Stack:** Kotlin · Jetpack Compose · Material 3 · Shizuku API 13 · Gemini (optional) · minSdk 26 · targetSdk 35
