package io.themis.core.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CallPath {
    private final String entry;
    private final String target;
    private final List<String> nodes;

    public CallPath(String entry, String target, List<String> nodes) {
        this.entry = entry;
        this.target = target;
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    public String getEntry() {
        return entry;
    }

    public String getTarget() {
        return target;
    }

    public List<String> getNodes() {
        return new ArrayList<>(nodes);
    }

    public int hopCount() {
        if (nodes.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, nodes.size() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallPath)) {
            return false;
        }
        CallPath callPath = (CallPath) o;
        return Objects.equals(entry, callPath.entry) && Objects.equals(target, callPath.target) && Objects.equals(nodes, callPath.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, target, nodes);
    }
}
