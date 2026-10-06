# Documentation index

| Document | Purpose | Status |
|---|---|---|
| `ARCHITECTURE.md` | Components, data flows, security, concurrency, scalability | Current (reviewed 2026-10-05) |
| `DECISIONS.md` | Architecture decision records (ADR-001 to ADR-019) | Current |
| `API.md` | REST endpoint reference | Current |
| `ERD.md` | Database schema reference | Current |
| `ENVIRONMENT.md` | Every environment variable | Current |
| `ONBOARDING.md` | Developer setup and conventions | Current |
| `OPERATIONS.md` | Monitoring, backups, recovery, runbooks | Current |
| `DEMO.md` | Interview demo mode: seeded logins, showcase data, local one-command demo, walkthrough | Current |
| `changes/` | Dated write-ups of significant changes | Current, append-only |
| `V2_DESIGN.md`, `V2_CHANGELOG.md` | V2 design proposal and build log | Historical |

## Deployment docs: which one to use

| Document | Use it for |
|---|---|
| `DEPLOYMENT_RUNBOOK.md` | **Canonical.** Full reproducible vps01 cutover and continuous deployment |
| `DEPLOYMENT.md` | Short reference (TLS, CI/CD secrets); defers to the runbook |
| `../DEPLOYMENT_STEPS.md` | One-page checklist for a routine deploy |
| `DEPLOY_VPS.md` | Legacy bootstrap notes, historical only |

When deployment changes, update the runbook first, then the shorter docs if they mention
the changed step.
