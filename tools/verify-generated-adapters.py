#!/usr/bin/env python3
"""Verify the AI-Skill-generated adapter is reproducible and auto-registered.

CI gate: a generated adapter must never drift from its spec/templates, and its
ServiceLoader entry must be present in the installable plugin. This is what
makes "code produced by the skill adapts automatically" verifiable rather than
a claim.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
GEN = ROOT / "skills/sky-adapter-generator/scripts/generate_adapter.py"
SPEC = ROOT / "skills/sky-adapter-generator/examples/acme-quests.json"
SCHEMA = ROOT / "skills/sky-adapter-generator/schemas/adapter-spec.schema.json"
MODULE = ROOT / "adapters/sky-adapter-generated-acme"
MODULE_LABEL = "adapters/sky-adapter-generated-acme"
PLUGIN_SERVICES = (
    ROOT / "sky-plugin-paper/src/main/resources/META-INF/services"
    / "dev.skyguard.api.ResourceParticipant"
)
FORBIDDEN = ("Thread.stop", "Thread.suspend", "Thread.resume", "System.gc(",
             "setAccessible(", "Runtime.getRuntime().exec")


def fail(message):
    print("::error:: " + message, file=sys.stderr)
    return 1


def main():
    status = 0

    # 1. Schema is valid and the example spec satisfies it.
    try:
        from jsonschema import Draft202012Validator
    except ImportError:
        print("[generated] jsonschema unavailable; generator fallback validation applies")
    else:
        schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        errors = list(Draft202012Validator(schema).iter_errors(spec))
        if errors:
            for err in errors:
                status |= fail("spec: " + err.message)
        else:
            print("[generated] adapter-spec schema + example: valid")

    # 2. Regenerate into a temp dir and diff against the committed module.
    result = subprocess.run(
        [sys.executable, str(GEN), "--spec", str(SPEC), "--out", str(MODULE),
         "--module-label", MODULE_LABEL, "--check"],
        capture_output=True, text=True, check=False,
    )
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    if result.returncode != 0:
        status |= fail("generated artefacts are stale or invalid")

    # 3. The generated service must be registered in the installable plugin.
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    entry = spec["packageName"] + "." + spec["className"]
    lines = [
        line.strip() for line in PLUGIN_SERVICES.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    if entry not in lines:
        status |= fail("plugin services file does not register " + entry)
    else:
        print("[generated] auto-registration entry present: " + entry)

    # 4. Generated Java must stay inside the safety boundary.
    for java in sorted(MODULE.rglob("*.java")):
        text = java.read_text(encoding="utf-8")
        for pattern in FORBIDDEN:
            if pattern in text:
                status |= fail("forbidden API in " + java.name + ": " + pattern)

    if status == 0:
        print("[generated] OK - reproducible, registered and safety-bounded")
    return status


if __name__ == "__main__":
    sys.exit(main())
