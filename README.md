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

[![Download APK](https://img.shields.io/badge/Download-v1.4.3_APK-34A853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/M0NDsuChTiG/Void-Auditor/releases/latest)

**Latest release:** [v1.4.3](https://github.com/M0NDsuChTiG/Void-Auditor/releases/tag/v1.4.3)

```text
Asset: Void-Auditor-v1.4.3.apk
sha256: 87aa672200646d29ce92a0b7ecbd21866025822bd1b91054192f9607176fee6f
```

Or install from a PC:

```bash
adb install -r Void-Auditor-v1.4.3.apk
```

---

## Build from source

```bash
git clone https://github.com/M0NDsuChTiG/Void-Auditor.git
cd Void-Auditor/android
# JDK 21 required (CI uses 21)
./gradlew :app:assembleDebug
```

APK output:

```text
android/app/build/outputs/apk/debug/Void-Auditor-v<version>.apk
```

> ⚠️ **Требование сборки:** нужен **JDK 21 (Java 21)** — CI и локальная сборка рассчитаны на него. Проверьте `java -version` и при необходимости укажите JDK 21 явно:
>
> ```bash
> JAVA_HOME=/путь/к/jdk-21 ./gradlew assembleDebug
> ```

---

## Quick start

1. Install **Shizuku** from GitHub / Play and **start** it (pair Wireless Debugging or USB).  
2. Install VOID Auditor and open it → grant **Shizuku** permission when prompted.  
3. Optional: enter a **Gemini API key** in the AI screen (stored encrypted).  
4. Run **Security Dashboard** or **CACHE** scan → use dry-run before purge.  
5. **CONN** → check WIFI_ADB status; **NET_SCAN** → scan the current subnet.

---

## Release notes (recent)

### v1.4.3

- **Permission Audit (PERMS)** — new module: audit installed packages by **actually granted** dangerous permissions, parsed from `dumpsys package` (One UI `requested`/`runtime` sections and AOSP inline format). Registry of 33 dangerous permissions grouped by sensitivity with weighted risk (SMS / mic / location / call-log = 3, contacts / camera / phone = 2, storage / calendar = 1, special app-ops = 2–3). Third-party scope by default; **ALL** mode marks system packages `[SYS]` and warns that high scores are expected there. Risk filters (LOW / MEDIUM / HIGH / CRITICAL), expandable app cards, live scan progress, report export to `Downloads/VOID_Auditor_Reports/` via MediaStore.
- **Parser unit tests** — 7 tests covering both dumpsys formats, risk weighting and the SYS flag.
- **Docs organization** — technical documentation (`README_TECH.md`) and field reports (`PERMISSION_AUDIT_REPORT.md`, `VOID_Auditor_Report_NET_SCAN.md`) moved to [`docs/`](docs/README.md) with automated CI link validation (`docs-links.yml`).
- **Dead Capacitor shell removed** — `android/settings.gradle` no longer includes the unused `:capacitor-android` / `:capacitor-haptics` / `:capacitor-cordova-android-plugins` projects; unqualified Gradle tasks (`testDebugUnitTest`, `lint`) no longer fail with `invalid source release: 21` (latent CI breaker).
- **Docs** — [`docs/PERMISSION_AUDIT_REPORT.md`](docs/PERMISSION_AUDIT_REPORT.md): full minimization campaign report (7 apps, before/after scores, evidence-based revocations).

### v1.4.2

- **Port scan reliability (NET_SCAN)**: fixed SYN-flood drops — 256 concurrent connects overwhelmed router SYN queues and silently lost real open ports (measured: 31% success at 256, ~92% at 32). Now 32 concurrent connects with 150ms timeouts, up to 4 retry passes for lost SYNs, and adaptive pacing between chunks so routers can drain their queues. Verified on-device: a full 65535-port scan now finds open ports the old scanner missed. `COMMON_PORTS` extended with **53 (DNS)** and **445 (SMB)**.
- **Command logging everywhere**: `ShizukuExecutor.init()` now runs app-wide, so `[CMD]` / `[RESULT]` lines appear in the console on every screen (previously only after opening the AI screen).
- **AI models fixed**: the Gemini 2.x line was shut down by Google (2.0 removed June 1, 2026; 2.5 “no longer available to new users” → `API_ERR_404`). Switched to **`gemini-3-flash-preview`** / **`gemini-3.1-flash-lite-preview`** (verified HTTP 200 with the API key), with automatic migration of the saved model preference.
- **Android accessibility (TalkBack)**: accessible names for all icon-only buttons (Send, Refresh, Save, Export, Back up, Info), contrast fixes for inactive nav tabs, log `[COPY]`/`[CLEAR]` and path chips (WCAG 1.4.3), console resize `+`/`−` buttons as an alternative to drag (WCAG 2.5.7).

### v1.4.1

- **AI Governance Layer (Intent Proposal)** — Gemini acts as advisor only: `PROPOSAL_JSON` (whitelisted `capabilityId`) → sanitize/parse/validate → `AI_PROPOSAL_REVIEW` dialog → PolicyEngine → CapabilityExecutor → AuditLogger (`ActorType.AI`). Destructive ops stay out of the whitelist by design.
- **Gemini API key** stored in EncryptedSharedPreferences (AES-256-GCM) with automatic migration from legacy plain prefs.
- **Command injection closed** in the network scanner — strict IPv4 validation before shell interpolation (`generateTargets` / `scanHosts`).
- **Gemini 429 UX** — one user bubble per request, single automatic retry without recursion, countdown only via `LaunchedEffect`.
- **Banking deep scan** — dangerous-permission detection now works (regex instead of literal search).
- **Report export** via MediaStore (scoped storage, API 29+) with legacy fallback.
- **Build/CI** — moved to JDK 21, jetifier disabled (fixes lint), dead Capacitor layout removed (fixes lint `MissingClass`), unit tests run in CI.

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

## Documentation

📚 **Index:** [docs/README.md](docs/README.md) — all technical docs and reports in one place

| Document | Description |
|----------|-------------|
| [README_TECH.md](docs/README_TECH.md) | Technical documentation — architecture, capabilities, PolicyEngine, Shizuku layer, AI governance |
| [PERMISSION_AUDIT_REPORT.md](docs/PERMISSION_AUDIT_REPORT.md) | Permission minimization campaign report — 7 third-party apps, before/after risk scores, evidence-based revocations via `pm revoke` / `appops` |
| [VOID_Auditor_Report_NET_SCAN.md](docs/VOID_Auditor_Report_NET_SCAN.md) | NET_SCAN field report — port-scan reliability measurements (parallelism 1–256, SYN-queue loss), service banners, full 65535-port scan results |

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
- Telegram: [EthicalHackingCS](https://t.me/EthicalHackingCS)

**Stack:** Kotlin · Jetpack Compose · Material 3 · Shizuku API 13 · Gemini (optional) · minSdk 26 · targetSdk 35
