package io.themis.fuzz.stage;

import io.themis.core.model.SymptomCandidate;

import java.util.ArrayList;
import java.util.List;

public class InterleavingRuntimeResult {
    private final List<String> trace;
    private final List<SymptomCandidate> observedSymptoms;
    private final boolean reachedPlannedInterleaving;

    public InterleavingRuntimeResult(List<String> trace,
                                     List<SymptomCandidate> observedSymptoms,
                                     boolean reachedPlannedInterleaving) {
        this.trace = trace == null ? new ArrayList<String>() : new ArrayList<String>(trace);
        this.observedSymptoms = observedSymptoms == null ? new ArrayList<SymptomCandidate>() : new ArrayList<SymptomCandidate>(observedSymptoms);
        this.reachedPlannedInterleaving = reachedPlannedInterleaving;
    }

    public List<String> getTrace() {
        return new ArrayList<String>(trace);
    }

    public List<SymptomCandidate> getObservedSymptoms() {
        return new ArrayList<SymptomCandidate>(observedSymptoms);
    }

    public boolean isReachedPlannedInterleaving() {
        return reachedPlannedInterleaving;
    }
}
