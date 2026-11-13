# Contributing to SKY Resource Guard

Thanks for contributing. SKY is safety infrastructure for live Minecraft
servers, so correctness and honesty matter more than feature count.

## Ground rules

1. **One verifiable increment per PR.** Follow the P0-P4 order in
   `docs/architecture.md`. Do not submit a whole subsystem in one PR.
2. **Do not invent facts.** Never fabricate a Paper/Purpur API, config key,
   build number, test result or performance gain. If something is unknown,
   write `UNRESOLVED` and describe a probe plan (task book 19).
3. **The core stays server-free.** `sky-api`, `sky-core` and `sky-audit` must
   compile and test without a Minecraft server, without an AI network call and
   without any Paper/Purpur type on their classpath.
4. **Safety invariants are not negotiable.** No `Thread.stop/suspend/resume`,
   no forced reclamation of unknown plugins' objects, no world writes moved to
   async threads, no `System.gc()` loops, no AI request on the main tick.
5. **Every action needs** capability validation, permission, dry-run, lease
   with TTL, snapshot, verify, audit and rollback.

## Commits

Conventional Commits, e.g. `feat(core): add hysteresis state machine`,
`fix(audit): keep hash chain on failed append`, `docs: mark purpur build unresolved`.

## Before opening a PR

```bash
./gradlew clean build
```

Then fill in the PR template, including the work-round report from task book 19:

```text
【本轮目标】/【已修改文件】/【行为变化】/【架构边界】/【约束核验】
【测试结果】/【兼容性】/【性能开销】/【风险与未决项】/【下一步】
```

Performance PRs must include baseline, hardware, exact commands and raw
results. A percentage without a reproducible baseline will be rejected.

## Reporting a security issue

Do **not** open a public issue. See [SECURITY.md](SECURITY.md).
