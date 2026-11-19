<!--
SPDX-License-Identifier: GPL-3.0-or-later
Copyright (C) 2026 SKY Resource Guard contributors
-->
# GENERATED ADAPTER - DO NOT EDIT BY HAND

This module was produced by the **`sky-adapter-generator`** AI Skill. Edit the
spec and regenerate instead of editing these files.

| Field | Value |
| --- | --- |
| Spec | `{{SPEC_FILE}}` |
| Generator | `skills/sky-adapter-generator/scripts/generate_adapter.py` |
| Service | `{{PACKAGE}}.{{CLASS}}` |
| Plugin | `{{PLUGIN_NAME}}` (`{{PLUGIN_ID}}` `{{VERSION}}`) |
| Mode | **OBSERVE_ONLY** (P1.1 hard constraint) |
| Declared capabilities | `{{CAPS_RAW}}` |

Regenerate and verify:

```bash
python3 skills/sky-adapter-generator/scripts/generate_adapter.py \
  --spec {{SPEC_FILE}} --out {{MODULE_DIR}}
python3 tools/verify-generated-adapters.py
./gradlew build
```

## What "automatic adaptation" means here

The generated `META-INF/services/dev.skyguard.api.ResourceParticipant` entry is
discovered by `SkyPlugin.onEnable()` through `java.util.ServiceLoader`. The
participant is auto-registered in `ParticipantRegistry` and sampled by the
read-only task - **no edit to the plugin source is required**. Generated code
declines every mutating lifecycle call (`quiesce`, `checkpoint`, `resume`,
`releaseForeignResources`, `requestLowerPower`) so an AI-produced adapter can
never change world state on its own.

## Limits (do not overstate)

- `sample()` returns declared starting values, **not measurements** of a live
  plugin. Replace them with real read-only readings only after review.
- Cross-class-loader discovery of adapters installed as separate plugin jars
  is **UNRESOLVED** (P2); only adapters visible to the SKY plugin's own class
  loader are discovered today.
- No performance gain, TPS or P99 number is claimed anywhere.
