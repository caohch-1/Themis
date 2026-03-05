package indi.dc.extraction.utils;

import cn.ac.ios.bridge.analysis.Analyzer;
import indi.dc.Main;
import soot.SootMethod;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

public class RPCUtils {
    public static CallGraph cg = Scene.v().getCallGraph();

    public static Set<SootMethod> findRPCImplMethods(Stmt invokeStmt) {
        if (!invokeStmt.containsInvokeExpr()) {
            return null;
        }

        SootMethod callee = invokeStmt.getInvokeExpr().getMethod();
        Set<SootMethod> realCallees = new HashSet<>();
        if (invokeStmt.getInvokeExpr() instanceof InterfaceInvokeExpr && indi.dc.Main.analyzer.impls.containsKey(callee.getDeclaringClass().toString())) {
            String protocol = callee.getDeclaringClass().toString();
            Set<String> impls = Main.analyzer.impls.get(protocol);
            for (String impl : impls) {
                SootClass implClass = Scene.v().getSootClassUnsafe(impl);
                if (implClass != null) {
                    SootMethod rpcCallee = implClass.getMethodUnsafe(callee.getName(), callee.getParameterTypes(), callee.getReturnType());
                    if (rpcCallee != null) {
                        realCallees.add(rpcCallee);
                    }
                }
            }
            return realCallees;
        } else {
            return null;
        }
    }

