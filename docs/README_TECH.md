# VOID Auditor — Technical Documentation

> English technical overview · aligned with **v1.4.5**
> For the project landing README see [README.md](../README.md). Russian short doc: [README_RU.md](../README_RU.md).

---

## 1. Project overview

**VOID Auditor** is a standalone Android application for **security audit**, device management, and lightweight forensics.  
It uses the **Shizuku API** and does **not** require root or a permanent PC connection.

| Item | Value |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Privileges | Shizuku API 13 |
| AI (optional) | Google Gemini (Flash / Flash Lite / 1.5 Flash) — **advisory only** |
| Build | Gradle + Android Gradle Plugin |
| minSdk / targetSdk | 26 / 35 |
| Package | `com.kuzyamond.voidauditor` |

---

## 2. Architecture

```text
UI (Compose screens)
  → Capability / intent layer (where migrated)
  → PolicyEngine (allow / deny / require confirmation)
  → ShizukuExecutor / ShizukuManager (shell, timeouts, CommandResult)
  → AuditLogger / GlobalLog / TRACE UI
```

**AI path (governance):**

```text
Sanitized context
  → Gemini (analysis / proposals only)
  → IntentProposal (non-executable)
  → Validator + PolicyEngine
  → Human confirmation
  → Capability execution via Shizuku
```

AI never runs arbitrary shell strings directly.

---

## 3. Feature modules

### A. Security Dashboard / Device Audit

- Risk score **0–100**
- Checks related to SELinux, ADB Wi‑Fi, Accessibility, overlays, package posture
- Banking / finance-oriented signals where configured
- Fix-oriented command suggestions (user must confirm destructive actions)

### B. Cache Cleaner

- Discovers cache-like directories under allowed roots (primarily **external** without root)
- Modes: QUICK / FULL / DEEP (depth and roots differ)
- **Dry-run** before destructive purge
- **3-factor confirmation** (exact phrase `PURGE`)
- Selective package/path selection; TOP size tracks selection
- Banner **`EXTERNAL_CACHE_ONLY`** when internal `/data/data` is not fully visible without root
- Optional **SYSTEM_TRIM** (`pm trim-caches`) path where applicable
- Scan state retained across tab switches (`CacheScanStorage`)

### C. NET_SCAN

- Resolves current subnet from device routing / interface context
- **Per-host discovery** (`Capability.PingIp`, one host per call): bounded concurrency (`Semaphore(32)`, `Dispatchers.IO`), `withTimeout(60_000)`, per-host failure isolation — replaces the previous single batched `PingSweep` (xargs) path
- Alive-host decision uses the raw `CommandResult` (`isSuccessful`); typed evidence is not required (`PingIpParser` is not registered)
- Live hosts enriched with **MAC** via `cat /proc/net/arp` through Shizuku (avoids app SELinux deny on `/proc/net/arp`)
- Optional TCP probes on common ports (e.g. 22, 80, 443, 5555, …) for live hosts only

### D. WIFI_ADB (Connection)

- **STATUS** from `getprop service.adb.tcp.port` only (`isWifiAdbEnabled`)
- **[CHECK]** re-reads status honestly
- **ENABLE** flow: `setprop` → `stop`/`start adbd` → `delay(800)` → **verify** with `getprop`
  - Failure modes: `SETPROP_FAILED`, `VERIFY_FAILED` (no fake “ADB over WiFi enabled”)
- On many non-root devices SELinux blocks `setprop`; UI must show failure, not success

### E. Apps manager

- List / filter installed packages
- Force-stop, disable/enable, clear data (policy + confirmation for high risk)
- Activity launcher by component where allowed

### F. AI Assistant (Gemini)

- Device audit prompts and log/IOC-oriented analysis
- Risk language in replies (LOW / MEDIUM / HIGH / CRITICAL) as **advisory** text
- API key in **EncryptedSharedPreferences** (AES-256-GCM)
- Russian or English user prompts supported by the model; governance stays in-app

### G. File manager (FS)

- Browse and inspect paths reachable under Shizuku policy
- Size / listing helpers (e.g. `du` where permitted)
- Not a substitute for full root filesystem access

### H. Scripts & Terminal

- Preset scripts (audit, lockdown, cleanup, network-oriented)
- Interactive shell through Shizuku with logging

### I. Backup

- Pull APKs of installed packages where permitted
- Operation status tracking

### J. Audit Trail (TRACE)

- Structured events: actor, command/capability, risk, policy decision, exit code, duration
- Supports forensic review of what the app actually ran

---

## 4. Security protocols

- Destructive operations require **explicit confirmation** (and phrase match for purge)
- Privilege boundary is **user-authorized Shizuku**, not hidden root
- Every shell path should be attributable in logs / TRACE
- Gemini keys and sensitive prefs: encrypted at rest
- Audit logs: **app-private** storage (not world-readable external dumps for secrets)

---

## 5. Platform limits (document in UI and docs)

| Limit | Behavior |
|-------|----------|
| No root | Internal `/data/data/*/cache` often invisible → `EXTERNAL_CACHE_ONLY` |
| SELinux | `setprop service.adb.tcp.port` may fail → honest `SETPROP_FAILED` / `VERIFY_FAILED` |
| Wireless ADB | Prefer platform **Wireless Debugging** / pairing when shell cannot enable tcpip |
| AI | Advisory only; execution only after policy + human gate |

