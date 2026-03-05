package io.themis.fuzz.instrumentation;

import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.ViolationTuple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class InstrumentationPlanner {
    private final int calleeHopLimit;

    public InstrumentationPlanner(int calleeHopLimit) {
        this.calleeHopLimit = calleeHopLimit;
    }

    public List<String> planTargets(ExecutionPathBundle bundle,
                                    Map<String, List<String>> adjacency) {
        List<String> targets = new ArrayList<>();
        ViolationTuple tuple = bundle.getViolationTuple();
        targets.addAll(bundle.getCallChain());
        for (String function : tuple.getEnclosingFunctions()) {
            targets.add(function);
        }
        tuple.getAccessSites().forEach(site -> targets.add(site.getStatement()));
        return expandWithCallees(targets, adjacency, calleeHopLimit);
    }

    public List<String> expandWithCallees(List<String> seedTargets,
                                          Map<String, List<String>> adjacency,
                                          int maxHops) {
        Map<String, Integer> depth = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        for (String seed : seedTargets) {
            if (!depth.containsKey(seed)) {
                depth.put(seed, 0);
                queue.add(seed);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int d = depth.get(current);
            if (d >= maxHops) {
                continue;
            }
            for (String next : adjacency.getOrDefault(current, new ArrayList<String>())) {
                if (depth.containsKey(next)) {
                    continue;
                }
                depth.put(next, d + 1);
                queue.add(next);
            }
        }
        return new ArrayList<>(depth.keySet());
    }

    public int getCalleeHopLimit() {
        return calleeHopLimit;
    }
}
