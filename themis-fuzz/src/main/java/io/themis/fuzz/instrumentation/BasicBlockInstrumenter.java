package io.themis.fuzz.instrumentation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BasicBlockInstrumenter {
    private final ProbeCatalog catalog = new ProbeCatalog();

    public List<String> instrument(List<String> plannedTargets) {
        Set<String> probes = new LinkedHashSet<String>();
        for (String target : plannedTargets) {
            probes.add(catalog.probeIdForTarget(target));
        }
        return new ArrayList<String>(probes);
    }

    public boolean outOfInstrumentationRange(String statement, List<String> probes) {
        return !probes.contains(catalog.probeIdForTarget(statement));
    }

    public String probeIdFor(String target) {
        return catalog.probeIdForTarget(target);
    }

    public ProbeCatalog getCatalog() {
        return catalog;
    }
}
