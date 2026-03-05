package io.themis.core.graph;

import java.util.Objects;

public class CallEdge {
    private final String source;
    private final String target;
    private final String relation;

    public CallEdge(String source, String target, String relation) {
        this.source = source;
        this.target = target;
        this.relation = relation;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getRelation() {
        return relation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallEdge)) {
            return false;
        }
        CallEdge callEdge = (CallEdge) o;
        return Objects.equals(source, callEdge.source) && Objects.equals(target, callEdge.target) && Objects.equals(relation, callEdge.relation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, relation);
    }
}
