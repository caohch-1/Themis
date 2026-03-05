package io.themis.core.util;

import io.themis.core.graph.CallPath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublicInterfaceRanker {
    private final HopDistanceCalculator calculator = new HopDistanceCalculator();

    public List<String> rank(String targetFunction, List<String> publicInterfaces, Map<String, List<String>> adjacency) {
        List<String> sorted = new ArrayList<>(publicInterfaces);
        Map<String, Integer> hops = new HashMap<>();
        for (String method : publicInterfaces) {
            CallPath path = calculator.shortestPath(method, targetFunction, adjacency);
            hops.put(method, path.hopCount());
        }
        sorted.sort(Comparator
            .comparingInt((String method) -> hops.getOrDefault(method, Integer.MAX_VALUE))
            .thenComparing(this::isTopLevel)
            .thenComparing(String::compareTo));
        return sorted;
    }

    private int isTopLevel(String method) {
        return method.contains("main(") || method.contains("run(") || method.contains("execute(") ? 0 : 1;
    }
}
