#!/usr/bin/env python3
"""Validate SKY JSON Schemas (CI step, task book 18.4).

Checks that every schema is a valid Draft 2020-12 schema, that a well-formed
AdviceProposal payload validates, and that a payload with an unregistered
action is rejected.
"""
import json
import pathlib
import sys

try:
    from jsonschema import Draft202012Validator, ValidationError
except ImportError:
    print("::error:: jsonschema is not installed (pip install jsonschema)", file=sys.stderr)
    sys.exit(2)

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "schemas"
status = 0

VALID = {
    "hypotheses": ["spawn budget saturated in world_nether"],
    "confidence": 0.82,
    "evidenceRefs": ["metric:mspt-p99", "event:spawn-failure"],
    "recommendedDryRuns": ["SPAWN_BUDGET"],
    "risk": "LOW",
    "rollback": "restore previous spawn budget",
    "missingEvidence": ["per-plugin cost breakdown"],
}

INVALID = dict(VALID, recommendedDryRuns=["DELETE_ALL_WORLDS"])

for schema_path in sorted(SCHEMA_DIR.glob("*.schema.json")):
    with schema_path.open(encoding="utf-8") as fh:
        schema = json.load(fh)
    try:
        Draft202012Validator.check_schema(schema)
        print(f"[schema] {schema_path.name}: valid Draft 2020-12 schema")
    except Exception as exc:  # noqa: BLE001 - CI diagnostics
        print(f"::error:: {schema_path.name}: {exc}", file=sys.stderr)
        status = 1
        continue

    validator = Draft202012Validator(schema)
    errors = list(validator.iter_errors(VALID))
    if errors:
        for err in errors:
            print(f"::error:: valid payload rejected: {err.message}", file=sys.stderr)
        status = 1
    else:
        print(f"[schema] {schema_path.name}: well-formed payload accepted")

    if any(validator.iter_errors(INVALID)):
        print(f"[schema] {schema_path.name}: unregistered action rejected")
    else:
        print(f"::error:: {schema_path.name}: unregistered action was NOT rejected", file=sys.stderr)
        status = 1

sys.exit(status)
