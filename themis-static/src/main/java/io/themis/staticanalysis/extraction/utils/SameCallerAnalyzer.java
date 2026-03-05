package indi.dc.extraction.utils;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import java.util.*;

public class SameCallerAnalyzer {
    public static boolean haveCommonCaller(SootMethod method1, SootMethod method2, CallGraph callGraph) {
        Set<SootMethod> callersOfMethod1 = findAllCallers(method1, callGraph);
        Set<SootMethod> callersOfMethod2 = findAllCallers(method2, callGraph);

        // 检查两个集合是否有交集
        callersOfMethod1.retainAll(callersOfMethod2);
        return !callersOfMethod1.isEmpty();
    }

    private static Set<SootMethod> findAllCallers(SootMethod targetMethod, CallGraph callGraph) {
        Set<SootMethod> visited = new HashSet<>();
        Queue<SootMethod> queue = new LinkedList<>();

        // 初始化队列，获取直接调用者
        Iterator<Edge> edgesInto = callGraph.edgesInto(targetMethod);
        while (edgesInto.hasNext()) {
            Edge edge = edgesInto.next();
            SootMethod caller = edge.src().method();
            if (visited.add(caller)) {
                queue.add(caller);
            }
        }

        // BFS遍历所有间接调用者
        while (!queue.isEmpty()) {
            SootMethod caller = queue.poll();
            Iterator<Edge> edgesIntoCaller = callGraph.edgesInto(caller);
            while (edgesIntoCaller.hasNext()) {
                Edge edge = edgesIntoCaller.next();
                SootMethod callerOfCaller = edge.src().method();
                if (visited.add(callerOfCaller)) {
                    queue.add(callerOfCaller);
                }
            }
        }

        return visited;
    }
}
