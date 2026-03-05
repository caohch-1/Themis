package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayList;
import java.util.List;

public class ObPatternMatcher implements ImplicitPatternMatcher {
    @Override
    public List<SymptomCandidate> match(ViolationTuple tuple) {
        List<SymptomCandidate> hits = new ArrayList<>();
        for (AccessSite site : tuple.getAccessSites()) {
            String s = site.getStatement().toLowerCase();
            if (s.contains("[") && s.contains("]") || s.contains("array") || s.contains("index")) {
                hits.add(new SymptomCandidate(tuple.getId() + "-ob-" + site.getId(), SymptomPattern.OB, SymptomKind.OUT_OF_BOUNDS, site.getStatement(), tuple.getEnclosingFunctions(), 0));
            }
        }
        return hits;
    }
}
