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

public class RpcClientUpwardTraversal {
    public CallTraversalResult traverse(SootMethod rpcClientMethod, int maxDepth) {
        CallGraph callGraph = Scene.v().getCallGraph();
        Queue<SootMethod> queue = new ArrayDeque<>();
        Map<String, Integer> depthByMethod = new HashMap<>();
        queue.offer(rpcClientMethod);
        depthByMethod.put(rpcClientMethod.getSignature(), 0);

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            int depth = depthByMethod.get(current.getSignature());
            if (depth >= maxDepth) {
                continue;
            }
            Iterator<Edge> it = callGraph.edgesInto(current);
            while (it.hasNext()) {
                Edge edge = it.next();
                SootMethod caller = edge.src();
                if (caller == null) {
                    continue;
                }
                if (!depthByMethod.containsKey(caller.getSignature())) {
                    depthByMethod.put(caller.getSignature(), depth + 1);
                    queue.offer(caller);
                }
            }
        }
        return new CallTraversalResult(depthByMethod);
    }
}
