#!/usr/bin/env bash
# Verify the installable plugin artefact (CI step, task book 18.4).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
status=0

JAR=$(find "$ROOT/sky-plugin-paper/build/libs" -name 'SKYResourceGuard-*.jar' -type f 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
    echo "::error:: plugin jar not found; run ./gradlew build first"
    exit 1
fi
echo "[plugin-jar] checking $(basename "$JAR")"

# Capture the listing ONCE. Piping `unzip -l` into `grep -q` would make grep
# exit early, send SIGPIPE to unzip, and fail the check under pipefail.
LISTING=$(unzip -l "$JAR")

expect() {
    if [[ "$LISTING" == *"$1"* ]]; then
        echo "[plugin-jar] present: $1"
    else
        echo "::error:: missing from plugin jar: $1"
        status=1
    fi
}

expect 'plugin.yml'
expect 'config.yml'
expect 'META-INF/services/dev.skyguard.api.ResourceParticipant'
expect 'dev/skyguard/plugin/SkyPlugin.class'
expect 'dev/skyguard/core/GuardRuntime.class'
expect 'dev/skyguard/adapter/acmequests/AcmeQuestsParticipant.class'

echo "[plugin-jar] server API must be compileOnly, never bundled"
if [[ "$LISTING" =~ org/bukkit/|io/papermc/|org/spigotmc/|net/minecraft/ ]]; then
    echo "::error:: server API classes were bundled; paper-api must stay compileOnly"
    status=1
else
    echo "[plugin-jar] OK: no server API classes in the jar"
fi

echo "[plugin-jar] auto-registration content"
SERVICES=$(unzip -p "$JAR" META-INF/services/dev.skyguard.api.ResourceParticipant)
if [[ "$SERVICES" == *"dev.skyguard.adapter.acmequests.AcmeQuestsParticipant"* ]]; then
    echo "[plugin-jar] OK: generated adapter is registered for ServiceLoader"
else
    echo "::error:: generated adapter is not registered in the jar's services file"
    status=1
fi

if [ "$status" -eq 0 ]; then
    echo "[plugin-jar] OK - installable fat jar verified"
fi
exit "$status"
