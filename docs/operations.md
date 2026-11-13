# Operations manual

## First install

1. Verify the exact Purpur 1.21.11 build and fill in `docs/version-matrix.md`.
   Until it is resolved, SKY must stay in read-only / suggest mode.
2. Start in **`SHADOW`** with AI off, automation off, `OBSERVE_ONLY` for unknown
   plugins, dry-run for every config write, and sampling with an overhead cap.
3. Observe for at least one full load cycle (peak + quiet). Check the SHADOW
   report and the sampling overhead itself.
4. Calibrate `StateThresholds` on the **target hardware**. The shipped numbers
   are initial candidates, not defaults.
5. Only then consider `ADVISE`, then `PROTECT`. `AUTOMATE` is the last step and
   still bounded by policy, permissions, leases and hard constraints.

## Run modes

| Mode | Production change | AI |
| --- | --- | --- |
| `SHADOW` | none | off by default |
| `ADVISE` | none; plans and advice only | optional |
| `PROTECT` | safe, reversible, leased actions | advisory |
| `AUTOMATE` | policy-bounded actions | advisory, always removable |

## Routine checks

```bash
/sky status
/sky metrics --world
/sky advice list
/sky audit query
/sky diagnostic create      # redacted, gated
```

Watch for: rising MSPT P99, growing network send queue, repeated `WATCH`→
`DEGRADE` transitions, a rising `LOCKOUT` count, audit-chain failures, or
sampling overhead above `max-overhead-p99-ms`.

## Incident flow

```
diagnose -> plan -> dry-run -> approve -> apply -> verify -> rollback / close
```

"Self-correction" may only choose from the pre-registered action directory with
policy approval. It must never generate code, reflect private APIs, modify core
files, delete worlds/backups or run an unregistered shell command.

## `LOCKOUT` recovery

`LOCKOUT` means a rollback failed or a safety invariant was violated. Automatic
action is stopped. An administrator must:

1. export the diagnostic bundle (`/sky diagnostic create`);
2. inspect the failing lease, old/new value and rollback result in the audit log;
3. restore the old state manually and verify it;
4. call `manualResetToNormal()` (or the equivalent admin command);
5. record the incident and add a regression test before re-enabling automation.

## Rollback and uninstall

- Every applied action has a lease with the old value and a rollback action;
  verification failure rolls back automatically.
- Drop items: a `RESTORE_PENDING` write-ahead record is written before
  serialisation. On startup, pending records must be rebuilt or alerted -
  **never silently dropped**. Restoration is capped per tick to avoid a storm.
- Uninstall: SKY disables automation first, restores any leased setting,
  flushes/restores pending drop records, then disables the plugin. Participants
  unregister their own listeners, tasks, connections and file watchers.

## Heavy diagnostics

Full thread dumps and object histograms require an admin trigger or a
WATCH-or-higher state, and must have a CPU, time and file-size budget. They are
never part of the normal tick loop.

## What not to do

Do not run `System.gc()` on a schedule, do not lower all thread priorities, do
not delete world/backup data to "fix" a lag spike, do not paste raw logs into
issues, and do not trust a performance percentage without a reproducible
baseline.
