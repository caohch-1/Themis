package io.themis.staticanalysis.publicapi;

import io.themis.core.model.PublicInterfacePair;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayList;
import java.util.List;

public class EscapabilityPruner {
    public List<ViolationTuple> prune(List<ViolationTuple> tuples) {
        List<ViolationTuple> kept = new ArrayList<>();
        for (ViolationTuple tuple : tuples) {
            PublicInterfacePair pair = tuple.getPublicInterfacePair();
            if (pair == null) {
                continue;
            }
            if (!pair.isComplete()) {
                continue;
            }
            kept.add(tuple);
        }
        return kept;
    }
}
