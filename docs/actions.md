# Actions, leases and permissions

## Pipeline

Every production change goes through `ActionGateway.execute(...)`:

```
validate capability -> check permission -> check dependencies
-> check safe range -> dry-run -> acquire lease -> snapshot old state
-> apply -> verify -> audit -> release or rollback
```

There is **no other path** to a configuration or world change. AI advice, chat
feedback and CLI input can only produce an `ActionIntent`; none of them can call
the executor directly.

## `ActionIntent` fields

`intentId`, `ActionKind` (pre-registered enum), `target`, `initiator`,
`heldPermissions`, `oldValue`, `newValue`, `ttl`, `sideEffects`, `evidence`,
`evidenceRefs`, `dryRun`, `approved`, `rollbackAction`.

Because `ActionKind` is an enum, an unregistered action cannot even be
constructed. `AdviceSchemaValidator` additionally rejects any recommended
dry-run whose name is not in the enum or whose kind is high-risk.

## Hard constraints (cannot be overridden by a score)

- world / inventory synchronous boundary;
- permissions (`Permissions.requiredFor(kind)`);
- dependency order and business floors;
- cooldown and lease;
- irreversible-action ban;
- player safety and data durability;
- `OBSERVE_ONLY` evidence rule (L2/L3 cannot authorise a restriction).

`priorityScore` from `docs/architecture.md` is advisory and is recorded in the
decision for explainability only.

## Lease fields

`leaseId`, `target`, `actionId`, `initiator`, `oldValue`, `newValue`,
`issuedAt`, `ttl`, `verificationQuery`, `rollbackAction`, `checksum` (SHA-256),
`evidenceRefs`.

`LeaseManager` rejects a second active lease on the same target, purges expired
leases and always computes a checksum over the immutable fields.

## Fail-safe behaviour

| Situation | Behaviour |
| --- | --- |
| config write fails | stop; never guess a follow-up write |
| verification fails | automatic rollback |
| controller crashes / lease lost | expiry restores old state |
| rollback fails | `LOCKOUT` + diagnostic bundle + human request |
| executor throws | rollback + `INTERNAL_ERROR`, no infinite retry |
| possible double write | never retried automatically |
| `LOCKOUT` | no further automatic action; `manualResetToNormal()` only |

## Permission nodes

| Node | Purpose |
| --- | --- |
| `sky.observe.read` | read telemetry |
| `sky.advice.read` | read AI/advisor output |
| `sky.plan.dryrun` | create dry-run plans |
| `sky.protect.manage` | manage protection settings |
| `sky.action.safe` | safe, reversible actions |
| `sky.action.expensive` | quiesce / cache eviction |
| `sky.action.plugin.lifecycle` | whole-plugin lifecycle |
| `sky.approve.standard` / `sky.approve.critical` | approval levels |
| `sky.ai.configure` | change AI provider settings |
| `sky.audit.export` | export audit/diagnostic data |
| `sky.override.emergency` | emergency override |

Economy writes, inventory changes, teleports, bans, irreversible deletion and
external network configuration **never** enter a fully automatic role.

## Commands (planned; P0 implements only the read-only subset)

```
/sky status
/sky metrics [--world] [--plugin]
/sky advice list
/sky plan create|dryrun|approve|apply|verify|rollback
/sky plugin list|inspect
/sky adapter list
/sky participant quiesce <id> --dry-run
/sky participant resume <id>
/sky drops inspect
/sky drops restore-now <area> --dry-run
/sky ai status|reload
/sky audit query
/sky diagnostic create
```

In P0, `sky-cli` implements `status`, `shadow-report`, `audit query` and `help`.
Every write-oriented command defaults to `--dry-run`.
