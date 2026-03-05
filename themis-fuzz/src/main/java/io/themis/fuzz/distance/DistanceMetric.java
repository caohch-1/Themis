package io.themis.fuzz.distance;

import io.themis.fuzz.instrumentation.ProbeCatalog;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DistanceMetric {
    private final ProbeCatalog catalog = new ProbeCatalog();

    public long distance(List<String> trace, List<String> targets) {
        if (trace == null || trace.isEmpty()) {
            return Long.MAX_VALUE;
        }
        Set<String> targetProbes = targetProbes(targets);
        if (targetProbes.isEmpty()) {
            return Long.MAX_VALUE;
        }
        Map<String, Integer> firstSeen = new HashMap<String, Integer>();
        int probeIndex = 0;
        for (String event : trace) {
            String probeId = probeIdOf(event);
            if (probeId.isEmpty()) {
                continue;
            }
            firstSeen.putIfAbsent(probeId, probeIndex++);
        }
        long best = Long.MAX_VALUE;
        for (String target : targetProbes) {
            Integer idx = firstSeen.get(target);
            if (idx != null) {
                best = Math.min(best, idx);
            }
        }
        return best;
    }

    public long infiniteDistance() {
        return Long.MAX_VALUE;
    }

    private Set<String> targetProbes(List<String> targets) {
        Set<String> targetProbes = new LinkedHashSet<String>();
        if (targets == null) {
            return targetProbes;
        }
        for (String target : targets) {
            if (catalog.isProbeId(target)) {
                targetProbes.add(target);
            }
        }
        return targetProbes;
    }

    private String probeIdOf(String event) {
        if (event == null) {
            return "";
        }
        if (event.startsWith("PROBE:")) {
            String value = event.substring("PROBE:".length()).trim();
            return catalog.isProbeId(value) ? value : "";
        }
        if (catalog.isProbeId(event)) {
            return event;
        }
        return catalog.parseProbeIdFromOutput(event);
    }
}