    public static boolean isRPCImplField(SootField sootField) {
        Set<SootClass> rpcClasses = new HashSet<>();
        for (String protocol : Main.analyzer.impls.keySet()) {
            for (String implClass : Main.analyzer.impls.get(protocol)) {
                SootClass implCls = Scene.v().getSootClassUnsafe(implClass);
                if (implCls != null) {
                    rpcClasses.add(implCls);
                }
            }
        }

        if (rpcClasses.contains(sootField.getDeclaringClass())) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isRPCImplMethod(SootMethod method) {
        Set<SootClass> rpcClasses = new HashSet<>();
        for (String protocol : Main.analyzer.impls.keySet()) {
            for (String implClass : Main.analyzer.impls.get(protocol)) {
                SootClass implCls = Scene.v().getSootClassUnsafe(implClass);
                if (implCls != null) {
                    rpcClasses.add(implCls);
                }
            }
        }

        if (rpcClasses.contains(method.getDeclaringClass()) && MethodOverrideChecker.isOverrideMethod(method)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isRPCProtoMethod(SootMethod method) {
        Set<SootClass> rpcClasses = new HashSet<>();
        for (String protocol : Main.analyzer.impls.keySet()) {
            SootClass protoCls = Scene.v().getSootClassUnsafe(protocol);
            if (protoCls != null) {
                rpcClasses.add(protoCls);
            }
        }

        if (rpcClasses.contains(method.getDeclaringClass())) {
            return true;
        } else {
            return false;
        }
    }

    public static Set<Analyzer.CallSite> findRPCCallerMethods(SootMethod method) {
        if (isRPCImplMethod(method)) {
            for (String protocol : Main.analyzer.impls.keySet()) {
                for (String impl : Main.analyzer.impls.get(protocol)) {
                    if (impl.equals(method.getDeclaringClass().toString())) {
                        Set<Analyzer.CallSite> rpcCallSites = Main.analyzer.proxyRPCSite.get(protocol);
                        Set<Analyzer.CallSite> callers = new HashSet<>();
                        for (Analyzer.CallSite rpcCallSite : rpcCallSites) {
                            if (rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature().equals(method.getSubSignature())) {
                                callers.add(rpcCallSite);
                            }
                        }
                        return callers;
                    }
                }
            }
        }
        return null;
    }

    private static final int MAX_RECURSION_DEPTH = 50;

    public static List<Analyzer.CallSite> findRPCRelatedCallee(SootMethod method) {
        if (!method.hasActiveBody()) return null;
        if (isRPCProtoMethod(method)) {
            return new ArrayList<Analyzer.CallSite>() {{add(new Analyzer.CallSite(method, null, null));}};
        }

        Set<SootMethod> visited = new HashSet<>();
        Map<SootMethod, SootMethod> predecessor = new HashMap<>();
        Map<SootMethod, Integer> depthMap = new HashMap<>(); // 记录每个方法的调用深度
        Queue<SootMethod> queue = new LinkedList<>();

        // 初始化：起始方法深度为0
        predecessor.put(method, null);
        depthMap.put(method, 0);
        queue.add(method);

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            int currentDepth = depthMap.get(current);
            // 如果当前深度超过阈值，跳过后续处理
            if (currentDepth > MAX_RECURSION_DEPTH) {
                continue;
            }

            if (isRPCProtoMethod(current)) {
                // 回溯构建路径
                List<Analyzer.CallSite> path = new ArrayList<>();
                SootMethod node = current;
                while (node != null) {
                    path.add(0, new Analyzer.CallSite(node, null, null));
                    node = predecessor.get(node);
                }
                return path;
            }

            // 遍历后继方法
            Iterator<Edge> succEdges = cg.edgesOutOf(current);
            while (succEdges.hasNext()) {
                Edge edge = succEdges.next();
                SootMethod succ = edge.tgt();
                int succDepth = currentDepth + 1;
                // 仅处理未超出深度限制的后继方法
                if (succDepth <= MAX_RECURSION_DEPTH && !depthMap.containsKey(succ)) {
                    depthMap.put(succ, succDepth);
                    predecessor.put(succ, current);
                    queue.add(succ);
                }
            }
        }
        return null;
    }

    // 主入口方法：查找RPC相关调用链
    public static List<Analyzer.CallSite> findRPCRelated(SootMethod target) {
        // 使用局部缓存（方法签名 -> 已探索路径）
        Map<String, List<Analyzer.CallSite>> localCache = new HashMap<>();
        List<Analyzer.CallSite> result = findRPCRelated(target, null, new HashSet<>(), localCache, 0);
        return (result != null && !result.isEmpty()) ? result : new ArrayList<>();
    }

    // 递归搜索核心逻辑
    private static List<Analyzer.CallSite> findRPCRelated(SootMethod currentMethod, SootMethod calleeMethod, Set<SootMethod> visited, Map<String, List<Analyzer.CallSite>> localCache, int depth) {
        // 终止条件1：已访问过该方法
        if (visited.contains(currentMethod)) return null;
        if (!currentMethod.hasActiveBody()) return null;
        if (depth > MAX_RECURSION_DEPTH) {
//            Log.i("[Warn] ", "Max recursion depth reached for: " + currentMethod.getName());
            return null;
        }

        visited.add(currentMethod);

        // 生成缓存键
        String cacheKey = (calleeMethod != null) ? calleeMethod.getSignature() + "->" + currentMethod.getSignature() : currentMethod.getSignature();
        if (localCache.containsKey(cacheKey)) {
            return localCache.get(cacheKey);
        }

        // 条件检查：当前方法是否为RPC实现
        if (isRPCImplMethod(currentMethod)) {
            List<Analyzer.CallSite> chain = new ArrayList<>();
            Stmt callStmt = findCallStmt(currentMethod, calleeMethod);
            chain.add(new Analyzer.CallSite(currentMethod, callStmt, null)); // 根节点标记
            localCache.put(cacheKey, chain);
            return chain;
        }


        Iterator<Edge> edges = cg.edgesInto(currentMethod);
        while (edges.hasNext()) {
            Edge edge = edges.next();
            Stmt callSiteStmt = edge.srcStmt();
            SootMethod caller = edge.getSrc().method();
            // 条件检查：调用前是否有RPC协议调用
            if (hasProtoBeforeCall(caller, callSiteStmt)) {
                List<Analyzer.CallSite> chain = new ArrayList<>();
                chain.add(new Analyzer.CallSite(caller, callSiteStmt, null));
                localCache.put(cacheKey, chain);
                return chain;
            }
            // 递归向上查找
            List<Analyzer.CallSite> upperChain = findRPCRelated(caller, currentMethod, new HashSet<>(visited), localCache, depth + 1);
            if (upperChain != null) {
                // 拼接调用链
                List<Analyzer.CallSite> newChain = new ArrayList<>(upperChain);
                newChain.add(new Analyzer.CallSite(caller, callSiteStmt, null));
                localCache.put(currentMethod.getSignature(), newChain);
                return newChain;
            }
        }

        localCache.put(cacheKey, null);
        return null;
    }

    // 检查调用语句前是否有RPC协议调用
    private static boolean hasProtoBeforeCall(SootMethod caller, Stmt callStmt) {
        if (!caller.hasActiveBody()) return false;
        Body body = caller.retrieveActiveBody();
        UnitGraph cfg = new BriefUnitGraph(body);

        List<Unit> units = cfg.getPredsOf(callStmt);
        List<Unit> visitedUnits = new ArrayList<>();
        visitedUnits.add(callStmt);

        while (!units.isEmpty()) {
            Unit unit = units.remove(0);
            if (visitedUnits.contains(unit)) {
                continue;
            }
            visitedUnits.add(unit);
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr() && isRPCProtoMethod(stmt.getInvokeExpr().getMethod())) {
                return true;
            }
            units.addAll(cfg.getPredsOf(unit));
        }
        return false;
    }

    // 辅助方法：查找调用指定方法的语句
    private static Stmt findCallStmt(SootMethod caller, SootMethod callee) {
        if (callee == null) return null;
        for (Unit unit : caller.getActiveBody().getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr() &&
                        stmt.getInvokeExpr().getMethod().equals(callee)) {
                    return stmt;
                }
            }
        }
        return null;
    }
}
