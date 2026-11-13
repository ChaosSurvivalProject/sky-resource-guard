# ResourceParticipant SPI

Package: `dev.skyguard.api` · Stability: **0.1.0, experimental** · Changes follow semantic versioning.

## The interface

```java
public interface ResourceParticipant {
    ParticipantMetadata metadata();
    CapabilitySet capabilities();
    HealthSnapshot sample(SampleBudget budget);
    boolean requestLowerPower(PowerLevel level, Lease lease);
    CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest request);
    CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest request);
    CompletableFuture<ResumeReceipt> resume(ResumeToken token);
    CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest request);
}
```

All DTOs live in `dev.skyguard.api.Contracts` as immutable records. Import them
with `import dev.skyguard.api.Contracts.*`.

## What a participant must express

| Field | Meaning |
| --- | --- |
| `pluginId`, `version`, `dependencies`, `serviceTier`, `faultDomain` | identity and dependency graph |
| `CapabilitySet` | truthfully declared abilities; never claim unaudited ones |
| `ExecutionPhase` | `TICK_ESSENTIAL`, `WORLD_SYNC`, `PLAYER_FACING`, `BACKGROUND_IO`, `MAINTENANCE` |
| `Urgency` | `SAFETY`, `CRITICAL`, `NORMAL`, `DEFERABLE`, `OPTIONAL` |
| `SideEffect` | `PLAYER_VISIBLE`, `ECONOMY`, `INVENTORY`, `WORLD_WRITE`, `EXTERNAL_IO`, `IRREVERSIBLE` |
| queue depth, concurrency, estimated heap bytes, CPU time | bounded, low-cost sampling |
| `idempotencyKey`, `generation`, `rollbackToken` | retry and recovery safety |
| health / dependency status | used by the policy engine, not by an AI model |

## Sampling contract

`sample(SampleBudget)` MUST be read-only, bounded and cheap. It runs on or near
the main tick in P0, so it must not do blocking I/O, allocate large structures
or touch world state. The overhead budget is `config.yml → sky.tick-sampling`;
exceeding it triggers automatic downsampling. A participant that throws is
isolated by `ParticipantRegistry` and recorded as a failure - SKY never tries
to repair it by interrupting arbitrary threads.

## quiesce contract (ordered)

1. **Atomically reject new work** (set an `accepting` flag before anything else).
2. Finish or safely cancel in-flight work; cancel not-yet-started tasks.
3. Reach a checkpoint.
4. Flush queues and durable state.
5. Close only the participant's **OWN** executors, connections, listeners and
   file watchers.
6. Report `QUIESCED`, `TIMEOUT` or `FAILED`.

SKY does **not** scan all JVM threads and interrupt them. Only executors that
the participant explicitly registered and that accept the interruption contract
are shut down. A `Deadline` bounds the critical section; checkpoint must not do
I/O while holding a lock.

## resume contract (ordered, idempotent)

1. Validate lease, `generation` and dependencies.
2. Restore connections, schema and caches.
3. Restore scheduling/executors.
4. Open player commands **last**.
5. All steps must be retryable and idempotent: a repeated
   `(idempotencyKey, generation)` is a replay, not a second write.

See `adapters/sky-adapter-selfexample-a` for a complete reference implementation
and `adapters/sky-adapter-selfexample-b` for the `ExternalPluginAdapter` path.

## Versioning rules (task book 18.5)

- Public API uses semantic versioning.
- A breaking SPI change requires `docs/migration.md` plus at least one release
  cycle of deprecation.
- New adapters and AI providers must be independently enableable.
- Self-developed identity raises **adaptation priority only**; it never grants
  a higher budget or an implicit permission.
