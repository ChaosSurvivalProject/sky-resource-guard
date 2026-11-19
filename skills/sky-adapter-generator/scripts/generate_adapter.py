#!/usr/bin/env python3
"""Deterministic SKY adapter generator (AI Skill, P1.1 observe-only).

Turns a factual adapter spec into a complete Gradle module:

  build.gradle.kts
  src/main/java/.../<ClassName>.java           (ResourceParticipant)
  src/main/resources/META-INF/services/...    (ServiceLoader registration)
  src/test/java/.../<ClassName>Test.java      (contract + auto-discovery tests)
  GENERATED.md

The generator refuses to emit an adapter that claims an active capability,
because P1.1 adapters are OBSERVE_ONLY and AI-produced code must never be able
to mutate a live server on its own.
"""
import argparse
import difflib
import json
import pathlib
import sys
import tempfile

SCRIPT = pathlib.Path(__file__).resolve()
SKILL_DIR = SCRIPT.parents[1]
ROOT = SCRIPT.parents[3]
TEMPLATES = SKILL_DIR / "templates"
SCHEMA_PATH = SKILL_DIR / "schemas" / "adapter-spec.schema.json"

# Only read/observe capabilities are permissible for a generated adapter.
OBSERVATION_CAPS = frozenset(
    {"HEALTH_CHECK", "QUEUE_REPORT", "CACHE_REPORT", "LEAK_DETECTION", "DRY_RUN"}
)
ACTIVE_CAPS = frozenset(
    {"LOWER_POWER", "CHECKPOINT", "QUIESCE", "RESUME",
     "RELEASE_RESOURCES", "EXECUTOR_SHUTDOWN"}
)
REQUIRED = (
    "pluginId", "version", "packageName", "className", "serviceTier",
    "executionPhase", "faultDomain", "capabilities", "observeOnly", "sampling",
)


def repo_rel(path):
    resolved = pathlib.Path(path).resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.as_posix()


def manual_validate(spec):
    """Minimal fallback when jsonschema is unavailable (keeps CI hermetic)."""
    problems = []
    for key in REQUIRED:
        if key not in spec:
            problems.append("missing required field: " + key)
    if spec.get("observeOnly") is not True:
        problems.append("observeOnly must be true (P1.1 is read-only)")
    for key in ("serviceTier", "executionPhase", "capabilities", "sampling"):
        if key in spec and not isinstance(spec[key], (list, dict, str)):
            problems.append("invalid type for " + key)
    for key in ("initialQueueDepth", "initialHeapBytes", "stage"):
        if isinstance(spec.get("sampling"), dict) and key not in spec["sampling"]:
            problems.append("sampling." + key + " is required")
    return problems


def validate(spec):
    problems = []
    try:
        from jsonschema import Draft202012Validator
    except ImportError:
        return manual_validate(spec)
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    for err in Draft202012Validator(schema).iter_errors(spec):
        location = "/".join(str(p) for p in err.absolute_path) or "(root)"
        problems.append(location + ": " + err.message)
    return problems


def business_rules(spec):
    """Extra safety checks beyond the JSON Schema."""
    problems = []
    caps = spec.get("capabilities", [])
    if spec.get("observeOnly") is not True:
        problems.append("observeOnly must be true; generated code is never allowed to mutate")
    for cap in caps:
        if cap in ACTIVE_CAPS:
            problems.append("capability " + cap + " is an ACTIVE capability; "
                            "an OBSERVE_ONLY generated adapter must not claim it")
        elif cap not in OBSERVATION_CAPS:
            problems.append("unknown capability: " + cap)
    if len(set(caps)) != len(caps):
        problems.append("capabilities contain duplicates")
    if spec.get("serviceTier") == "CORE":
        problems.append("CORE tier needs human review; generate as BACKGROUND first")
    return problems


def render(template_name, mapping):
    text = (TEMPLATES / template_name).read_text(encoding="utf-8")
    for key, value in mapping.items():
        text = text.replace("{{" + key + "}}", str(value))
    if "{{" in text:
        raise RuntimeError("unresolved placeholder in " + template_name)
    # Exactly one trailing newline, no trailing blanks: check-style.sh enforces this.
    return text.rstrip("\n") + "\n"


