#!/usr/bin/env bash
# Security-sensitive pattern scan (task book 18.2, 18.4).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "[security] forbidden runtime APIs in production Java sources"
patterns=('Thread\.stop' 'Thread\.suspend' 'Thread\.resume' 'System\.gc\(' \
          'setAccessible\(' 'Runtime\.getRuntime\(\)\.exec')
for pattern in "${patterns[@]}"; do
    if grep -rInE --include='*.java' "$pattern" . \
        --exclude-dir=build --exclude-dir=.gradle --exclude-dir=test \
        | grep -v '/src/test/'; then
        echo "::error:: forbidden API usage: $pattern"
        exit 1
    fi
done

echo "[security] credential-shaped literals"
# Exclude tools/ so this script's own patterns are not self-matched.
if grep -rInE --include='*.java' --include='*.kts' --include='*.yml' \
    --include='*.yaml' --include='*.md' --include='*.json' --include='*.py' \
    --include='*.sh' 'ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}' . \
    --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git --exclude-dir=tools; then
    echo "::error:: credential-looking literal found; remove it before committing"
    exit 1
fi

echo "[security] no real logs / world data / player data"
if find . -type f \( -name '*.log' -o -name '*.nbt' -o -name '*.hprof' \
    -o -name 'thread-dump*.txt' -o -name 'diagnostic-*.zip' \) \
    -not -path '*/build/*' -not -path './.git/*' | grep -q .; then
    echo "::error:: runtime artefact found in the repository"
    exit 1
fi

echo "[security] OK"
