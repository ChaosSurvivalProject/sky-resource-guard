<!--
SPDX-License-Identifier: GPL-3.0-or-later
Copyright (C) 2026 SKY Resource Guard contributors
-->
# Governance

SKY Resource Guard is developed in the open under the
[`ChaosSurvivalProject`](https://github.com/ChaosSurvivalProject) organization.
This document describes how decisions are made; it does not grant anyone the
right to bypass the safety invariants in [CONTRIBUTING.md](CONTRIBUTING.md).

## Roles

| Role | Responsibility |
| --- | --- |
| Contributor | Opens issues and pull requests, follows the ground rules |
| Maintainer | Reviews and merges PRs, triages issues, cuts releases |
| Security reporter | Reports privately through Security Advisories |

Maintainers are the organization members with write access. Review requests
are assigned via [`.github/CODEOWNERS`](.github/CODEOWNERS). New maintainers
are added by the existing maintainers; there is no self-appointment.

## Decision making

1. **One verifiable increment per PR.** Large subsystems are split into P0-P4
   slices (see [`docs/architecture.md`](docs/architecture.md)).
2. **CI is the gate.** A change merges only when the `CI` workflow is green:
   schema validation, style and dependency checks, sensitive-pattern scan,
   build, tests and javadoc. Branch protection on `main` enforces this.
3. **Facts over claims.** A PR must not invent a build number, API, config key,
   test result or performance gain. Unknowns are marked `UNRESOLVED` with a
   probe plan. A percentage without a reproducible baseline is rejected.
4. **Safety invariants are not negotiable and cannot be voted away**: no
   `Thread.stop/suspend/resume`, no forced reclamation of an unknown plugin's
   objects, no `System.gc()` loops, no world writes moved to async threads, no
   AI call on the main tick, no irreversible action in an automatic role.
5. **The core stays server-free.** `sky-api`, `sky-core` and `sky-audit` must
   compile and test with no Minecraft server on the classpath.

Disagreements are resolved in the PR discussion. If consensus cannot be
reached, the change does not merge; the conservative (no production effect)
option wins.

## Releases

Releases are tag-driven (`.github/workflows/release.yml`). A **stable** release
is blocked while `docs/version-matrix.md` contains `UNRESOLVED` entries;
experimental pre-release tags are allowed but are published as GitHub
pre-releases. See [RELEASE.md](RELEASE.md). No release deploys anything to a
production server.

## Licence and contributions

- The project is **GPL-3.0-or-later** ([LICENSE](LICENSE)). Every source file
  carries an SPDX header.
- By contributing you agree that your contribution is licensed under the same
  terms. There is **no CLA** and no copyright assignment.
- Do not paste code with an unknown or incompatible licence.
- All participants follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Scope limits

This project is safety infrastructure for live servers. Governance favours
auditability, reversibility and honest uncertainty over feature velocity.
