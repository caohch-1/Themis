package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ViolationType;

import java.util.ArrayList;
import java.util.List;

public class UrPatternMatcher implements ImplicitPatternMatcher {
    @Override
    public List<SymptomCandidate> match(ViolationTuple tuple) {
        List<SymptomCandidate> hits = new ArrayList<>();
        if (tuple.getType() != ViolationType.ORDER) {
            return hits;
        }
        for (AccessSite site : tuple.getAccessSites()) {
            String s = site.getStatement().toLowerCase();
            if (s.contains("init") || s.contains("start") || s.contains("ready") || s.contains("null")) {
                hits.add(new SymptomCandidate(tuple.getId() + "-ur-" + site.getId(), SymptomPattern.UR, SymptomKind.NULL_DEREFERENCE, site.getStatement(), tuple.getEnclosingFunctions(), 0));
            }
        }
        return hits;
    }
}
