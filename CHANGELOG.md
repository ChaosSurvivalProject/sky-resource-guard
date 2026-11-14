# Changelog

All notable changes to this project are documented here. This project follows
[Semantic Versioning](https://semver.org/) for the public `sky-api` surface and
[Conventional Commits](https://www.conventionalcommits.org/) for commit messages.

## [Unreleased]

Status: still **experimental, not installable on a server**. This is a
hardening and open-source governance increment: no server artifact was
resolved, no performance claim was added, and `docs/version-matrix.md` keeps its
`UNRESOLVED` entries.

### Fixed

- `GuardStateMachine` stored transition history in an unbounded `ArrayList`.
  On a long-running server it grew forever and the SHADOW report grew with it.
  It is now a bounded deque (default 256, configurable) with an observable
  `historyDropped()` counter, so truncation is never silent.
- Action-slot reservation had a check-then-act race between the separately
  synchronized `requestActionSlot` and `markActionStarted`. Added an atomic
  `tryBeginAction`, now used by `ActionGateway`.
- `ActionGateway` **leaked an active lease when the verifier or rollback
  threw** (the `release` call was skipped). Lease release now lives in a
  `finally`; any throwing collaborator is treated as an unverified change, so
  it is rolled back and escalates to `LOCKOUT` if the rollback also fails.
  Only the exception class is audited, never the message.
- `LeaseManager.expire` silently discarded expired leases, which made the
  "lost/expired lease restores old state" guarantee impossible to implement.
  Added `drainExpired(Instant)`, returning the expired leases with their old
  value and rollback action for the caller to restore.
- `MetricsRingBuffer.max()` started at `0.0` and walked raw indexes: it clamped
  negative-valued metrics and did not express the live ring window. The
  running sum is also recomputed once per full lap to bound floating-point
  drift on long-running servers.

### Added

- 12 regression tests (70 -> 82): bounded history, atomic action slot, four
  exception-path lease-release cases, expired-lease recovery, and ring-buffer
  window/max/drift checks.
- `.github/dependabot.yml` (Gradle + GitHub Actions, weekly),
  `.github/CODEOWNERS`, `SUPPORT.md`, `GOVERNANCE.md`.
- CI: concurrency control, 20-minute timeout, `persist-credentials: false`.
- Release workflow: a real gate that refuses **stable** tags while the version
  matrix contains `UNRESOLVED` entries (experimental tags stay allowed but are
  published as GitHub pre-releases), plus tag-ref validation.
- README badges and links to the support/governance documents.

### Changed

- Repository settings (not tracked files): `main` branch protection requiring
  the CI status check, Dependabot security updates, vulnerability alerts.

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
