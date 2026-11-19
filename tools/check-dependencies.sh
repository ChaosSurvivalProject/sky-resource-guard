#!/usr/bin/env bash
# Dependency licence / provenance check (task book 18.1, 18.4).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "[deps] auditing externally declared coordinates"
coords=$(grep -rhoE '"[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+"' \
    --include='*.gradle.kts' . 2>/dev/null | tr -d '"' | sort -u || true)

if [ -z "$coords" ]; then
    echo "::error:: no external coordinates found; audit may be broken"
    exit 1
fi

while IFS= read -r coord; do
    case "$coord" in
        org.junit.jupiter:junit-jupiter:*)
            echo "  approved (EPL-2.0, test only): $coord" ;;
        io.papermc.paper:paper-api:*)
            echo "  approved (compileOnly, NOT redistributed; see version-matrix.md): $coord" ;;
        org.gradle:*|dev.skyguard:*)
            echo "  approved (build/internal): $coord" ;;
        *)
            echo "::error:: unapproved dependency, add a licence review to docs/version-matrix.md: $coord"
            exit 1 ;;
    esac
done <<< "$coords"

grep -q 'JUnit Jupiter' docs/version-matrix.md || {
    echo "::error:: dependencies are not recorded in docs/version-matrix.md"
    exit 1
}
grep -q 'io.papermc.paper:paper-api' docs/version-matrix.md || {
    echo "::error:: paper-api coordinate is not recorded in docs/version-matrix.md"
    exit 1
}
echo "[deps] OK - every external dependency is documented and licence-reviewed"
