package io.themis.core.report;

import io.themis.core.model.BugReport;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SymptomPattern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BugReportBuilder {
    public List<BugReport> build(List<FuzzOutcome> outcomes) {
        Map<String, Set<String>> groupedViolations = new HashMap<>();
        Map<String, Set<SymptomPattern>> groupedPatterns = new HashMap<>();
        Map<String, String> groupedEvidence = new HashMap<>();
        for (FuzzOutcome outcome : outcomes) {
            if (!outcome.isExposed()) {
                continue;
            }
            String key = groupingKey(outcome);
            groupedViolations.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(outcome.getViolationId());
            Set<SymptomPattern> patterns = groupedPatterns.computeIfAbsent(key, k -> new LinkedHashSet<>());
            for (SymptomCandidate candidate : outcome.getManifestedSymptoms()) {
                patterns.add(candidate.getPattern());
            }
            groupedEvidence.put(key, String.join("|", outcome.getReachedTargets()));
        }
        List<BugReport> reports = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<String, Set<String>> entry : groupedViolations.entrySet()) {
            String bugId = String.format("bug-%03d", idx++);
            Set<SymptomPattern> patterns = groupedPatterns.getOrDefault(entry.getKey(), new LinkedHashSet<>());
            String summary = "Merged violations " + String.join(",", entry.getValue());
            String evidence = groupedEvidence.getOrDefault(entry.getKey(), "");
            reports.add(new BugReport(bugId, entry.getValue(), patterns, summary, evidence, Instant.now()));
        }
        return reports;
    }

    private String groupingKey(FuzzOutcome outcome) {
        List<String> keys = new ArrayList<>();
        for (SymptomCandidate candidate : outcome.getManifestedSymptoms()) {
            keys.add(candidate.getPattern().name());
        }
        if (keys.isEmpty()) {
            keys.add("TO");
        }
        keys.sort(String::compareTo);
        return String.join("+", keys);
    }
}
