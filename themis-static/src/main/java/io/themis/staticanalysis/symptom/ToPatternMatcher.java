package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomKind;
import io.themis.core.model.SymptomPattern;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ToPatternMatcher implements ImplicitPatternMatcher {
    private final ThreadUnsafeRegistry registry;

    public ToPatternMatcher(ThreadUnsafeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<SymptomCandidate> match(ViolationTuple tuple) {
        List<SymptomCandidate> hits = new ArrayList<>();
        for (AccessSite site : tuple.getAccessSites()) {
            String statement = site.getStatement();
            String methodName = methodName(statement);
            String type = inferType(site, statement);
            if (!registry.isThreadUnsafeOp(type, methodName)) {
                continue;
            }
            if (!site.isWriteLike() && !statement.toLowerCase(Locale.ROOT).contains("get")) {
                continue;
            }
            hits.add(new SymptomCandidate(tuple.getId() + "-to-" + site.getId(), SymptomPattern.TO, SymptomKind.THREAD_UNSAFE_EFFECT, statement, tuple.getEnclosingFunctions(), 0));
        }
        return hits;
    }

    private String inferType(AccessSite site, String statement) {
        Map<String, String> attrs = site.getAttributes();
        String valueType = attrs.getOrDefault("valueType", "");
        if (!valueType.isEmpty()) {
            return valueType;
        }
        String lower = statement == null ? "" : statement.toLowerCase(Locale.ROOT);
        if (lower.contains("hashmap")) {
            return "java.util.HashMap";
        }
        if (lower.contains("arraylist")) {
            return "java.util.ArrayList";
        }
        if (lower.contains("linkedhashmap")) {
            return "java.util.LinkedHashMap";
        }
        if (lower.contains("hashset")) {
            return "java.util.HashSet";
        }
        if (lower.contains("randomaccessfile")) {
            return "java.io.RandomAccessFile";
        }
        return attrs.getOrDefault("declaringClass", "");
    }

    private String methodName(String statement) {
        int idx = statement.indexOf('(');
        if (idx <= 0) {
            return statement;
        }
        int dot = statement.lastIndexOf('.', idx);
        if (dot < 0) {
            return statement.substring(0, idx).trim();
        }
        return statement.substring(dot + 1, idx).trim();
    }
}
