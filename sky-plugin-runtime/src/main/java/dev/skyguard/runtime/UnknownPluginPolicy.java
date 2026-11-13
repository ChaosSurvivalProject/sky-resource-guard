/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.runtime;

import dev.skyguard.api.EvidenceLevel;
import java.util.ArrayList;
import java.util.List;

/**
 * Default policy for unadapted / unknown plugins (task book 5).
 *
 * <p><strong>Observable does not mean attributable; having a restriction hook
 * does not mean a plugin can be safely frozen.</strong> Unknown plugins default
 * to OBSERVE_ONLY. Only L0/L1 evidence may trigger automatic restriction, L2
 * is warning-only, L3 enters investigation suggestions. A high-privilege-looking
 * plugin name never grants a bypass.</p>
 */
public final class UnknownPluginPolicy {

    public enum Mode { OBSERVE_ONLY, LIMITED, ADMIN_DISABLE }

    public record Decision(
            Mode mode, boolean mayAutoRestrict, boolean mayForceThreadStop,
            EvidenceLevel evidence, List<String> reasons) {}

    private static final List<String> PRIVILEGED_LOOKING = List.of("admin", "op", "root", "sky", "system");

    private Mode defaultMode = Mode.OBSERVE_ONLY;

    public synchronized Mode defaultMode() {
        return defaultMode;
    }

    public Decision evaluate(
            String pluginId, EvidenceLevel evidence,
            boolean adapterInstalled, boolean adminAuthorised) {
        List<String> reasons = new ArrayList<>();
        reasons.add("default=" + defaultMode);
        reasons.add("evidence=" + evidence);

        if (looksPrivileged(pluginId)) {
            reasons.add("high-privilege-looking name is NOT a policy bypass");
        }

        if (evidence == null || evidence.warningOnly()) {
            reasons.add("L2/L3: warning or investigation only, never automatic restriction");
            return new Decision(Mode.OBSERVE_ONLY, false, false, evidence, List.copyOf(reasons));
        }
        if (!adapterInstalled) {
            reasons.add("no explicit adapter: read-only observation only");
            return new Decision(Mode.OBSERVE_ONLY, false, false, evidence, List.copyOf(reasons));
        }
        if (adminAuthorised) {
            reasons.add("administrator authorised; dependency check, backup and recovery steps required");
            return new Decision(Mode.ADMIN_DISABLE, false, false, evidence, List.copyOf(reasons));
        }
        reasons.add("L0/L1 + adapter: limited, cooperative restriction only");
        return new Decision(Mode.LIMITED, true, false, evidence, List.copyOf(reasons));
    }

    /** Kept as an explicit constant: SKY never force-stops unknown threads. */
    public boolean mayForceThreadStop() {
        return false;
    }

    public static boolean looksPrivileged(String pluginId) {
        if (pluginId == null) {
            return false;
        }
        String lower = pluginId.toLowerCase();
        return PRIVILEGED_LOOKING.stream().anyMatch(lower::contains);
    }
}
