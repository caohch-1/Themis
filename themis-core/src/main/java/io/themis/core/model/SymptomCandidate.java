package io.themis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SymptomCandidate {
    private final String id;
    private final SymptomPattern pattern;
    private final SymptomKind kind;
    private final String statement;
    private final List<String> path;
    private final int hopDistance;

    public SymptomCandidate(String id, SymptomPattern pattern, SymptomKind kind, String statement, List<String> path, int hopDistance) {
        this.id = id;
        this.pattern = pattern;
        this.kind = kind;
        this.statement = statement;
        this.path = path == null ? new ArrayList<>() : new ArrayList<>(path);
        this.hopDistance = hopDistance;
    }

    public String getId() {
        return id;
    }

    public SymptomPattern getPattern() {
        return pattern;
    }

    public SymptomKind getKind() {
        return kind;
    }

    public String getStatement() {
        return statement;
    }

    public List<String> getPath() {
        return new ArrayList<>(path);
    }

    public int getHopDistance() {
        return hopDistance;
    }

    public boolean withinHopLimit(int limit) {
        return hopDistance <= limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SymptomCandidate)) {
            return false;
        }
        SymptomCandidate that = (SymptomCandidate) o;
        return hopDistance == that.hopDistance
            && Objects.equals(id, that.id)
            && pattern == that.pattern
            && kind == that.kind
            && Objects.equals(statement, that.statement)
            && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pattern, kind, statement, path, hopDistance);
    }
}
