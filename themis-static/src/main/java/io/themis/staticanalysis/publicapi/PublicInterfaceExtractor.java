package io.themis.staticanalysis.publicapi;

import io.themis.core.model.PublicInterfacePair;
import io.themis.core.model.ViolationTuple;
import io.themis.core.util.HopDistanceCalculator;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class PublicInterfaceExtractor {
    private final HopDistanceCalculator hopDistanceCalculator = new HopDistanceCalculator();

    public PublicInterfacePair extractForRules123(ViolationTuple tuple) {
        List<String> functions = tuple.getEnclosingFunctions();
        if (functions.size() < 2) {
            return new PublicInterfacePair("", "", new ArrayList<String>(), new ArrayList<String>());
        }
        String left = nearestPublicCaller(functions.get(0));
        String right = nearestPublicCaller(functions.get(1));
        return new PublicInterfacePair(left, right, path(left, functions.get(0)), path(right, functions.get(1)));
    }

    public PublicInterfacePair extractForRules45(ViolationTuple tuple) {
        List<String> functions = tuple.getEnclosingFunctions();
        if (functions.isEmpty()) {
            return new PublicInterfacePair("", "", new ArrayList<String>(), new ArrayList<String>());
        }
        String api = nearestPublicCaller(functions.get(0));
        return new PublicInterfacePair(api, api, path(api, functions.get(0)), path(api, functions.get(0)));
    }

    public List<String> rankCandidates(String targetFunction, List<String> candidates) {
        Map<String, List<String>> adjacency = asAdjacency();
        List<String> ranked = new ArrayList<>(candidates);
        ranked.sort((a, b) -> {
            int ha = hopDistanceCalculator.shortestPath(a, targetFunction, adjacency).hopCount();
            int hb = hopDistanceCalculator.shortestPath(b, targetFunction, adjacency).hopCount();
            int topA = isTopLevel(a) ? 0 : 1;
            int topB = isTopLevel(b) ? 0 : 1;
            int cmp = Integer.compare(ha, hb);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(topA, topB);
            if (cmp != 0) {
                return cmp;
            }
            return a.compareTo(b);
        });
        return ranked;
    }

    public boolean isTopLevel(String methodSignature) {
        if (methodSignature == null || methodSignature.isEmpty()) {
            return false;
        }
        if (!Scene.v().containsMethod(methodSignature)) {
            return methodSignature.contains("main(");
        }
        SootMethod method = Scene.v().getMethod(methodSignature);
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> it = callGraph.edgesInto(method);
        return !it.hasNext();
    }

    private String nearestPublicCaller(String target) {
        if (target == null || target.isEmpty() || !Scene.v().containsMethod(target)) {
            return "";
        }
        SootMethod seed = Scene.v().getMethod(target);
        Queue<SootMethod> queue = new ArrayDeque<>();
        Map<String, Boolean> visited = new HashMap<>();
        queue.offer(seed);
        visited.put(seed.getSignature(), true);
        CallGraph callGraph = Scene.v().getCallGraph();

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            if (current.isPublic()) {
                return current.getSignature();
            }
            Iterator<Edge> in = callGraph.edgesInto(current);
            while (in.hasNext()) {
                SootMethod caller = in.next().src();
                if (caller == null) {
                    continue;
                }
                if (!visited.containsKey(caller.getSignature())) {
                    visited.put(caller.getSignature(), true);
                    queue.offer(caller);
                }
            }
        }
        return "";
    }

    private List<String> path(String start, String end) {
        return hopDistanceCalculator.shortestPath(start, end, asAdjacency()).getNodes();
    }

    private Map<String, List<String>> asAdjacency() {
        Map<String, List<String>> adjacency = new HashMap<>();
        CallGraph callGraph = Scene.v().getCallGraph();
        Iterator<Edge> iterator = callGraph.iterator();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            String src = edge.src().getSignature();
            String tgt = edge.tgt().getSignature();
            adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
        }
        return adjacency;
    }
}
