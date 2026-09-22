# VOID Auditor — Документация

Центральный каталог технической документации и отчётов проекта.

> Основной README: [английский](../README.md) · [русский](../README_RU.md)

---

## Документы

| Документ | Описание |
|----------|-----------|
| [README_TECH.md](README_TECH.md) | **Техническая документация** — архитектура приложения, слой capabilities, PolicyEngine, Shizuku-обёртка, AI governance (v1.4.5) |
| [PERMISSION_AUDIT_REPORT.md](PERMISSION_AUDIT_REPORT.md) | **Отчёт по PERMS** — кампания минимизации разрешений: 7 third-party приложений, баллы риска до/после, отзывы по фактам использования (`pm revoke` / `appops`) |
| [VOID_Auditor_Report_NET_SCAN.md](VOID_Auditor_Report_NET_SCAN.md) | **Отчёт по NET_SCAN** — замеры надёжности порт-скана (параллельность 1–256, потери SYN-очереди), баннеры сервисов, полный скан 65535 портов |
| [RELEASE_NOTES_v1.4.5.md](RELEASE_NOTES_v1.4.5.md) | **Release notes v1.4.5** — фикс обнаружения NET_SCAN (per-host `PingIp`), факты/предположения/неизвестное, проверка на устройстве, статус публикации релиза |

## Прочее в папке

- [`index.html`](index.html) — лендинг проекта (GitHub Pages)

---

**Связанные документы (вне `docs/`):** [SECURITY.md](../SECURITY.md) — политика безопасности · [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) — чек-лист релиза · [LICENSE](../LICENSE) — MIT
