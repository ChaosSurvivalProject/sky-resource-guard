#!/usr/bin/env bash
# Lightweight equivalent of Spotless/Checkstyle for P0 (task book 18.4).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
status=0

echo "[style] trailing whitespace in source/config files"
if grep -rIn --include='*.java' --include='*.kts' --include='*.yml' --include='*.yaml' \
    --include='*.py' --include='*.sh' -E '[[:blank:]]+$' . \
    --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git; then
    echo "::error:: trailing whitespace found"
    status=1
fi

echo "[style] tab indentation in Java/Kotlin sources"
if grep -rIn --include='*.java' --include='*.kts' -P '^\t' . \
    --exclude-dir=build --exclude-dir=.gradle; then
    echo "::error:: tab indentation found"
    status=1
fi

echo "[style] final newline"
while IFS= read -r -d '' file; do
    [ -s "$file" ] || continue
    if [ "$(tail -c1 "$file" | wc -l)" -ne 1 ]; then
        echo "::error:: missing final newline: $file"
        status=1
    fi
done < <(find . -type f \( -name '*.java' -o -name '*.kts' -o -name '*.md' \
    -o -name '*.yml' -o -name '*.json' -o -name '*.sh' -o -name '*.py' \) \
    -not -path '*/build/*' -not -path './.git/*' -print0)

if [ "$status" -eq 0 ]; then
    echo "[style] OK"
fi
exit "$status"
