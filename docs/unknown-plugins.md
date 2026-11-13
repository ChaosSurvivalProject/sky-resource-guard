# Unknown and unadapted plugins

> **Observable does not mean attributable. Having a restriction hook does not
> mean a plugin can be safely frozen.**

## Default: `OBSERVE_ONLY`

An unadapted plugin is observed, never controlled. SKY may record:

- enabled state, event timings, scheduled tasks, thread **names**, heap totals,
  GC, file handles and network signals.

SKY must NOT:

- attribute JVM-global metrics to a specific plugin without evidence;
- force-stop, suspend or freeze a thread it does not own;
- scan or modify private fields, clear internal queues or reflect into them;
- force-unload a running plugin;
- claim thread ownership from a similar-looking name;
- write an unrecognised Paper/Purpur YAML key.

## Four evidence levels

| Level | Meaning | Allowed use |
| --- | --- | --- |
| `L0_FACT` | plugin self-reported or an explicit platform API | automatic restriction (with a lease) |
| `L1_STRONG` | explicit synchronous cost / task record around a plugin call | automatic restriction (with a lease) |
| `L2_WEAK` | thread name, class loader, time correlation | warning only |
| `L3_GUESS` | resource totals and player perception | investigation suggestion only |

`EvidenceLevel.mayTriggerAutomaticRestriction()` encodes this rule in code, and
`PolicyEngine` rejects any intent whose evidence is `warningOnly()`.

## Safe measures, in ascending risk

1. Lower the admission rate of new requests / delay SKY's own proxied entry points.
2. Pause SKY-managed shared-cache warm-up, AI tasks and non-critical maintenance.
3. Reduce input frequency (proxied command cooldown, event merging).
4. Call a plugin's own documented low-power mode.
5. Disable a known non-critical sub-feature through a dedicated adapter.
6. With administrator authorisation, disable/unload the whole plugin - only
   after dependency checks, backup, player-impact notice and a recovery plan.
7. Migrate a high-risk plugin to a separate process/sidecar (stage U4).

## Forbidden "pseudo-solutions"

Killing by thread name; clearing an undocumented internal queue; reflecting a
private field to `null`; globally lowering thread priorities; periodic Full GC;
unloading a ClassLoader while unknown threads survive; modifying unrecognised
Purpur/Paper keys; blocking the main thread with SQL/network/file I/O to
"pause" a plugin.

## `ExternalPluginAdapter`

```java
public interface ExternalPluginAdapter {
    AdapterMetadata metadata();
    CompatibilityReport probe(PluginRuntimeSnapshot runtime);
    Optional<ResourceSample> sample(SampleBudget budget);
    Optional<PowerHandle> requestLowerPower(PowerLevel level, Lease lease);
    Optional<CheckpointHandle> checkpoint(Deadline deadline);
    Optional<QuiesceHandle> quiesce(QuiesceRequest request);
    ResumeReport resume(ResumeToken token);
}
```

Every `Optional.empty()` means "not supported, stay read-only". Adapters live in
`adapters/`, are versioned independently, and are **never enabled by default**
(`AdapterMetadata` forces `enabledByDefault = false`). They may use only public
APIs, public events and documented commands. Where the target plugin lacks a
public hook, the correct action is to ask its maintainer to extend the API.

## Iteration roadmap

| Stage | Goal | Status |
| --- | --- | --- |
| U0 | Unified identity, public API, event and thread observation | policy layer shipped |
| U1 | Evidence chains by plugin / subsystem / call site | shipped (L0-L3) |
| U2 | Optional official adapters for core ecosystem plugins | SPI + example shipped |
| U3 | Community "low-power mode / health check / deferred task" interface | not started |
| U4 | Separate process or sidecar for uncooperative high-risk plugins | design only |

## Support matrix

| Plugin category | Default mode | Automatic restriction | Whole-plugin disable |
| --- | --- | --- | --- |
| Native SPI participant | cooperative | yes, leased | admin only |
| Official bridge adapter | cooperative | yes, leased | admin only |
| Controlled integration (public command) | limited | via documented command | admin only |
| Unknown / unadapted | **OBSERVE_ONLY** | **no** | admin + checks + backup |