def build_mapping(spec, spec_file, module_label):
    caps = spec["capabilities"]
    deps = spec.get("dependencies", [])
    sampling = spec["sampling"]
    cap_expr = ", ".join("Capability." + c for c in caps)
    return {
        "SPEC_FILE": repo_rel(spec_file),
        "MODULE_DIR": module_label,
        "PACKAGE": spec["packageName"],
        "CLASS": spec["className"],
        "PLUGIN_ID": spec["pluginId"],
        "PLUGIN_NAME": spec.get("pluginName", spec["pluginId"]),
        "VERSION": spec["version"],
        "TIER": spec["serviceTier"],
        "PHASE": spec["executionPhase"],
        "FAULT": spec["faultDomain"],
        "QUEUE": sampling["initialQueueDepth"],
        "HEAP": sampling["initialHeapBytes"],
        "STAGE": sampling["stage"],
        "DEPS": ", ".join(json.dumps(d) for d in deps),
        "N_DEPS": len(deps),
        "CAPS": cap_expr,
        "CAPS_ASSERT": cap_expr,
        "N_CAPS": len(caps),
        "CAPS_RAW": ", ".join(caps) if caps else "(none)",
    }


def rendered_files(spec, spec_file, module_label):
    mapping = build_mapping(spec, spec_file, module_label)
    pkg = spec["packageName"].replace(".", "/")
    cls = spec["className"]
    return {
        "build.gradle.kts": render("build.gradle.kts.tpl", mapping),
        "src/main/java/" + pkg + "/" + cls + ".java":
            render("participant.java.tpl", mapping),
        "src/main/resources/META-INF/services/dev.skyguard.api.ResourceParticipant":
            render("services.tpl", mapping),
        "src/test/java/" + pkg + "/" + cls + "Test.java":
            render("participant_test.java.tpl", mapping),
        "GENERATED.md": render("GENERATED.md.tpl", mapping),
    }


def write_all(target, files):
    for rel, content in files.items():
        path = target / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description="Generate a SKY OBSERVE_ONLY adapter module")
    ap.add_argument("--spec", required=True, type=pathlib.Path, help="adapter spec JSON")
    ap.add_argument("--out", required=True, type=pathlib.Path, help="output module directory")
    ap.add_argument("--module-label", default=None,
                    help="repo-relative module path used inside GENERATED.md")
    ap.add_argument("--check", action="store_true",
                    help="regenerate into a temp dir and diff against --out")
    args = ap.parse_args()

    if not args.spec.is_file():
        print("::error:: spec not found: " + str(args.spec), file=sys.stderr)
        return 2
    spec = json.loads(args.spec.read_text(encoding="utf-8"))
    problems = validate(spec) + business_rules(spec)
    if problems:
        for p in problems:
            print("::error:: " + p, file=sys.stderr)
        return 2

    label = args.module_label or "adapters/sky-adapter-generated-" + spec["pluginId"]
    files = rendered_files(spec, args.spec, label)

    if args.check:
        if not args.out.is_dir():
            print("::error:: --out is not a directory: " + str(args.out), file=sys.stderr)
            return 2
        with tempfile.TemporaryDirectory(prefix="sky-gen-") as tmp:
            write_all(pathlib.Path(tmp), files)
            bad = False
            for rel, expected in files.items():
                actual_path = args.out / rel
                if not actual_path.is_file():
                    print("::error:: generated file missing: " + rel, file=sys.stderr)
                    bad = True
                    continue
                actual = actual_path.read_text(encoding="utf-8")
                if actual != expected:
                    diff = difflib.unified_diff(
                        actual.splitlines(), expected.splitlines(),
                        fromfile=rel + " (committed)", tofile=rel + " (regenerated)",
                        lineterm="",
                    )
                    print("\n".join(diff), file=sys.stderr)
                    print("::error:: generated artefact is stale: " + rel, file=sys.stderr)
                    bad = True
            if bad:
                return 1
        print("[generated] OK - " + str(len(files)) + " files match the spec and templates")
        return 0

    write_all(args.out, files)
    print("[generated] wrote " + str(len(files)) + " files to " + str(args.out))
    print("[generated] ServiceLoader entry: " + spec["packageName"] + "."
          + spec["className"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
