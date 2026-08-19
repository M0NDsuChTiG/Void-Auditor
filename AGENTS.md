# VOID Auditor — Agent Engineering Contract

## 1. Project Identity

Project: VOID Auditor

Repository:
https://github.com/M0NDsuChTiG/Void-Auditor

Package:
`com.kuzyamond.voidauditor`

Current repository baseline:
`main`

Current documented application version:
`v1.4.3`

VOID Auditor is an Android security-audit, device-management and lightweight-forensics application using the Shizuku API.

The application is designed to operate without root and without a permanent PC connection.

---

## 2. Mission

VOID Auditor exists to provide controlled, auditable and policy-governed security operations on Android devices.

Primary goals:

- security auditing;
- device posture inspection;
- package/application management;
- filesystem inspection;
- network discovery;
- cache analysis and controlled cleanup;
- wireless ADB status inspection;
- forensic-oriented audit logging;
- controlled shell execution through Shizuku;
- AI-assisted analysis and recommendations.

Security and auditability take priority over convenience.

---

## 3. Current Version / Baseline

The repository is the source of truth.

The current documented baseline is `v1.4.3`.

Do not assume that historical project descriptions, previous conversations or old documentation represent the current implementation.

Before modifying architecture or security-sensitive code:

1. inspect the current repository;
2. inspect relevant source files;
3. inspect tests;
4. inspect current documentation;
5. determine the actual implementation state.

Never implement against an assumed historical architecture.

---

## 4. Technology Stack

Primary stack:

- Kotlin
- Android
- Jetpack Compose
- Material 3
- Gradle
- Android Gradle Plugin
- Java 17 target
- Android compile SDK 35
- minimum SDK 26
- target SDK 35
- Shizuku API 13.x
- AndroidX Security
- AndroidX Biometric
- JUnit
- MockK
- Kotlin Coroutines

Application package:

`com.kuzyamond.voidauditor`

The repository may use JDK 21 as a build/runtime environment while targeting JVM 17.

Do not change JVM target versions without verifying the complete Gradle/toolchain configuration.

---

## 5. Actual Repository Architecture

The current architecture contains the following major layers.

### UI

Compose-based screens including:

- Dashboard
- Audit
- Apps
- Connect
- Files
- Backup
- Scripts
- Terminal
- AI Assistant
- Permission Audit
- Activity Launcher

### Core

The core security/execution layer currently includes:

- `Capability`
- `CapabilityExecutor`
- `PolicyEngine`
- `PolicyDecision`
- `ConfirmationManager`
- `ShizukuExecutor`
- `AuditLogger`
- `AuditEvent`
- `ScanStateStore`

### Feature domains

Current domains include:

- cache
- network
- security
- AI governance
- application management
- audit
- backup
- filesystem
- terminal/scripts
- connectivity

Treat the exact repository tree as authoritative.

---

## 6. Security Architecture

The fundamental security boundary is:

UI / Intent
    ↓
Capability
    ↓
PolicyEngine
    ↓
Confirmation
    ↓
CapabilityExecutor
    ↓
ShizukuExecutor
    ↓
Android shell / system operation

Operations must not bypass the policy layer.

Operations must not bypass the capability model.

Operations must not directly execute arbitrary AI-generated shell commands.

---

## 7. Capability Model

Capabilities represent controlled operations.

Examples include:

- reading system properties;
- shell operations;
- querying packages;
- dumping services;
- modifying settings;
- installing packages;
- uninstalling packages;
- force stopping packages;
- clearing application data;
- reading files;
- writing files;
- network actions;
- sensitive-data operations.

Every capability must have an explicit security meaning and risk classification.

Do not introduce generic unrestricted execution when a typed capability can represent the operation.

---

## 8. Policy Engine

`PolicyEngine` is a mandatory security gate.

Current risk thresholds:

- `< 50` → Allowed
- `50–79` → Require confirmation
- `>= 80` → Require double confirmation

Severity mapping:

- `< 40` → LOW
- `40–69` → MEDIUM
- `70–89` → HIGH
- `>= 90` → CRITICAL

Do not silently weaken these controls.

Any modification to risk thresholds requires explicit architectural justification and tests.

---

## 9. Execution Boundary

`CapabilityExecutor` is responsible for enforcing policy before execution.

`ShizukuExecutor` is responsible for controlled command execution through Shizuku.

The execution chain must remain auditable.

Execution results must preserve:

- success/failure;
- output;
- error;
- exit code;
- execution duration.

Do not allow AI or UI code to directly invoke unrestricted shell execution when a capability path exists.

---

## 10. AI Governance

AI is advisory.

AI does not have direct execution authority.

The intended model is:

Sanitized context
    ↓
AI analysis
    ↓
IntentProposal
    ↓
validation
    ↓
PolicyEngine
    ↓
human approval
    ↓
Capability execution
    ↓
Audit

`IntentProposal` is non-executable.

AI-generated proposals must not become shell commands automatically.

AI confidence must not replace policy risk evaluation.

Risk classification belongs to the governance/policy layer, not to the AI model.

---

## 11. Evidence / Audit Model

Security-sensitive operations must remain attributable.

Audit information should preserve, where applicable:

- actor;
- capability;
- risk level;
- policy decision;
- target;
- exit code;
- duration;
- relevant error/details;
- evidence/context.

Audit logs are security telemetry, not ordinary debug output.

Do not expose sensitive audit information unnecessarily.

