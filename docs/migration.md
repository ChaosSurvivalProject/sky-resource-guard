# Migration guide

Current public API version: **0.1.0 (experimental, P0)**.

## Compatibility promise

`sky-api` follows semantic versioning. Because 0.x is experimental, a minor
version may still change the SPI, but every breaking change will:

1. be listed here with a concrete before/after example;
2. remain deprecated for at least one release cycle where feasible;
3. ship with an adapter or shim so existing participants keep compiling;
4. be called out in `CHANGELOG.md` and the PR work-round report.

## 0.1.0 → next

No released migration yet. Planned, potentially breaking areas (design notes,
not commitments):

| Area | Possible change | Mitigation |
| --- | --- | --- |
| `Contracts.ParticipantMetadata` | add dependency graph and data-recovery descriptors | add a new constructor with defaults; keep the old one deprecated |
| `ResourceParticipant` | add a `healthCheck()` method | provide a `default` implementation returning a static healthy snapshot |
| `ActionKind` | add new pre-registered kinds | additive only; schema enum updated in lockstep |
| `AdviceProposal` | `prompt-schema-version` bump | `schemas/` keeps a versioned file; validator accepts the previous version for one cycle |
| `ExternalPluginAdapter` | add capability-negotiation metadata | default method returning `Optional.empty()` |

## Adapters and AI providers

Adapters are independently versioned and disabled by default, so upgrading SKY
core never implicitly enables or changes an adapter. AI providers are selected
by `provider` + `model-alias` + `base-url` in config; the core has no vendor
type, so switching providers is a config change plus a provider adapter.

## Config keys

Cross-minor-version Purpur/Paper config keys are **not assumed stable**.
`PurpurConfigGuard` reports unknown keys and refuses to write them. A new
server version requires a fresh startup probe and a `docs/version-matrix.md`
update before any key is considered verified.

## Checklist for a breaking SPI PR

- [ ] `docs/migration.md` entry with before/after code.
- [ ] Deprecation annotation and one release cycle.
- [ ] Both example adapters compile against the new SPI.
- [ ] `./gradlew build` and all tests pass.
- [ ] `CHANGELOG.md` updated; `sky.version` bumped.
