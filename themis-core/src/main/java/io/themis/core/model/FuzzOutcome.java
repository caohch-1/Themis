package io.themis.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FuzzOutcome {
    private final String violationId;
    private final boolean exposed;
    private final FuzzStage reachedStage;
    private final List<String> reachedTargets;
    private final List<InterleavingShape> exercisedInterleavings;
    private final List<SymptomCandidate> manifestedSymptoms;
    private final SeedPoolState finalSeedPool;
    private final Map<String, String> metadata;

    public FuzzOutcome(String violationId,
                       boolean exposed,
                       FuzzStage reachedStage,
                       List<String> reachedTargets,
                       List<InterleavingShape> exercisedInterleavings,
                       List<SymptomCandidate> manifestedSymptoms,
                       SeedPoolState finalSeedPool,
                       Map<String, String> metadata) {
        this.violationId = violationId;
        this.exposed = exposed;
        this.reachedStage = reachedStage;
        this.reachedTargets = reachedTargets == null ? new ArrayList<>() : new ArrayList<>(reachedTargets);
        this.exercisedInterleavings = exercisedInterleavings == null ? new ArrayList<>() : new ArrayList<>(exercisedInterleavings);
        this.manifestedSymptoms = manifestedSymptoms == null ? new ArrayList<>() : new ArrayList<>(manifestedSymptoms);
        this.finalSeedPool = finalSeedPool;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getViolationId() {
        return violationId;
    }

    public boolean isExposed() {
        return exposed;
    }

    public FuzzStage getReachedStage() {
        return reachedStage;
    }

    public List<String> getReachedTargets() {
        return new ArrayList<>(reachedTargets);
    }

    public List<InterleavingShape> getExercisedInterleavings() {
        return new ArrayList<>(exercisedInterleavings);
    }

    public List<SymptomCandidate> getManifestedSymptoms() {
        return new ArrayList<>(manifestedSymptoms);
    }

    public SeedPoolState getFinalSeedPool() {
        return finalSeedPool;
    }

    public Map<String, String> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FuzzOutcome)) {
            return false;
        }
        FuzzOutcome that = (FuzzOutcome) o;
        return exposed == that.exposed
            && Objects.equals(violationId, that.violationId)
            && reachedStage == that.reachedStage
            && Objects.equals(reachedTargets, that.reachedTargets)
            && Objects.equals(exercisedInterleavings, that.exercisedInterleavings)
            && Objects.equals(manifestedSymptoms, that.manifestedSymptoms)
            && Objects.equals(finalSeedPool, that.finalSeedPool)
            && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(violationId, exposed, reachedStage, reachedTargets, exercisedInterleavings, manifestedSymptoms, finalSeedPool, metadata);
    }
}
