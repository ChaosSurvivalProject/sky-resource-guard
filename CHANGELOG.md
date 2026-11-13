# Changelog

All notable changes to this project are documented here. This project follows
[Semantic Versioning](https://semver.org/) for the public `sky-api` surface and
[Conventional Commits](https://www.conventionalcommits.org/) for commit messages.

## [0.1.0] - 2026-11-13 - P0 / experimental

Status: **experimental, not installable on a server yet.** This is the first
verifiable increment (P0 "trustworthy observation") from `docs/architecture.md`.
It contains pure-Java core logic only; no Bukkit/Paper/Purpur artifact is linked.

### Added

- Gradle Java 21 multi-module skeleton with a stable `dev.skyguard.api` surface.
- `sky-api`: `ResourceParticipant` SPI, `ExternalPluginAdapter` SPI, DTOs,
  capability/phase/urgency/evidence enums, error codes, permission constants,
  and a versioned `advice-proposal` JSON Schema.
- `sky-core`: metrics ring buffer, MSPT histogram, evidence correlator,
  hysteresis guard state machine (including `LOCKOUT`), policy engine with
  non-overridable hard constraints, action gateway, lease manager with TTL and
  rollback, budget arbiter and event bus.
- `sky-audit`: append-only, sequence-numbered, hash-chained event log,
  redaction helper and diagnostic bundle skeleton.
- `sky-plugin-runtime`: participant registry and `OBSERVE_ONLY` default policy
  with the four evidence levels (L0-L3).
- `sky-world`: drop-item state machine and protection rules (no Bukkit calls).
- Platform probe interfaces with opt-in reflection, fail-closed behaviour and
  a config-key guard that reports unknown keys instead of writing them.
- `sky-ai-client`: provider abstraction, safe-by-default configuration and
  advice schema validation. **No HTTP client, no API key handling yet.**
- `sky-cli`: runnable SHADOW report demo; `sky-bootstrap`: pure-Java assembly
  and compatibility check; `sky-testkit`: fakes; `sky-benchmarks`: deterministic
  replay scenario.
- Two reference adapters: a native SPI participant (background I/O) and an
  `ExternalPluginAdapter` (cache/database/command/world-sync shaped plugin).
- Unit tests for state machine hysteresis, action gateway permissions,
  lease TTL/conflict/rollback idempotency, redaction, schema validation,
  ring buffer, histogram and unknown-plugin policy.
- Open-source governance: GPL-3.0-or-later, CONTRIBUTING, SECURITY,
  CODE_OF_CONDUCT, RELEASE, CI and release workflows, issue/PR templates,
  `docs/version-matrix.md` with explicit `UNRESOLVED` markers.

### Known limitations

- No installable plugin JAR and no `plugin.yml` yet (P1).
- Purpur 1.21.11 concrete build, SHA-256 and upstream Paper build are
  `UNRESOLVED`; no dependency coordinate or download link was invented.
- No measured performance numbers. Any P99 overhead or TPS improvement claim
  is intentionally absent until a reproducible baseline exists.
