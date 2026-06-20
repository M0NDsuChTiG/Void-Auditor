# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

We take the security of VOID Auditor seriously. If you believe you have found a security vulnerability, please **do not** open a public issue.

### Responsible Disclosure Process

1. **Report via Telegram**: [@kuzyamond](https://t.me/kuzyamond)
2. Include a detailed description of the vulnerability, steps to reproduce, and affected versions.
3. You will receive an acknowledgment within 48 hours.
4. We will work on a fix and release timeline, keeping you informed.

### What to Expect

- Acknowledgment of receipt within 2 business days.
- An initial assessment and expected fix timeline within 5 business days.
- Credit in release notes and this file (if desired).

## Security Features

- **Encrypted API Key Storage** — AES-256-GCM via EncryptedSharedPreferences
- **Biometric Authentication** — BiometricPrompt with PIN/pattern fallback
- **Secure Audit Logs** — App-private directory (`filesDir/audit_logs/`), no external storage
- **CI/CD Pipeline** — GitHub Actions with lint and build verification
- **Min SDK 26** — Modern cryptographic APIs and security patches

## Data Handling

- API keys are encrypted at rest using Android Keystore-backed encryption.
- Audit logs are stored in the app's private sandbox and deleted on uninstall.
- No telemetry, analytics, or network requests are made without explicit user action.
- The app does not collect or transmit any personal data.
