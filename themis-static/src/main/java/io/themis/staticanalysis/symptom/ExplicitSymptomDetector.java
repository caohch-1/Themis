package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExplicitSymptomDetector {
    public List<SymptomCandidate> detect(ViolationTuple tuple,
                                         Map<String, Integer> reachable,
                                         int hopBound) {
        Map<String, SymptomCandidate> results = new LinkedHashMap<>();
        for (AccessSite site : tuple.getAccessSites()) {
            int distance = reachable.getOrDefault(site.getMethodSignature(), hopBound + 1);
            if (distance > hopBound) {
                continue;
            }
            addIfMatched(results, tuple, site.getStatement(), site.getMethodSignature(), distance);
        }
        for (Map.Entry<String, Integer> entry : reachable.entrySet()) {
            String methodSig = entry.getKey();
            int distance = entry.getValue();
            if (distance > hopBound || !Scene.v().containsMethod(methodSig)) {
                continue;
            }
            SootMethod method = Scene.v().getMethod(methodSig);
            if (!method.hasActiveBody()) {
                continue;
            }
            for (Unit unit : method.getActiveBody().getUnits()) {
                addIfMatched(results, tuple, unit.toString(), methodSig, distance);
            }
        }
        return new ArrayList<>(results.values());
    }

    private void addIfMatched(Map<String, SymptomCandidate> out,
                              ViolationTuple tuple,
                              String statement,
                              String methodSignature,
                              int distance) {
        String text = statement == null ? "" : statement.toLowerCase();
        SymptomCandidate candidate = null;
        if (text.contains("throw") || text.contains("exception")) {
            candidate = candidate(tuple, statement, methodSignature, distance, SymptomPattern.EO, SymptomKind.UNCAUGHT_EXCEPTION, "eo");
        } else if (text.contains("log.error") || text.contains("logger.error") || text.contains("fatal")) {
            candidate = candidate(tuple, statement, methodSignature, distance, SymptomPattern.EO, SymptomKind.SEVERE_LOG, "eo");
        } else if (text.contains("system.exit") || text.contains("runtime.halt") || text.contains("assert false")) {
            candidate = candidate(tuple, statement, methodSignature, distance, SymptomPattern.EA, SymptomKind.SYSTEM_ABORT, "ea");
        }
        if (candidate != null) {
            out.put(candidate.getId(), candidate);
        }
    }

    private SymptomCandidate candidate(ViolationTuple tuple,
                                       String statement,
                                       String methodSignature,
                                       int distance,
                                       SymptomPattern pattern,
                                       SymptomKind kind,
                                       String prefix) {
        List<String> context = new ArrayList<>(tuple.getEnclosingFunctions());
        if (!context.contains(methodSignature)) {
            context.add(methodSignature);
        }
        String id = tuple.getId() + "-" + prefix + "-" + Integer.toHexString(Math.abs((methodSignature + "::" + statement + "::" + distance).hashCode()));
        return new SymptomCandidate(id, pattern, kind, statement, context, distance);
    }
}
