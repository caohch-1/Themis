package io.themis.staticanalysis.traversal;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

public class RpcServerDownwardTraversal {
    public CallTraversalResult traverse(SootMethod rpcServerMethod, int maxDepth) {
        CallGraph callGraph = Scene.v().getCallGraph();
        Queue<SootMethod> queue = new ArrayDeque<>();
        Map<String, Integer> depthByMethod = new HashMap<>();
        queue.offer(rpcServerMethod);
        depthByMethod.put(rpcServerMethod.getSignature(), 0);

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            int depth = depthByMethod.get(current.getSignature());
            if (depth >= maxDepth) {
                continue;
            }
            Iterator<Edge> it = callGraph.edgesOutOf(current);
            while (it.hasNext()) {
                Edge edge = it.next();
                SootMethod callee = edge.tgt();
                if (callee == null) {
                    continue;
                }
                if (!depthByMethod.containsKey(callee.getSignature())) {
                    depthByMethod.put(callee.getSignature(), depth + 1);
                    queue.offer(callee);
                }
            }
        }
        return new CallTraversalResult(depthByMethod);
    }
}
