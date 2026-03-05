package io.themis.staticanalysis.traversal;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

public class MethodNeighborhoodCollector {
    public Map<String, Integer> collect(SootMethod startMethod, int maxDistance) {
        CallGraph callGraph = Scene.v().getCallGraph();
        Queue<SootMethod> queue = new ArrayDeque<SootMethod>();
        Map<String, Integer> depth = new LinkedHashMap<String, Integer>();
        Map<String, SootMethod> methods = new HashMap<String, SootMethod>();

        queue.add(startMethod);
        depth.put(startMethod.getSignature(), 0);
        methods.put(startMethod.getSignature(), startMethod);

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            int currentDistance = depth.get(current.getSignature());
            if (currentDistance >= maxDistance) {
                continue;
            }

            Iterator<Edge> incomingEdges = callGraph.edgesInto(current);
            while (incomingEdges.hasNext()) {
                Edge edge = incomingEdges.next();
                MethodOrMethodContext src = edge.src();
                if (!(src instanceof SootMethod)) {
                    continue;
                }
                SootMethod caller = (SootMethod) src;
                if (depth.containsKey(caller.getSignature())) {
                    continue;
                }
                depth.put(caller.getSignature(), currentDistance + 1);
                methods.put(caller.getSignature(), caller);
                queue.add(caller);
            }

            Iterator<Edge> outgoingEdges = callGraph.edgesOutOf(current);
            while (outgoingEdges.hasNext()) {
                Edge edge = outgoingEdges.next();
                SootMethod callee = edge.tgt();
                if (depth.containsKey(callee.getSignature())) {
                    continue;
                }
                depth.put(callee.getSignature(), currentDistance + 1);
                methods.put(callee.getSignature(), callee);
                queue.add(callee);
            }
        }

        return depth;
    }
}
