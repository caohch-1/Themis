package io.themis.staticanalysis.symptom;

import io.themis.core.model.RpcPair;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RpcEnhancedCallGraph {
    public Map<String, List<String>> build(List<RpcPair> rpcPairs) {
        Map<String, List<String>> graph = new HashMap<>();
        if (Scene.v().hasCallGraph()) {
            CallGraph callGraph = Scene.v().getCallGraph();
            Iterator<Edge> iterator = callGraph.iterator();
            while (iterator.hasNext()) {
                Edge edge = iterator.next();
                String src = edge.src().getSignature();
                String tgt = edge.tgt().getSignature();
                graph.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            }
        }
        for (RpcPair pair : rpcPairs) {
            List<String> clients = resolveMethodSignatures(pair.getClientClass(), pair.getClientMethod());
            List<String> servers = resolveMethodSignatures(pair.getServerClass(), pair.getServerMethod());
            if (clients.isEmpty() || servers.isEmpty()) {
                continue;
            }
            for (String src : clients) {
                for (String tgt : servers) {
                    graph.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
                }
            }
        }
        return graph;
    }

    private List<String> resolveMethodSignatures(String className, String methodName) {
        List<String> methods = new ArrayList<>();
        if (!Scene.v().containsClass(className)) {
            return methods;
        }
        SootClass cls = Scene.v().getSootClass(className);
        for (SootMethod method : cls.getMethods()) {
            if (method.getName().equals(methodName)) {
                methods.add(method.getSignature());
            }
        }
        return methods;
    }
}
