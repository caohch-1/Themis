package io.themis.fuzz.interleaving;

import io.themis.fuzz.instrumentation.ProbeCatalog;

import java.util.ArrayList;
import java.util.List;

public class InterleavingFeasibilityPruner {
    private final ProbeCatalog probeCatalog = new ProbeCatalog();

    public List<InterleavingPlan> pruneUnreachable(List<InterleavingPlan> plans, List<String> trace) {
        List<InterleavingPlan> feasible = new ArrayList<>();
        for (InterleavingPlan plan : plans) {
            if (reachable(plan, trace)) {
                feasible.add(plan);
            }
        }
        return feasible;
    }

    private boolean reachable(InterleavingPlan plan, List<String> trace) {
        int idx = 0;
        for (String event : trace) {
            String probe = probeId(event);
            if (probe.isEmpty()) {
                continue;
            }
            if (idx < plan.getSequence().size() && probe.equals(plan.getSequence().get(idx))) {
                idx++;
            }
        }
        return idx == plan.getSequence().size();
    }

    private String probeId(String event) {
        if (event == null) {
            return "";
        }
        if (event.startsWith("PROBE:")) {
            String value = event.substring("PROBE:".length()).trim();
            return probeCatalog.isProbeId(value) ? value : "";
        }
        if (probeCatalog.isProbeId(event)) {
            return event;
        }
        return probeCatalog.parseProbeIdFromOutput(event);
    }
}
