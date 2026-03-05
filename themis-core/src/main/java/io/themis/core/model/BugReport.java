package io.themis.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BugReport {
    private final String bugId;
    private final Set<String> violationIds;
    private final Set<SymptomPattern> patterns;
    private final String summary;
    private final String evidence;
    private final Instant createdAt;

    public BugReport(String bugId,
                     Set<String> violationIds,
                     Set<SymptomPattern> patterns,
                     String summary,
                     String evidence,
                     Instant createdAt) {
        this.bugId = bugId;
        this.violationIds = violationIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(violationIds);
        this.patterns = patterns == null ? new LinkedHashSet<>() : new LinkedHashSet<>(patterns);
        this.summary = summary;
        this.evidence = evidence;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String getBugId() {
        return bugId;
    }

    public Set<String> getViolationIds() {
        return new LinkedHashSet<>(violationIds);
    }

    public Set<SymptomPattern> getPatterns() {
        return new LinkedHashSet<>(patterns);
    }

    public String getSummary() {
        return summary;
    }

    public String getEvidence() {
        return evidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<String> sortedViolationIds() {
        List<String> ids = new ArrayList<>(violationIds);
        ids.sort(String::compareTo);
        return ids;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BugReport)) {
            return false;
        }
        BugReport bugReport = (BugReport) o;
        return Objects.equals(bugId, bugReport.bugId)
            && Objects.equals(violationIds, bugReport.violationIds)
            && Objects.equals(patterns, bugReport.patterns)
            && Objects.equals(summary, bugReport.summary)
            && Objects.equals(evidence, bugReport.evidence)
            && Objects.equals(createdAt, bugReport.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bugId, violationIds, patterns, summary, evidence, createdAt);
    }
}
