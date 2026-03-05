package io.themis.fuzz.check;

import io.themis.core.model.InterleavingShape;
import io.themis.core.model.SymptomCandidate;

import java.util.ArrayList;
import java.util.List;

public class BehaviorObservation {
    private final InterleavingShape shape;
    private final List<SymptomCandidate> symptoms;

    public BehaviorObservation(InterleavingShape shape, List<SymptomCandidate> symptoms) {
        this.shape = shape;
        this.symptoms = symptoms == null ? new ArrayList<>() : new ArrayList<>(symptoms);
    }

    public InterleavingShape getShape() {
        return shape;
    }

    public List<SymptomCandidate> getSymptoms() {
        return new ArrayList<>(symptoms);
    }
}
