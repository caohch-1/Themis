package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayList;
import java.util.List;

public class MrPatternMatcher implements ImplicitPatternMatcher {
    @Override
    public List<SymptomCandidate> match(ViolationTuple tuple) {
        List<SymptomCandidate> hits = new ArrayList<>();
        for (AccessSite site : tuple.getAccessSites()) {
            String s = site.getStatement().toLowerCase();
            if (s.contains("get(") || s.contains("remove(") || s.contains("containskey") || s.contains("map")) {
                hits.add(new SymptomCandidate(tuple.getId() + "-mr-" + site.getId(), SymptomPattern.MR, SymptomKind.NULL_DEREFERENCE, site.getStatement(), tuple.getEnclosingFunctions(), 0));
            }
        }
        return hits;
    }
}
