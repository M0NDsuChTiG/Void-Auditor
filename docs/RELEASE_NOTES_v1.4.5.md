# VOID Auditor v1.4.5

> **Publication status (2026-09-22).** A git tag `v1.4.5` exists at commit `e69a897`, but **no
> GitHub Release and no APK asset are published yet**, so `releases/latest` still resolves to
> v1.4.3 and there is no downloadable `Void-Auditor-v1.4.5.apk`. The network-discovery change
> documented below is currently **uncommitted** (working tree) and is therefore **not contained in
> tag `e69a897`**. It was built and validated from a **locally built** `v1.4.5` APK.

## Summary

v1.4.5 is a **reliability and execution-architecture release**. It fixes a cache scan/purge
regression, an API-26 build break, and a subnet-discovery failure in NET_SCAN. It introduces **no
major new user-facing feature**.

## Problem encountered

On an unrooted device (Shizuku), the NET_SCAN discovery phase returned **0 alive hosts** and the
port-scan phase never started. The affected runs showed a fast-fail signature in the log
(`Exit: -1 | ~52 ms`).

## Root cause

- **FACT:** discovery previously issued a **single** batched `Capability.PingSweep` (xargs over the
  whole /24) and derived alive hosts from typed evidence (`PingSweepEvidence`). A `null` cast
  produced an empty alive list. The current code no longer creates any `PingSweep` call at runtime.
- **INFERENCE:** the batched xargs pipeline's child `sh` could not run `ping` under the Shizuku
  shell context, so every target exited non-zero within milliseconds; with no usable alive set, the
  scan stopped at discovery.
- **UNKNOWN:** the exact stderr / SELinux context that produced the fast-fail was not captured, so
  the mechanism is inferred from the exit-code/timing signature, not proven.

## Solution

Discovery now issues **one `Capability.PingIp` per host** instead of a single xargs batch. Each call
runs through the same capability path (`NetworkScanner → CapabilityExecutor → ShizukuExecutor`), and
a host is considered alive when its raw command succeeds (`commandResult.isSuccessful`). Typed
evidence is **not** used for this decision, and `PingIpParser` is intentionally not registered.

## Architecture changes

- New capability `Capability.PingIp(ip)`; `CommandMapper` maps it to
  `ping -c 1 -W 1 "<ip>" >/dev/null 2>&1 && echo "<ip>"`, with IPv4 validation before interpolation.
- xargs `PingSweep` is no longer invoked by the scanner.
- Execution boundary is preserved: the production probe routes one `Capability.PingIp` per host
  through `NetworkScanner → CapabilityExecutor → ShizukuExecutor` (`ActorType.SCRIPT`); the executor
  call is injectable for unit tests, but no production caller bypasses the boundary.

## Network Scanner

- **Concurrency:** `Semaphore(32)` gate under `withContext(Dispatchers.IO)`.
- **Timeout:** whole phase wrapped in `withTimeout(60_000)`.
- **Cancellation:** timeout / exception degrades to `emptyList()` instead of aborting.
- **Failure isolation:** each host runs in `try/catch`; an unreachable host is skipped.
- **Aggregation:** alive IPs collected in a synchronized list, then enriched with MAC
  (`/proc/net/arp`) and hostname.

## Typed Evidence

- This release does **not** complete a broad typed-evidence migration. The discovery fix deliberately
  bypasses typed evidence and uses the raw command result.
- Note: the `PingSweepParser` **is** registered (`EvidenceParserRegistry.kt`) — the earlier absence
  referred to in the scanner comment is a historical state, not the current one.

## Testing

- New `NetworkScannerDiscoveryTest` — unit-tests the per-host discovery seam (`discoverAliveIps` / `pingHostAlive`): the alive rule (`success && exitCode == 0`), failure isolation, bounded concurrency (suspension-level `Semaphore(32)`), phase-timeout degradation, cancellation propagation, and the IPv4 gate.
- Existing `NetworkScannerTest` — covers `isValidIpv4` / `generateTargets` (plus one target-filter case).
- New `NetworkIdentityTest` and `NetworkProfileDetectorTest` — cover subnet/scope derivation.
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL.

## Real-device validation

- **Scope:** one lab device, locally built `v1.4.5` APK (versionName `1.4.5`, versionCode `9`).
- **Discovery:** 254 targets × 2 phases = **508 pings**; **0** fast-fail (`Exit: -1 | ≤100 ms`)
  signatures in the completed run.
- **Completion:** final UI showed `Found 5 host(s)`, the `Scanning:` progress block was gone, CANCEL
  was replaced by a Refresh action, no error card appeared, and worker threads were idle
  (`isScanning=false`).
- **Results (sanitized):** five host cards rendered. Observed open-port / service shapes were:
  a single high port on one host; `53` and `64374` on the gateway (`53: DNS`,
  `64374: SSH-2.0-dropbear` banners); one host with a single high port; `5555` on one host
  (`5555: ADB` banner); and one host that was alive via ping but had **0 open ports**.
  Exact IPs/MACs are retained with the raw local evidence and are intentionally not published here.

## Security observations

- **Observed exposure only — not a compromise verdict.** ADB exposure was observed on TCP/5555 for
  one scanned host. No host is labeled malicious and no compromise is claimed.
- Service labels (`DNS`, `SSH-2.0-dropbear`, `ADB`) are recorded only where the UI actually rendered
  a banner; other ports are listed as numbers only.

## New features

None. v1.4.5 does not introduce a major new user-facing feature. The release primarily improves
scanner reliability, execution architecture, and runtime validation.

## Improvements

- Deterministic scan completion state (progress block cleared, Refresh restored).
- Isolated per-host failures so one unreachable host cannot block the scan.
- Honest failure behavior retained across WIFI_ADB and cache modules (no fake success).

## Known limitations

- No root: internal app caches remain partially invisible (`EXTERNAL_CACHE_ONLY`).
- A full 65 535-port pass with up to 4 retry passes takes tens of minutes per host.
- Validation timings come from a single lab device; other vendors / SELinux configurations are not
  covered.

## Upgrade notes

- No data migration is required.
- Cache and WIFI_ADB behavior is unchanged; the NET_SCAN discovery path is now per-host.
- Because no v1.4.5 APK asset is published, install v1.4.3 or build from source until the release is
  published.

## Verification evidence

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL.
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- `git diff --check` — clean.
- Device validation captured in-app UI dumps and screenshots (retained locally; not published with
  device identifiers).