---

## 6. Build and deploy

```bash
git clone https://github.com/M0NDsuChTiG/Void-Auditor.git
cd Void-Auditor/android
./gradlew :app:assembleDebug
```

Typical output:

```text
android/app/build/outputs/apk/debug/Void-Auditor-v1.4.5.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/Void-Auditor-v1.4.5.apk
```

**Requirements on device:** Android 8.0+, Shizuku v13+ started, permission granted to VOID Auditor.

---

## 7. Source layout (simplified)

```text
android/app/src/main/java/com/kuzyamond/voidauditor/
├── core/           # ShizukuExecutor, policy, audit, scan state, …
├── cache/          # PathSanitizer, CacheScanner, CacheCleaner, UI
├── network/        # NetworkScanner, NetworkAdb, models, UI
├── security/       # Encrypted prefs / biometric helpers (where present)
├── *Screen.kt      # Compose screens (Dashboard, AI, Apps, Connect, …)
└── MainActivity.kt
```

Exact file set evolves; treat this as a map, not a frozen tree.

---

### Testing

- New `NetworkScannerDiscoveryTest` unit-tests the discovery seam (`discoverAliveIps` / `pingHostAlive`):
  the alive rule (`success && exitCode == 0`), failure isolation, bounded concurrency,
  phase-timeout degradation, cancellation propagation, and the IPv4 gate.

## 8. Releases

- GitHub Releases: https://github.com/M0NDsuChTiG/Void-Auditor/releases  
- **Latest published release:** **v1.4.5** (APK asset published, sha256 in README).
- Always prefer the **Assets** APK + published **sha256** over random mirrors  

---

## 9. v1.4.5 — Network Discovery Reliability

Scope: the subnet-discovery phase of NET_SCAN (`network/NetworkScanner.kt`). Released in tag
`v1.4.5` (commit `1aa11fc`). Device proof was obtained from the released `v1.4.5` APK.

### Previous behavior

- Discovery called `CapabilityExecutor.execute(..., Capability.PingSweep(targets))` — a **single** xargs
  batch over the whole /24 — under `withTimeout(45_000)`.
- Alive hosts were taken from typed evidence (`... as? PingSweepEvidence`); a `null` cast produced an empty alive list.

### Observed failure

- Log entries with the xargs fast-fail signature `Exit: -1 | ~52 ms` (28 occurrences, timestamps
  00:28–13:40:42) — all **before** the fixed APK install (`lastUpdateTime 13:40:52`).
- In the affected runs discovery returned 0 alive hosts and the port phase never started.

### Root cause

- **FACT:** the current code creates no `PingSweep` call at runtime; the scan uses per-host `PingIp`.
  The historical batch failed fast (`Exit: -1`, ~52 ms).
- **INFERENCE:** the batched xargs pipeline's child `sh` could not run `ping` under the Shizuku shell
  context, so every target exited non-zero within milliseconds; with no usable alive set the scan
  stopped at discovery.
- **UNKNOWN:** the exact stderr / SELinux context that produced the failure was not captured, so the
  mechanism is inferred from the exit-code/timing signature, not proven.

### Implementation

- `Capability.PingIp(ip)` added; `CommandMapper` maps it to `ping -c 1 -W 1 "<ip>" >/dev/null 2>&1 && echo "<ip>"`.
- `NetworkScanner.scanHosts` launches one coroutine per validated target.

### Concurrency model

- Suspension-level `Semaphore(32)` gate; `withContext(Dispatchers.IO)`; results collected in a synchronized list.

### Cancellation

- The whole phase is wrapped in `withTimeout(60_000)`; an exception/timeout degrades to `emptyList()`.

### Failure isolation

- Each host runs in `try/catch`; a failed or unreachable host is skipped without aborting the scan.

### Evidence handling

- The alive decision uses `result.commandResult.isSuccessful` (raw result); typed evidence is not used
  and `PingIpParser` is intentionally not registered.

### Runtime validation

- 254 targets × 2 phases = 508 pings; **0** fast-fail (`Exit: -1 | ≤100 ms`).
- **Result:** `Found 5 host(s)`; the progress block was removed and Refresh restored in the final UI;
  all 5 host cards rendered; process idle (`isScanning=false`).

### Known limitations

- Results are only as complete as the network allows (SYN filtering, probe loss).
- A full 65 535-port pass with up to 4 retries takes tens of minutes per host.
- Timings were measured on one lab device; other vendors / SELinux configurations are not covered.

---

## 10. Related links

- [README.md](../README.md) — English project README  
- [README_RU.md](../README_RU.md) — Russian technical notes (may lag; this file tracks v1.4.5)
- [SECURITY.md](../SECURITY.md) — vulnerability reporting  
- [Landing](https://m0ndsuchtig.github.io/Void-Auditor/) — Pages site (EN default)  
- Screenshots: [`screenshots/`](../screenshots/)  
- Telegram: [EthicalHackingCS](https://t.me/EthicalHackingCS)

---

## 11. License

MIT — see [LICENSE](../LICENSE).

**Authorized use only.** Audit devices you own or have explicit permission to test.
