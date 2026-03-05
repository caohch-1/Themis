package io.themis.staticanalysis.symptom;

import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImplicitPatternEngine {
    private final List<ImplicitPatternMatcher> matchers;

    public ImplicitPatternEngine() {
        this(new ThreadUnsafeRegistry());
    }

    public ImplicitPatternEngine(String threadUnsafeRegistryPath) {
        this(new ThreadUnsafeRegistry(threadUnsafeRegistryPath));
    }

    public ImplicitPatternEngine(ThreadUnsafeRegistry registry) {
        this.matchers = new ArrayList<>();
        this.matchers.add(new NpPatternMatcher());
        this.matchers.add(new ObPatternMatcher());
        this.matchers.add(new UrPatternMatcher());
        this.matchers.add(new MrPatternMatcher());
        this.matchers.add(new ToPatternMatcher(registry));
    }

    public List<SymptomCandidate> match(ViolationTuple tuple) {
        Map<String, SymptomCandidate> unique = new LinkedHashMap<>();
        for (ImplicitPatternMatcher matcher : matchers) {
            for (SymptomCandidate candidate : matcher.match(tuple)) {
                unique.put(candidate.getId(), candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
