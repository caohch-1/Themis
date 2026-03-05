package io.themis.fuzz.engine;

import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.List;

public class FuzzExecutionResult {
    private final List<String> trace;
    private final boolean reachedTargets;
    private final ParameterSeed seed;

    public FuzzExecutionResult(List<String> trace, boolean reachedTargets, ParameterSeed seed) {
        this.trace = trace == null ? new ArrayList<>() : new ArrayList<>(trace);
        this.reachedTargets = reachedTargets;
        this.seed = seed;
    }

    public List<String> getTrace() {
        return new ArrayList<>(trace);
    }

    public boolean isReachedTargets() {
        return reachedTargets;
    }

    public ParameterSeed getSeed() {
        return seed;
    }
}
