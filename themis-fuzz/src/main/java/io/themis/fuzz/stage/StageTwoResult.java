package io.themis.fuzz.stage;

import io.themis.fuzz.check.BehaviorObservation;
import io.themis.fuzz.interleaving.InterleavingPlan;

import java.util.ArrayList;
import java.util.List;

public class StageTwoResult {
    private final boolean allInterleavingsReached;
    private final boolean exposedInStageTwo;
    private final List<BehaviorObservation> observations;
    private final List<InterleavingPlan> feasiblePlans;

    public StageTwoResult(boolean allInterleavingsReached,
                          boolean exposedInStageTwo,
                          List<BehaviorObservation> observations,
                          List<InterleavingPlan> feasiblePlans) {
        this.allInterleavingsReached = allInterleavingsReached;
        this.exposedInStageTwo = exposedInStageTwo;
        this.observations = observations == null ? new ArrayList<>() : new ArrayList<>(observations);
        this.feasiblePlans = feasiblePlans == null ? new ArrayList<>() : new ArrayList<>(feasiblePlans);
    }

    public boolean isAllInterleavingsReached() {
        return allInterleavingsReached;
    }

    public boolean isExposedInStageTwo() {
        return exposedInStageTwo;
    }

    public List<BehaviorObservation> getObservations() {
        return new ArrayList<>(observations);
    }

    public List<InterleavingPlan> getFeasiblePlans() {
        return new ArrayList<>(feasiblePlans);
    }
}
