package io.themis.fuzz.check;

import io.themis.core.model.InterleavingShape;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BehaviorChecker {
    public boolean manifestsOnlyUnderBuggyInterleaving(ViolationType type,
                                                        List<BehaviorObservation> observations,
                                                        InterleavingShape buggyShape) {
        if (observations == null || observations.isEmpty()) {
            return false;
        }
        Map<InterleavingShape, Boolean> hasSymptom = new HashMap<>();
        for (BehaviorObservation observation : observations) {
            hasSymptom.put(observation.getShape(), !observation.getSymptoms().isEmpty());
        }
        if (type == ViolationType.ORDER) {
            for (Map.Entry<InterleavingShape, Boolean> entry : hasSymptom.entrySet()) {
                if (entry.getKey() == buggyShape && !entry.getValue()) {
                    return false;
                }
                if (entry.getKey() != buggyShape && entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<InterleavingShape, Boolean> entry : hasSymptom.entrySet()) {
            if (entry.getKey() == buggyShape && !entry.getValue()) {
                return false;
            }
            if (entry.getKey() != buggyShape && entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean onlyThreadUnsafeEvidence(List<BehaviorObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return false;
        }
        boolean seenSymptom = false;
        for (BehaviorObservation observation : observations) {
            for (SymptomCandidate candidate : observation.getSymptoms()) {
                seenSymptom = true;
                if (candidate.getPattern() != SymptomPattern.TO) {
                    return false;
                }
            }
        }
        return seenSymptom;
    }

    public List<SymptomCandidate> flattenSymptoms(List<BehaviorObservation> observations) {
        List<SymptomCandidate> out = new ArrayList<>();
        for (BehaviorObservation observation : observations) {
            out.addAll(observation.getSymptoms());
        }
        return out;
    }
}
