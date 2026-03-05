package io.themis.core.util;

import io.themis.core.graph.CallPath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class HopDistanceCalculator {
    public CallPath shortestPath(String start, String end, Map<String, List<String>> adjacency) {
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(end)) {
                break;
            }
            for (String next : adjacency.getOrDefault(current, new ArrayList<>())) {
                if (visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                parent.put(next, current);
                queue.add(next);
            }
        }
        if (!visited.contains(end)) {
            return new CallPath(start, end, new ArrayList<>());
        }
        List<String> nodes = new ArrayList<>();
        String cursor = end;
        while (cursor != null) {
            nodes.add(0, cursor);
            cursor = parent.get(cursor);
        }
        return new CallPath(start, end, nodes);
    }
}
