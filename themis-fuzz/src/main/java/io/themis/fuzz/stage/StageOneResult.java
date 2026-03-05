package io.themis.fuzz.stage;

import io.themis.core.model.ParameterSeed;
import io.themis.core.model.SeedPoolState;

import java.util.ArrayList;
import java.util.List;

public class StageOneResult {
    private final boolean reachedTargets;
    private final ParameterSeed reachedSeed;
    private final SeedPoolState seedPoolState;
    private final List<String> reachedTrace;

    public StageOneResult(boolean reachedTargets,
                          ParameterSeed reachedSeed,
                          SeedPoolState seedPoolState,
                          List<String> reachedTrace) {
        this.reachedTargets = reachedTargets;
        this.reachedSeed = reachedSeed;
        this.seedPoolState = seedPoolState;
        this.reachedTrace = reachedTrace == null ? new ArrayList<String>() : new ArrayList<String>(reachedTrace);
    }

    public boolean isReachedTargets() {
        return reachedTargets;
    }

    public ParameterSeed getReachedSeed() {
        return reachedSeed;
    }

    public SeedPoolState getSeedPoolState() {
        return seedPoolState;
    }

    public List<String> getReachedTrace() {
        return new ArrayList<String>(reachedTrace);
    }
}
