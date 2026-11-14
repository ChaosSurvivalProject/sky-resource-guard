<!--
SPDX-License-Identifier: GPL-3.0-or-later
Copyright (C) 2026 SKY Resource Guard contributors
-->
# Support

Thanks for using SKY Resource Guard. Please read the project status first:
this repository is **experimental (P0)**. It is pure-Java core logic, **not an
installable server plugin**, and it has no measured performance results. There
is no production deployment to support yet.

## Where to ask

| Topic | Channel |
| --- | --- |
| Bug in the pure-Java core, a failing test, a build problem | [Bug report issue](https://github.com/ChaosSurvivalProject/sky-resource-guard/issues/new?template=bug_report.yml) |
| Feature or design proposal | [Feature request issue](https://github.com/ChaosSurvivalProject/sky-resource-guard/issues/new?template=feature_request.yml) |
| How the SPI works | [`docs/spi.md`](docs/spi.md), then a `question` issue |
| Security vulnerability | **Do not open an issue.** Use [Security Advisories](https://github.com/ChaosSurvivalProject/sky-resource-guard/security/advisories/new), see [SECURITY.md](SECURITY.md) |
| Contribution process | [CONTRIBUTING.md](CONTRIBUTING.md) and [GOVERNANCE.md](GOVERNANCE.md) |

## Before filing a bug

Please include:

- `java -version` (JDK 21 is required) and the exact `./gradlew` command;
- the full failing test name and its output, or a minimal reproducer;
- whether you changed anything in `config/` or `schemas/`.

For a server-related problem, note that **no server artifact is resolved in
P0**. Reproduce it against the pure-Java core first; if it only happens on a
live Purpur server, include the exact build and `sha256sum` of the server jar
so the report can be compared with [`docs/version-matrix.md`](docs/version-matrix.md).

## What to expect

This is a volunteer-run, pre-1.0 project. There is **no SLA, no paid support
and no guaranteed response time**. Issues about facts the project deliberately
does not claim (TPS gains, P99 numbers, a pinned Purpur build) will be closed
as out of scope until a reproducible baseline exists. Honest `UNRESOLVED`
markers are intentional, not missing documentation.

## Roadmap questions

P0-P4 scope and the "one verifiable increment per PR" rule are described in
[`docs/architecture.md`](docs/architecture.md) and [CHANGELOG.md](CHANGELOG.md).
