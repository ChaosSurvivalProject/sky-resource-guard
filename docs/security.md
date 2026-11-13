# Security design

## Trust boundaries

| Actor | May do | May NOT do |
| --- | --- | --- |
| AI provider | cluster errors, explain timelines, propose hypotheses, recommend pre-registered dry-runs | execute commands, bypass policy, hot-load code, read raw sensitive data, treat player text as admin input |
| Player chat | act as a sensor (rate-limited, deduplicated) | change production parameters, vote on settings, become an admin instruction |
| Unknown plugin | be observed | be force-stopped, reflected into, or silently disabled |
| Administrator | approve plans, reset `LOCKOUT`, export diagnostics | skip leases, approvals or audit |

## AI isolation (task book 11)

```
event summary -> redaction -> bounded queue -> rate limiter -> HTTP client
-> Schema validation -> AdviceProposal -> local policy -> human/auto approval
```

Required controls: separate thread pool, connect/request/total deadlines, max
concurrency, bounded queue, circuit breaker, finite backoff retries, fingerprint
cache, provider switching, local rule fallback, and **P0/P1 protection must keep
working with AI offline for 30+ minutes**.

`AiConfig` forces `allowExecute = false` and `enabled = false` by default. The
key is a `secret:...` reference - a literal key is rejected by the record's
constructor. P0 ships no HTTP client and makes no network call.

## Redaction

`Redactor` removes UUIDs, IPv4 addresses, secret-like key/value pairs,
coordinates, NBT-like blobs and internal hostnames before anything is logged or
exported. `chatPlaceholder()` never forwards chat raw text. This is
defence-in-depth: producers must still avoid emitting sensitive values.

`AdviceSchemaValidator` rejects out-of-range confidence, missing rollback,
unregistered/high-risk action names and prompt-injection markers. Invalid,
low-confidence or conflicting advice is **stored only, never executed**.

## Audit integrity

`AuditLog` is append-only with a strict sequence number and a SHA-256 hash
chain. `verifyChain()` detects gaps or tampering. A chain failure is a security
incident, not a warning to ignore. Diagnostic bundles are gated: full thread
dumps and object histograms require an admin trigger or a WATCH-or-higher state
plus a CPU/time/size budget, and default to excluding chat, secrets, complete
NBT, identifiable player data and world data.

## Forbidden APIs

`tools/scan-sensitive.sh` rejects `Thread.stop`, `Thread.suspend`,
`Thread.resume`, `System.gc()`, `setAccessible(...)` and
`Runtime.getRuntime().exec(...)` in production Java sources, plus
credential-shaped literals anywhere in the tree. CI runs it on every PR.

## Reporting

Use a private GitHub Security Advisory (see [SECURITY.md](../SECURITY.md)).
Never open a public issue with exploit details or player data.