---

## 12. Network / Cache / Permission Modules

Current repository functionality includes security auditing, network scanning, cache inspection/cleanup, wireless ADB status handling, application management, filesystem inspection, backup and permission auditing.

Important constraints:

### Cache

Without root, internal application cache paths may not be fully accessible.

The application must honestly communicate limitations.

Do not claim complete cache cleaning when the operation is limited to externally accessible paths.

### Network

Network discovery must respect Android/Shizuku limitations.

Do not assume unrestricted raw socket/root capabilities.

### Wireless ADB

Do not report success unless the state has actually been verified.

Failure must be represented honestly.

### Permissions

Permission/security findings must distinguish:

- observed state;
- inferred risk;
- recommended action.

Do not present inference as fact.

---

## 13. Android / Shizuku Constraints

VOID Auditor does not assume root access.

Shizuku provides the privileged execution boundary.

SELinux restrictions may prevent certain operations.

Some Android system properties or settings may be inaccessible or immutable.

Platform restrictions must be represented honestly in both code and UI.

Never fake successful execution.

---

## 14. Build Requirements

Primary Android project:

`android/`

```markdown
Build command:

```bash
cd android
./gradlew :app:assembleDebug

## 15. Testing Rules

Every security-sensitive architectural change should have tests where practical.

Priority testing areas:

PolicyEngine;
CapabilityExecutor;
capability risk classification;
confirmation logic;
command execution results;
AI proposal validation;
audit events;
sanitization;
destructive operations;
permission boundaries.

Do not remove tests simply to make a build pass.

A passing compilation is not equivalent to a security-valid implementation.
Build command:

---

## 16. Security Rules

Never:

bypass PolicyEngine;
bypass CapabilityExecutor;
give AI direct shell execution;
silently escalate privileges;
claim an operation succeeded without verification;
weaken confirmation requirements without justification;
expose secrets in logs;
store API keys in source code;
introduce unrestricted command injection paths;
treat user-controlled strings as trusted shell commands.

Security-sensitive changes require explicit reasoning about:

trust boundary;
attacker-controlled input;
privilege boundary;
validation;
authorization;
logging;
failure behavior.

---

## 17. Change Rules

Before changing an existing security component:

inspect current implementation;
identify callers;
identify tests;
identify documentation;
determine compatibility impact;
make the smallest safe change;
run relevant tests;
inspect the resulting diff.

Do not rewrite stable components without a concrete reason.

Prefer incremental architectural evolution.

---

## 18. Git Rules

Do not make destructive Git operations without explicit user authorization.

Do not:

force-push;
reset user work;
delete branches;
rewrite history;
discard uncommitted changes.

Before substantial changes inspect:

git status
git branch --show-current
git log -1 --oneline
git diff

Keep commits logically scoped.

---

## 19. USF Relationship

USF is the architectural/security framework being developed around VOID Auditor.

Current implementation concepts related to USF already exist in:

Capability;
PolicyEngine;
CapabilityExecutor;
AuditLogger;
AuditEvent;
AI governance;
IntentProposal.

However, do not claim that the complete USF architecture is implemented unless the corresponding implementation exists in the repository.

USF architecture must evolve from the actual codebase rather than being imposed as an unrelated abstraction.

---

## 20. UOG Status

UOG means USF Object Graph.

Do not assume UOG is implemented.

UOG is an architectural direction unless concrete repository implementation proves otherwise.

When introducing UOG:

define object identity;
define relationships;
define ownership;
define lifecycle;
define trust boundaries;
define serialization;
define audit semantics;
define compatibility with existing Capability and Policy systems.

Do not create duplicate abstractions where existing domain objects already provide the required semantics.

---

## 21. Forbidden Assumptions

The agent must not assume:

root access;
unrestricted Android shell access;
unrestricted filesystem access;
successful setprop;
successful wireless ADB activation;
AI authorization to execute actions;
complete visibility of /data/data;
that old documentation matches current code;
that previous conversation architecture is implemented;
that a documented feature necessarily exists in source code;
that a source-code feature is production-ready merely because it compiles.

Repository evidence takes precedence over assumptions.

---

## 22. Source-of-Truth Hierarchy

When information conflicts, use this priority:

Current source code
Current tests
Current build configuration
Current security configuration
Current technical documentation
README
Historical documentation
Previous conversation context

Never use historical conversation context to override the current repository.

---

## 23. Agent Workflow

For every non-trivial task:

Phase 1 — Inspect

Inspect the relevant repository files first.

Phase 2 — Understand

Determine:

current architecture;
dependencies;
security boundaries;
callers;
tests;
failure modes.
Phase 3 — Plan

State the intended change and affected components.

Phase 4 — Implement

Make the smallest coherent implementation.

Phase 5 — Verify

Run appropriate tests/build checks.

Phase 6 — Review

Inspect:

git diff
git status

Check for:

security regressions;
unintended behavior;
dead code;
API changes;
missing tests;
documentation drift.
Phase 7 — Report

Report:

what changed;
why;
files affected;
tests performed;
remaining limitations;
security implications.

---

## Final Rule

VOID Auditor is a security-sensitive project.

Correctness, explicit authorization, policy enforcement, auditability and honest failure reporting are more important than convenience or apparent feature completeness.

When uncertain:

STOP → INSPECT → VERIFY → PLAN → CHANGE → TEST → REVIEW.

Never guess about security architecture.

---
