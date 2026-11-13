# Architecture

## Positioning

SKY is a **server plugin suite**, not a Minecraft mod: no mod loader, no
patched server, no source modification of Minecraft/Paper/Purpur. Everything is
built on the Bukkit/Paper public API where possible; Purpur-specific abilities
are isolated behind a startup capability probe and adapters.

## Layers and dependency direction

```
                 sky-bootstrap / sky-cli
                          |
   +----------+-----------+-----------+----------+
   |          |           |           |          |
sky-world  sky-plugin-  sky-ai-    sky-audit  sky-platform-*
           runtime      client                 (paper/purpur)
   |          |           |           |          |
   +----------+-----------+-----------+----------+
                          |
                      sky-core            (pure Java, no server types)
                          |
                      sky-api             (stable SPI, minimal deps)
```

Hard rules:

- `sky-api` depends on nothing but the JDK. No server types, no AI SDK types.
- `sky-core` depends only on `sky-api`. It must be testable with no Minecraft
  server and no network (task book §3.3).
- Platform modules translate server signals into API DTOs; they never leak a
  Purpur type into the core state machine.
- Adapters are independently versioned and **not enabled by default**.
- AI is downstream of the event bus: it observes and advises, never decides.

## Guard state machine

```
NORMAL -> WATCH -> DEGRADE -> CRITICAL -> RECOVERY -> NORMAL
                      \          /
                        LOCKOUT
```

- Entering a higher-pressure state requires **sustained** violation.
- Exiting uses a **lower threshold and a longer duration** (hysteresis).
- Only one major action at a time; cooldown between actions.
- Recovery is staged; never restore all entities, caches and tasks at once.
- `LOCKOUT` stops further automatic action and keeps a human recovery path.

Candidate thresholds (P95 MSPT over 1s, P99 over 10s, weighted TPS,
consecutive 50/100ms ticks, GC pauses, network queue, player/region density,
entity/chunk/pathfinding/spawn pressure) are **initial configuration values,
not universal defaults**. They must be calibrated on the target hardware.

## Decision model

`priorityScore = urgency × playerImpact × deadlinePressure × evidenceConfidence
                 / (estimatedCost × actionRisk)`

The score is advisory only. It is always combined with **non-overridable hard
constraints**: world/inventory sync boundary, permissions, dependency order,
business floors, cooldowns and leases, the irreversible-action ban, player
safety and data durability. A high score can never authorise an unregistered
or irreversible action.

## Action pipeline (§10)

```
validate capability -> check permission -> check dependencies
-> check safe range -> dry-run -> acquire lease -> snapshot old state
-> apply -> verify -> audit -> release or rollback
```

Lease fields: `leaseId`, target, old/new value, initiator, preconditions, TTL,
verification query, rollback action, checksum, evidence references.

Fail-safe: a failed config write never triggers guessed follow-up writes;
verification failure rolls back automatically; a lost/expired lease restores;
a failed rollback enters `LOCKOUT` and produces a diagnostic bundle; actions
that could double-write are never retried indefinitely.

## Development stages

| Stage | Goal | Status in 0.1.0 |
| --- | --- | --- |
| P0 | Trustworthy observation: skeleton/CI, version & capability probe, tick/MSPT histogram, sampling, sampling-overhead control, SHADOW report & diagnostic bundle | **in progress / core logic delivered** |
| P1 | Conservative world protection: action gateway, lease, approval, rollback; simulation-distance budget; spawn/activation budget; safe drop aggregation; framed recovery + write-ahead log; seeded load test | gateway/lease/rules delivered in pure Java; no server actions yet |
| P2 | Cooperative plugin scheduling: ResourceParticipant SPI, self-developed adapters, two reference adapters, unknown-plugin evidence model, ExternalPluginAdapter, checkpoint/quiesce/resume suite | SPI + evidence model + reference adapters delivered |
| P3 | AI & repair assistant: provider abstraction, async DeepSeek client, redaction/Schema/circuit breaker/audit, incident replay, chat-feedback correlation, human-approved plans | abstraction + schema only; **no HTTP client** |
| P4 | Strong-isolation assessment: sidecar design, IPC, telemetry/audit offload, separate-process PoC, cgroup/PID/memory limits | not started (design only) |

Each PR must deliver exactly one verifiable increment, in this order.
"Server patch" is explicitly **not** a default prerequisite (§17-P4).

## What "fair" means here

Not OS-level fairness. It means: core services have a floor budget that normal
plugins cannot squeeze; participants at the same service tier are allocated by
urgency, player impact, deadline, estimated cost and risk; continuous overuse
gets a short-term reduction rather than a permanent penalty; unadapted plugins
are read-only by default and cannot bypass policy with a high-privilege name;
modules that truly need hard CPU/memory/PID/failure-domain isolation belong in
a separate process or container in a later stage — a pure plugin must not
pretend to provide that.

## File map

Task book §3.2 layout is reproduced in the repository root. The `schemas/`
directory holds versioned JSON Schemas, `config/` the default-safe config,
`tools/` helper scripts, and `.github/` CI, release workflow and templates.
