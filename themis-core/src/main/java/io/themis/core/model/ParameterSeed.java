package io.themis.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ParameterSeed {
    private final String id;
    private final Map<String, String> values;
    private final double score;
    private final String source;

    public ParameterSeed(String id, Map<String, String> values, double score, String source) {
        this.id = id;
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        this.score = score;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getValues() {
        return new LinkedHashMap<>(values);
    }

    public double getScore() {
        return score;
    }

    public String getSource() {
        return source;
    }

    public ParameterSeed withScore(double newScore) {
        return new ParameterSeed(id, values, newScore, source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParameterSeed)) {
            return false;
        }
        ParameterSeed that = (ParameterSeed) o;
        return Double.compare(that.score, score) == 0 && Objects.equals(id, that.id) && Objects.equals(values, that.values) && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, values, score, source);
    }
}
