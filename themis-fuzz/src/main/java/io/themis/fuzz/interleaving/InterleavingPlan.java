package io.themis.fuzz.interleaving;

import io.themis.core.model.InterleavingShape;

import java.util.ArrayList;
import java.util.List;

public class InterleavingPlan {
    private final InterleavingShape shape;
    private final List<String> sequence;

    public InterleavingPlan(InterleavingShape shape, List<String> sequence) {
        this.shape = shape;
        this.sequence = sequence == null ? new ArrayList<>() : new ArrayList<>(sequence);
    }

    public InterleavingShape getShape() {
        return shape;
    }

    public List<String> getSequence() {
        return new ArrayList<>(sequence);
    }
}
