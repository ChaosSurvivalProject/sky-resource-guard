#!/usr/bin/env bash
# Prove auto-adaptation on the INSTALLABLE jar (CI step, task book 18.4).
#
# This is the executable evidence for "code produced by the AI Skill adapts
# automatically": it loads the fat jar exactly as a server would and asks
# java.util.ServiceLoader for ResourceParticipant providers. A generated
# OBSERVE_ONLY adapter must be found and must report no side effects.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR=$(find "$ROOT/sky-plugin-paper/build/libs" -name 'SKYResourceGuard-*.jar' -type f 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
    echo "::error:: plugin jar not found; run ./gradlew build first"
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=$(command -v javac)
    JAVA=$(command -v java)
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/Probe.java" <<'EOF'
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.ResourceParticipant;
import java.util.ServiceLoader;

public class Probe {
    public static void main(String[] args) {
        int n = 0;
        for (ResourceParticipant p : ServiceLoader.load(ResourceParticipant.class)) {
            var m = p.metadata();
            var s = p.sample(SampleBudget.strict());
            System.out.println("FOUND id=" + m.pluginId() + " version=" + m.version()
                    + " caps=" + p.capabilities().size()
                    + " healthy=" + s.healthy() + " sideEffects=" + s.sideEffects().size());
            n++;
        }
        System.out.println("TOTAL=" + n);
        if (n == 0) {
            throw new IllegalStateException("no participant auto-discovered");
        }
    }
}
EOF

"$JAVAC" -cp "$JAR" -d "$TMP" "$TMP/Probe.java"
OUT=$("$JAVA" -cp "$JAR:$TMP" Probe)
echo "$OUT"

for expected in 'FOUND id=acme-quests' 'caps=4' 'sideEffects=0' 'TOTAL=1'; do
    if [[ "$OUT" != *"$expected"* ]]; then
        echo "::error:: auto-discovery probe missing: $expected"
        exit 1
    fi
done
echo "[autodiscovery] OK - generated adapter is auto-registered and read-only in the installable jar"
