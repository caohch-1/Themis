package io.themis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SeedPoolState {
    private final String violationId;
    private final FuzzStage stage;
    private final int iteration;
    private final List<ParameterSeed> seeds;
    private final long bestDistance;

    public SeedPoolState(String violationId, FuzzStage stage, int iteration, List<ParameterSeed> seeds, long bestDistance) {
        this.violationId = violationId;
        this.stage = stage;
        this.iteration = iteration;
        this.seeds = seeds == null ? new ArrayList<>() : new ArrayList<>(seeds);
        this.bestDistance = bestDistance;
    }

    public String getViolationId() {
        return violationId;
    }

    public FuzzStage getStage() {
        return stage;
    }

    public int getIteration() {
        return iteration;
    }

    public List<ParameterSeed> getSeeds() {
        return new ArrayList<>(seeds);
    }

    public long getBestDistance() {
        return bestDistance;
    }

    public SeedPoolState next(FuzzStage nextStage, int nextIteration, List<ParameterSeed> nextSeeds, long nextBestDistance) {
        return new SeedPoolState(violationId, nextStage, nextIteration, nextSeeds, nextBestDistance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeedPoolState)) {
            return false;
        }
        SeedPoolState that = (SeedPoolState) o;
        return iteration == that.iteration
            && bestDistance == that.bestDistance
            && Objects.equals(violationId, that.violationId)
            && stage == that.stage
            && Objects.equals(seeds, that.seeds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(violationId, stage, iteration, seeds, bestDistance);
    }
}
