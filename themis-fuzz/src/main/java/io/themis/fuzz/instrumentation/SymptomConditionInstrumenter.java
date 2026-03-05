package io.themis.fuzz.instrumentation;

import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;

import java.util.ArrayList;
import java.util.List;

public class SymptomConditionInstrumenter {
    public List<String> synthesizeTargets(List<SymptomCandidate> candidates) {
        List<String> targets = new ArrayList<>();
        for (SymptomCandidate candidate : candidates) {
            if (candidate.getKind() == SymptomKind.NULL_DEREFERENCE) {
                targets.add("if(" + normalizedVar(candidate.getStatement()) + "==null)");
            } else if (candidate.getKind() == SymptomKind.OUT_OF_BOUNDS) {
                String var = normalizedVar(candidate.getStatement());
                targets.add("if(index<0||index>=" + var + ".length)");
            } else {
                targets.add(candidate.getStatement());
            }
        }
        return targets;
    }

    private String normalizedVar(String statement) {
        String cleaned = statement.replaceAll("[^A-Za-z0-9_]", " ").trim();
        if (cleaned.isEmpty()) {
            return "var";
        }
        String[] parts = cleaned.split("\\s+");
        return parts[0];
    }
}
