package io.themis.staticanalysis.symptom;

import io.themis.core.model.AccessSite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class SymptomPropagator {
    public Map<String, Integer> reachableWithinHops(List<AccessSite> seedSites,
                                                    Map<String, List<String>> graph,
                                                    int hopBound) {
        Map<String, Integer> distance = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (AccessSite site : seedSites) {
            String node = site.getMethodSignature();
            queue.add(node);
            visited.add(node);
            distance.put(node, 0);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int d = distance.getOrDefault(current, Integer.MAX_VALUE);
            if (d >= hopBound) {
                continue;
            }
            for (String next : graph.getOrDefault(current, new ArrayList<String>())) {
                if (visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                distance.put(next, d + 1);
                queue.add(next);
            }
        }
        return distance;
    }
}
