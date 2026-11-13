/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic, fixed-seed tick replay (task book 15.4). No robot behaviour is
 * presented as a real player experience; this only drives pure-Java metrics.
 */
public final class TickReplay {

    private TickReplay() {}

    public static List<Double> msptSeries(long seed, int ticks, double baseMspt, double spikeEvery) {
        Random random = new Random(seed);
        List<Double> out = new ArrayList<>(ticks);
        for (int i = 0; i < ticks; i++) {
            double noise = random.nextGaussian() * 2.0;
            double spike = (spikeEvery > 0 && i % (int) spikeEvery == 0) ? 45.0 : 0.0;
            out.add(Math.max(1.0, baseMspt + noise + spike));
        }
        return out;
    }

}
