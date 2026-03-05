package io.themis.staticanalysis.symptom;

import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.ViolationTuple;

import java.util.List;

public interface ImplicitPatternMatcher {
    List<SymptomCandidate> match(ViolationTuple tuple);
}
