package io.themis.fuzz.stage;

import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ParameterSeed;
import io.themis.fuzz.engine.FuzzExecutionResult;
import io.themis.fuzz.engine.JqfInvoker;
import io.themis.fuzz.interleaving.InterleavingPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class JqfInterleavingRuntimeExecutor implements InterleavingRuntimeExecutor {
    private final JqfInvoker invoker;
    private final String testClass;
    private final String testMethod;
    private final ParameterSeed seed;
    private final List<String> baseArgs;

    public JqfInterleavingRuntimeExecutor(JqfInvoker invoker,
                                          String testClass,
                                          String testMethod,
                                          ParameterSeed seed,
                                          List<String> baseArgs) {
        this.invoker = invoker;
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.seed = seed;
        this.baseArgs = baseArgs == null ? new ArrayList<String>() : new ArrayList<String>(baseArgs);
    }

    @Override
    public InterleavingRuntimeResult execute(ViolationTuple tuple,
                                             InterleavingPlan plan,
                                             Map<String, String> runtimeProperties) {
        List<String> args = new ArrayList<String>(baseArgs);
        for (Map.Entry<String, String> entry : runtimeProperties.entrySet()) {
            args.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        args.add("-Dthemis.stage=stage2");
        args.add("-Dthemis.interleaving.shape=" + plan.getShape().name());

        FuzzExecutionResult result = invoker.execute(testClass, testMethod, seed, plan.getSequence(), args);
        List<SymptomCandidate> symptoms = collectSymptoms(tuple, result.getTrace(), plan.getShape().name().toLowerCase(Locale.ROOT));
        boolean reached = result.isReachedTargets();
        return new InterleavingRuntimeResult(result.getTrace(), symptoms, reached);
    }

    private List<SymptomCandidate> collectSymptoms(ViolationTuple tuple, List<String> trace, String shapeTag) {
        Map<String, SymptomCandidate> unique = new LinkedHashMap<String, SymptomCandidate>();
        Set<String> patternHints = new LinkedHashSet<String>();
        for (String event : trace) {
            if (!event.startsWith("SYMPTOM:")) {
                if (event.startsWith("RAW:")) {
                    inferFromRaw(event.substring("RAW:".length()), patternHints);
                }
                continue;
            }
            String payload = event.substring("SYMPTOM:".length()).trim();
            if (payload.isEmpty()) {
                continue;
            }
            String[] parts = payload.split("\\|", 2);
            String patternText = parts[0].trim().toUpperCase(Locale.ROOT);
            SymptomPattern pattern = safePattern(patternText);
            String statement = parts.length > 1 ? parts[1].trim() : payload;
            String id = tuple.getId() + "-runtime-" + pattern.name().toLowerCase(Locale.ROOT) + "-" + Math.abs(statement.hashCode());
            unique.put(id, new SymptomCandidate(id, pattern, kindFor(pattern), statement, tuple.getEnclosingFunctions(), 0));
        }
        if (!patternHints.isEmpty()) {
            for (String hint : patternHints) {
                SymptomPattern pattern = safePattern(hint);
                String id = tuple.getId() + "-runtime-" + pattern.name().toLowerCase(Locale.ROOT) + "-" + shapeTag;
                unique.putIfAbsent(id, new SymptomCandidate(id, pattern, kindFor(pattern), shapeTag, tuple.getEnclosingFunctions(), 0));
            }
        }
        if (unique.isEmpty()) {
            for (SymptomCandidate candidate : tuple.getSymptomCandidates()) {
                if (seenInTrace(candidate, trace)) {
                    unique.put(candidate.getId(), candidate);
                }
            }
        }
        return new ArrayList<SymptomCandidate>(unique.values());
    }

    private boolean seenInTrace(SymptomCandidate candidate, List<String> trace) {
        String statement = candidate.getStatement() == null ? "" : candidate.getStatement().toLowerCase(Locale.ROOT);
        if (statement.isEmpty()) {
            return false;
        }
        for (String event : trace) {
            if (event.startsWith("RAW:") && event.toLowerCase(Locale.ROOT).contains(statement)) {
                return true;
            }
        }
        return false;
    }

    private void inferFromRaw(String raw, Set<String> outPatterns) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (lower.contains("nullpointerexception")) {
            outPatterns.add("NP");
        }
        if (lower.contains("arrayindexoutofboundsexception") || lower.contains("indexoutofboundsexception")) {
            outPatterns.add("OB");
        }
        if (lower.contains("concurrentmodificationexception")) {
            outPatterns.add("TO");
        }
    }

    private SymptomPattern safePattern(String value) {
        try {
            return SymptomPattern.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SymptomPattern.TO;
        }
    }

    private SymptomKind kindFor(SymptomPattern pattern) {
        switch (pattern) {
            case OB:
                return SymptomKind.OUT_OF_BOUNDS;
            case TO:
                return SymptomKind.THREAD_UNSAFE_EFFECT;
            default:
                return SymptomKind.NULL_DEREFERENCE;
        }
    }
}
