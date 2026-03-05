package io.themis.staticanalysis.traversal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallTraversalResult {
    private final Map<String, Integer> depthByMethod;

    public CallTraversalResult(Map<String, Integer> depthByMethod) {
        this.depthByMethod = new HashMap<>(depthByMethod);
    }

    public Map<String, Integer> getDepthByMethod() {
        return new HashMap<>(depthByMethod);
    }

    public List<String> methods() {
        List<String> methods = new ArrayList<>(depthByMethod.keySet());
        methods.sort(String::compareTo);
        return methods;
    }

    public int depth(String signature) {
        return depthByMethod.getOrDefault(signature, Integer.MAX_VALUE);
    }
}
