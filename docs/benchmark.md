# Benchmark method

> **Status: NOT MEASURED.** This repository contains no performance result,
> no TPS figure and no percentage improvement claim. `sky-benchmarks` ships a
> fixed-seed, pure-Java replay that only exercises the metrics code. It is
> explicitly **not** a server benchmark.

## Why there are no numbers yet

A credible claim needs a pinned Purpur 1.21.11 build (SHA-256), known JDK and
OS, a real world save, realistic plugins and a reproducible load generator.
Those facts are `UNRESOLVED` in `docs/version-matrix.md`. Publishing a percentage
without them would violate the task book's honesty rules.

## Required method (task book 15.4 / 16.1 / 16.10)

| Requirement | Detail |
| --- | --- |
| Fixed seed + entity layout | record the seed, world name, entity counts and plugin list |
| Three modes | `baseline` (SKY absent), `shadow`, `protect` |
| Load scenarios | robot movement, teleport storms, drop-item storms, mobs/villagers/vehicles/redstone/tile-entity load |
| Stability | sample only after each tier has stabilised |
| Report | P50 / P95 / P99, confidence interval, failure rate, sampling overhead |
| Isolation | change exactly one policy family per run |
| Honesty | never present robot behaviour as real player experience |

## Acceptance targets (to be verified, not assumed)

1. NORMAL-mode monitoring adds **< 1 ms** to main-thread P99, with automatic
   downsampling when over budget.
2. All automatic actions have audit, dry-run, permission, TTL, preconditions
   and rollback.
3. Unauthorised or irreversible automatic actions: **0**.
4. AI offline for 30 minutes: telemetry, world protection, recovery and
   commands still work.
5. Invalid schema / prompt injection / over-privileged advice execution: **0**.
6. Unknown plugins default to `OBSERVE_ONLY`; no forced thread stop.
7. Drop serialisation, power loss and repeated-restore tests: no silent loss.
8. Fault injection produces no sustained thread / handle / executor growth.
9. Unsupported capabilities fall back to read-only, never a crash loop.
10. Performance gains are shown **relative to the target hardware baseline**
    and are reproducible.

## Reproducing (once a real server baseline exists)

```bash
# 1. record the environment in docs/version-matrix.md
sha256sum purpur-1.21.11.jar
java -version

# 2. pure-Java replay (sanity check only, NOT a benchmark)
./gradlew :sky-benchmarks:run

# 3. real runs: baseline / shadow / protect, fixed seed, one policy family each
#    -> write raw samples, P50/P95/P99, CI and failure rate here
```

## Reporting template

```text
Environment: <server build + sha256> / <JDK> / <OS> / <CPU, RAM, disk>
Baseline:    p50/p95/p99 = ..., failure rate = ...
Shadow:      p50/p95/p99 = ..., sampling overhead = ...
Protect:     p50/p95/p99 = ..., actions applied / rolled back = ...
Commands:    <exact command lines>
Seed:        <seed>
Unmeasured:  <what is still unknown>
```
