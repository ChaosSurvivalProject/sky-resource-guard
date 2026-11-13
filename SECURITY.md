# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 0.1.0 (P0, experimental) | security fixes only |

## Reporting a vulnerability

Please report privately through GitHub's **Security Advisories → Report a
vulnerability** on this repository. Do not open a public issue, do not post
exploit details in discussions, and do not include player data in the report.

Include: affected version, reproduction steps, impact (especially whether it
can bypass permissions, execute an unregistered action, or leak player data),
and any suggested mitigation. You should receive an acknowledgement within 7
days; if not, please follow up privately.

## What SKY considers a security issue

- Bypassing `sky.plan.approve` / `sky.action.*` permissions.
- Any path that lets player chat or AI advice become an administrator command.
- Unredacted UUID, IP, chat text, coordinates, item NBT, tokens or internal
  hostnames in logs, diagnostic bundles or AI requests.
- Automatic execution of economy, inventory, teleport, ban, world deletion or
  other irreversible actions.
- Hash-chain gaps in the audit log, or an action without a lease/rollback.
- Leaking an API key into a commit, artefact or error report.

## Design constraints (non-exploitable by design)

- AI is advisory only: `allow-execute = false` by default.
- Unknown plugins default to `OBSERVE_ONLY`; only L0/L1 evidence can trigger
  automatic restriction.
- Secrets are referenced (`secret:...`), never stored in config or logged.
- `LOCKOUT` stops further automatic action and keeps a human recovery path.
