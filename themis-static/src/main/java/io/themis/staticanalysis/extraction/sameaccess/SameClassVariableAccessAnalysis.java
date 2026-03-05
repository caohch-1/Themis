package indi.dc.extraction.sameaccess;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.SeverExtraction;
import indi.dc.extraction.utils.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

public class SameClassVariableAccessAnalysis {
    public static Set<SameAccessSite> findSameClsVarAccessesWhole(SootClass targetClass, Set<Analyzer.CallSite> rpcCallSites) {
        Set<SameAccessSite> sameClsAccesses = new HashSet<>();

        List<SootMethod> methods = new ArrayList<>();
        for (SootMethod method : targetClass.getMethods()) {
            if (method.hasActiveBody()) {
                methods.add(method);
            }
        }

        for (int i = 0; i < methods.size(); i++) {
            SootMethod m1 = methods.get(i);
            if (m1.getName().equals("<init>") || m1.getName().equals("<clinit>")) continue;
            for (int j = i + 1; j < methods.size(); j++) {
                SootMethod m2 = methods.get(j);
                if (m2.getName().equals("<init>") || m2.getName().equals("<clinit>")) continue;
                Set<SameAccessSite> pairResults = findSameClsVarAccesses(m1, m2, rpcCallSites);
                if (!pairResults.isEmpty()) {
                    sameClsAccesses.addAll(pairResults);
                }
            }
        }
        return sameClsAccesses;
    }

    public static Map<SootField, Set<Stmt>> getInsVarAccessMap(SootMethod method) {
        Map<SootField, Set<Stmt>> fieldAccessMap = new HashMap<>();
        if (!method.hasActiveBody()) {
            return fieldAccessMap;
        }
        try {
            SootClass sootClass = method.getDeclaringClass();
            if (!method.hasActiveBody()) return fieldAccessMap;
            Body body = method.retrieveActiveBody();
            for (Unit unit : body.getUnits()) {
                Stmt stmt = (Stmt) unit;
                List<ValueBox> boxes = new ArrayList<>();
                boxes.addAll(stmt.getUseBoxes());
                boxes.addAll(stmt.getDefBoxes());

                for (ValueBox box : boxes) {
                    Value value = box.getValue();
                    if (value instanceof InstanceFieldRef) {
                        SootField field = ((InstanceFieldRef) value).getField();
                        if (field.getSignature().contains("org.slf4j")) continue;
                        if (field.getDeclaringClass().equals(sootClass)) {
                            if (!fieldAccessMap.containsKey(field)) {
                                fieldAccessMap.put(field, new HashSet<>());
                            }
                            fieldAccessMap.get(field).add(stmt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i("[Error] ", e.getMessage().replace("\n", ""));
//            e.printStackTrace();
        }
        return fieldAccessMap;
    }

    public static Map<SootField, Set<Stmt>> getClsVarAccessMap(SootMethod method) {
        Map<SootField, Set<Stmt>> fieldAccessMap = new HashMap<>();
        if (!method.hasActiveBody()) {
            return fieldAccessMap;
        }
        try {
            SootClass sootClass = method.getDeclaringClass();
            if (!method.hasActiveBody()) return fieldAccessMap;
            Body body = method.retrieveActiveBody();
            for (Unit unit : body.getUnits()) {
                Stmt stmt = (Stmt) unit;
                List<ValueBox> boxes = new ArrayList<>();
                boxes.addAll(stmt.getUseBoxes());
                boxes.addAll(stmt.getDefBoxes());

                for (ValueBox box : boxes) {
                    Value value = box.getValue();
                    if (value instanceof FieldRef) {
                        SootField field = ((FieldRef) value).getField();
                        if (field.getSignature().contains("org.slf4j")) continue;
                        if (field.getDeclaringClass().equals(sootClass)) {
                            if (!fieldAccessMap.containsKey(field)) {
                                fieldAccessMap.put(field, new HashSet<>());
                            }
                            fieldAccessMap.get(field).add(stmt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i("[Error] ", e.getMessage().replace("\n", ""));
//            e.printStackTrace();
        }
        return fieldAccessMap;
    }

    // Helper method to compute callers up to a specified depth
    private static Set<String> computeCallersUpToDepth(SootMethod targetMethod, int maxDepth) {
        Set<String> callerSubSigs = new HashSet<>();
        Queue<Pair<SootMethod, Integer>> queue = new LinkedList<>();
        Set<SootMethod> visited = new HashSet<>();

        // Start with the target method at depth 0
        queue.add(new Pair<>(targetMethod, 0));
        visited.add(targetMethod);

        while (!queue.isEmpty()) {
            Pair<SootMethod, Integer> entry = queue.poll();
            SootMethod currentMethod = entry.getKey();
            int currentDepth = entry.getValue();

            // Add the current method's sub-signature to the result set
            callerSubSigs.add(currentMethod.getSubSignature());

            // Stop traversing if we've reached the maximum depth
            if (currentDepth >= maxDepth) {
                continue;
            }

            // Traverse direct callers of the current method
            for (Iterator<Edge> it = SeverExtraction.cg.edgesInto(currentMethod); it.hasNext(); ) {
                SootMethod caller = it.next().src();
                if (!visited.contains(caller)) {
                    visited.add(caller);
                    queue.add(new Pair<>(caller, currentDepth + 1));
                }
            }
        }
        return callerSubSigs;
    }

    public static Set<SameAccessSite> findSameClsVarAccesses(SootMethod method1, SootMethod method2, Set<Analyzer.CallSite> rpcCallSites) {
        Map<SootField, Set<Stmt>> fields1 = getInsVarAccessMap(method1);
        Map<SootField, Set<Stmt>> fields2 = getInsVarAccessMap(method2);

        Set<SootField> commonFields = new HashSet<>(fields1.keySet());
        commonFields.retainAll(fields2.keySet());

        Set<SameAccessSite> sameAccessSites = new HashSet<>();
        for (SootField field : commonFields) {

            Set<Analyzer.CallSite> callSites1 = new HashSet<>();
            Set<Analyzer.CallSite> callSites2 = new HashSet<>();

            // Compute all methods that can transitively call method1/method2 (up to depth 7)
            Set<String> method1Callers = computeCallersUpToDepth(method1, MAX_CG_DEPTH);
            Set<String> method2Callers = computeCallersUpToDepth(method2, MAX_CG_DEPTH);

            for (Analyzer.CallSite rpcCallSite : rpcCallSites) {
                String currentSubSig = rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature();
                if (method1Callers.contains(currentSubSig)) {
                    callSites1.add(rpcCallSite);
                }
                if (method2Callers.contains(currentSubSig)) {
                    callSites2.add(rpcCallSite);
                }
            }


            if (!callSites1.isEmpty() && !callSites2.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1.get(field), field, callSites1),
                        new AccessSite(method2, fields2.get(field), field, callSites2))
                );
            }
            else if (!callSites1.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1.get(field), field, callSites1),
                        new AccessSite(method2, fields2.get(field), field, null))
                );
            } else if (!callSites2.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1.get(field), field, null),
                        new AccessSite(method2, fields2.get(field), field, callSites2))
                );
            } else {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1.get(field), field, null),
                        new AccessSite(method2, fields2.get(field), field, null))
                );
            }
        }

        return sameAccessSites;
    }

    public static Set<SameAccessSite> findSameStaticVarAccessesWhole(SootClass targetClass, Set<Analyzer.CallSite> rpcCallSites) {
        Set<SameAccessSite> sameClsAccesses = new HashSet<>();

        Map<SootField, Map<SootMethod, Set<Stmt>>> methodStaticAccessMap = new HashMap<>();
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            for (SootMethod sootMethod : sootClass.getMethods()) {
                if (!sootMethod.hasActiveBody() || sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                Map<SootField, Set<Stmt>> fieldStaticAccessMap = getStaticVarAccessMap(sootMethod);


                for (SootField sootField : fieldStaticAccessMap.keySet()) {
                    if (methodStaticAccessMap.containsKey(sootField)) {
                        methodStaticAccessMap.get(sootField).put(sootMethod, fieldStaticAccessMap.get(sootField));
                    } else {
                        methodStaticAccessMap.put(sootField, new HashMap<SootMethod, Set<Stmt>>(){{put(sootMethod, fieldStaticAccessMap.get(sootField));}});
                    }
                }
            }
        }

        Set<SootMethod> rpcMethods = new HashSet<>();
        for (SootMethod method : targetClass.getMethods()) {
            if (method.hasActiveBody()) {
                rpcMethods.add(method);
            }
        }

        for (SootField sootField : targetClass.getFields()) {
            if (!methodStaticAccessMap.containsKey(sootField)) {
                continue;
            }
            ArrayList<SootMethod> methods = new ArrayList<>(methodStaticAccessMap.get(sootField).keySet());
            for (int i = 0; i < methods.size(); i++) {
                SootMethod method1 = methods.get(i);
                if (rpcMethods.contains(method1)) {
                    for (int j = i + 1; j < methods.size(); j++) {
                        SootMethod method2 = methods.get(j);
                        Set<Analyzer.CallSite> callSites1 = new HashSet<>();
                        Set<Analyzer.CallSite> callSites2 = new HashSet<>();
                        for (Analyzer.CallSite rpcCallSite : rpcCallSites) {
                            if (rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature().equals(method1.getSubSignature())) {
                                callSites1.add(rpcCallSite);
                            } else if (rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature().equals(method2.getSubSignature())) {
                                callSites2.add(rpcCallSite);
                            }
                        }

                        List<List<SootMethod>> chain2StaticAccess1 = new ArrayList<>();
                        chain2StaticAccess1.add(new ArrayList<SootMethod>(){{add(method1);}});
                        List<List<SootMethod>> chain2StaticAccess2 = new ArrayList<>();
                        chain2StaticAccess2.add(new ArrayList<SootMethod>(){{add(method2);}});

                        sameClsAccesses.add(new SameAccessSite(
                                new AccessSite(method1, methodStaticAccessMap.get(sootField).get(method1), sootField, callSites1, chain2StaticAccess1),
                                new AccessSite(method2, methodStaticAccessMap.get(sootField).get(method2), sootField, callSites2, chain2StaticAccess2))
                        );


                    }
                }
            }
        }
//
//        for (int i = 0; i < methods.size(); i++) {
//            SootMethod m1 = methods.get(i);
//            if (m1.getName().equals("<init>") || m1.getName().equals("<clinit>")) continue;
//            for (int j = i + 1; j < methods.size(); j++) {
//                SootMethod m2 = methods.get(j);
//                if (m2.getName().equals("<init>") || m2.getName().equals("<clinit>")) continue;
//                Set<SameAccessSite> pairResults = findSameStaticVarAccesses(m1, m2, rpcCallSites);
//                if (!pairResults.isEmpty()) {
//                    sameClsAccesses.addAll(pairResults);
//                }
//            }
//        }

        return sameClsAccesses;
    }

    public static Set<SameAccessSite> findSameClsVarAccessesClient(Analyzer.CallSite callSite) {
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        Map<SootField, Set<Stmt>> sootMethodAccessField = new HashMap<>();
        SootMethod sootMethod = callSite.getCallMethod();
        if (!sootMethod.hasActiveBody()) return sameClsVarAccessSites;

        Map<SootField, Set<Local>> fields2Locals = new HashMap<>();
        for (Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
            if (unit instanceof AssignStmt) {
                Value rightOp = ((AssignStmt) unit).getRightOp();
                Value leftOp = ((AssignStmt) unit).getLeftOp();
                if (rightOp instanceof FieldRef && leftOp instanceof Local) {
                    FieldRef fieldRef = (FieldRef) rightOp;
                    SootField sootField = fieldRef.getField();
                    if (!fields2Locals.containsKey(sootField)) {
                        fields2Locals.put(sootField, new HashSet<Local>(){{add((Local) leftOp);}});
                    } else {
                        fields2Locals.get(sootField).add((Local) leftOp);
                    }
                }
            }
        }

        Stmt stmt = callSite.getCallStmt();
        List<ValueBox> boxes = stmt.getUseAndDefBoxes();
        for (ValueBox box : boxes) {
            Value value = box.getValue();
            if (value instanceof FieldRef) {
                FieldRef fieldRef = (FieldRef)value;
                SootField sootField = fieldRef.getField();
                if (sootMethodAccessField.containsKey(sootField)) {
                    sootMethodAccessField.get(sootField).add(stmt);
                } else {
                    sootMethodAccessField.put(sootField, new HashSet<Stmt>(){{add(stmt);}});
                }
            } else if (value instanceof Local) {
                Local local = (Local) value;
                for (SootField sootField : fields2Locals.keySet()) {
                    Set<Local> locals = fields2Locals.get(sootField);
                    if (locals.contains(local)) {
                        if (sootMethodAccessField.containsKey(sootField)) {
                            sootMethodAccessField.get(sootField).add(stmt);
                        } else {
                            sootMethodAccessField.put(sootField, new HashSet<Stmt>(){{add(stmt);}});
                        }
                    }
                }
            }
        }

        if (sootMethodAccessField.isEmpty()) return sameClsVarAccessSites;

        SootClass sootClass = sootMethod.getDeclaringClass();
        for (SootMethod sootMethod1 : sootClass.getMethods()) {
            if (!sootMethod1.hasActiveBody() || sootMethod1.equals(sootMethod)) continue;
            Map<SootField, Set<Stmt>> sootMethodAccessField1 = getClsVarAccessMap(sootMethod1);
            Set<SootField> commonFields = new HashSet<>(sootMethodAccessField.keySet());
            commonFields.retainAll(sootMethodAccessField1.keySet());

            for (SootField field : commonFields) {
                sameClsVarAccessSites.add(new SameAccessSite(
                        new AccessSite(sootMethod, sootMethodAccessField.get(field), field, new HashSet<Analyzer.CallSite>(){{add(callSite);}}),
                        new AccessSite(sootMethod1, sootMethodAccessField1.get(field), field, null))
                );
            }
        }

        return sameClsVarAccessSites;
    }

    public static Set<SameAccessSite> findSameClsVarAccessesClientLocal(Analyzer.CallSite callSite) {
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        Map<Local, Set<Stmt>> sootMethodAccessLocal1 = new HashMap<>();
        SootMethod sootMethod = callSite.getCallMethod();
        if (!sootMethod.hasActiveBody()) return sameClsVarAccessSites;


        Stmt stmt = callSite.getCallStmt();
        List<ValueBox> boxes1 = stmt.getUseAndDefBoxes();
        for (ValueBox box : boxes1) {
            Value value = box.getValue();
            if (value instanceof Local) {
                sootMethodAccessLocal1.put((Local) value, Collections.singleton(stmt));
            }
        }

        if (sootMethodAccessLocal1.isEmpty()) return sameClsVarAccessSites;

        for (Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
            if (((Stmt) unit).containsInvokeExpr()) {
                List<ValueBox> boxes2 = unit.getUseAndDefBoxes();
                for (ValueBox box : boxes2) {
                    Value value = box.getValue();
                    if (value instanceof Local) {
                        if (sootMethodAccessLocal1.containsKey((Local) value)) {
                            sameClsVarAccessSites.add(new SameAccessSite(
                                    new AccessSite(sootMethod, sootMethodAccessLocal1.get((Local) value), (Local) value, new HashSet<Analyzer.CallSite>(){{add(callSite);}}),
                                    new AccessSite(sootMethod, new HashSet<Stmt>(){{add((Stmt) unit);}}, (Local) value, null))
                            );
                        }

                    }
                }
            }
        }

        return sameClsVarAccessSites;
    }

    // RPC caller site
    public static final int MAX_CG_DEPTH = 4;

    public static Set<SameAccessSite> findSameStaticVarAccesses(SootMethod method1, SootMethod method2, Set<Analyzer.CallSite> rpcCallSites) {
        Map<SootField, Set<Stmt>> fields1AccessStmts = new HashMap<>();
        Map<SootField, List<List<SootMethod>>> fields1AccessMethodChain = new HashMap<>();
        findSameStaticVarAccessesThroughCG(method1, fields1AccessStmts, fields1AccessMethodChain, MAX_CG_DEPTH);
        Map<SootField, Set<Stmt>> fields2AccessStmts = new HashMap<>();
        Map<SootField, List<List<SootMethod>>> fields2AccessMethodChain = new HashMap<>();
        findSameStaticVarAccessesThroughCG(method1, fields2AccessStmts, fields2AccessMethodChain, MAX_CG_DEPTH);

        Set<SootField> commonFields = new HashSet<>(fields1AccessStmts.keySet());
        commonFields.retainAll(fields2AccessStmts.keySet());

        Set<SameAccessSite> sameAccessSites = new HashSet<>();
        for (SootField field : commonFields) {
//            Log.i(field.getName());
            Set<Analyzer.CallSite> callSites1 = new HashSet<>();
            Set<Analyzer.CallSite> callSites2 = new HashSet<>();
            for (Analyzer.CallSite rpcCallSite : rpcCallSites) {
                if (rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature().equals(method1.getSubSignature())) {
                    callSites1.add(rpcCallSite);
                } else if (rpcCallSite.getCallStmt().getInvokeExpr().getMethod().getSubSignature().equals(method2.getSubSignature())) {
                    callSites2.add(rpcCallSite);
                }
            }


            if (!callSites1.isEmpty() && !callSites2.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1AccessStmts.get(field), field, callSites1, fields1AccessMethodChain.get(field)),
                        new AccessSite(method2, fields2AccessStmts.get(field), field, callSites2, fields2AccessMethodChain.get(field)))
                );
            }
            else if (!callSites1.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1AccessStmts.get(field), field, callSites1, fields1AccessMethodChain.get(field)),
                        new AccessSite(method2, fields2AccessStmts.get(field), field, null, fields2AccessMethodChain.get(field)))
                );
            } else if (!callSites2.isEmpty()) {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1AccessStmts.get(field), field, null, fields1AccessMethodChain.get(field)),
                        new AccessSite(method2, fields2AccessStmts.get(field), field, callSites2, fields2AccessMethodChain.get(field)))
                );
            } else {
                sameAccessSites.add(new SameAccessSite(
                        new AccessSite(method1, fields1AccessStmts.get(field), field, null, fields1AccessMethodChain.get(field)),
                        new AccessSite(method2, fields2AccessStmts.get(field), field, null, fields2AccessMethodChain.get(field)))
                );
            }
        }

        return sameAccessSites;
    }

    // Todo: chain可能有不同最后一个元素，或许有bug
    public static void findSameStaticVarAccessesThroughCG(SootMethod method, Map<SootField, Set<Stmt>> fields1AccessStmts, Map<SootField, List<List<SootMethod>>> fields1AccessMethodChain, int maxDepth) {  // 添加最大深度参数
        Queue<Pair<SootMethod, List<SootMethod>>> queue = new LinkedList<>();
        queue.add(new Pair<>(method, new ArrayList<SootMethod>(){{ add(method); }}));
        Set<SootMethod> visitedMethods = new HashSet<>();

        while (!queue.isEmpty()) {
            Pair<SootMethod, List<SootMethod>> currentPair = queue.poll();
            SootMethod currentMethod = currentPair.getKey();
            List<SootMethod> currentChain = new ArrayList<>(currentPair.getValue());
            if (visitedMethods.contains(currentMethod)) { continue; }
            else visitedMethods.add(currentMethod);

            // 处理静态变量访问
            Map<SootField, Set<Stmt>> temp = getStaticVarAccessMap(currentMethod);
            for (SootField f : temp.keySet()) {
                fields1AccessStmts.computeIfAbsent(f, k -> new HashSet<>()).addAll(temp.get(f));
                fields1AccessMethodChain.computeIfAbsent(f, k -> new ArrayList<>()).add(new ArrayList<>(currentChain));
            }

            // 深度检查：当前深度达到maxDepth时不再继续搜索
            if (currentChain.size() >= maxDepth) { continue; }

            // 遍历子方法
            Iterator<Edge> iterator = SeverExtraction.cg.edgesOutOf(currentMethod);
            while (iterator.hasNext()) {
                Edge edge = iterator.next();
                SootMethod callee = edge.getTgt().method();

                // 生成新调用链并检查深度
                List<SootMethod> newChain = new ArrayList<>(currentChain);
                newChain.add(callee);
                if (newChain.size() <= maxDepth && !visitedMethods.contains(callee)) {  // 仅当新深度不超过限制时加入队列
                    queue.add(new Pair<>(callee, newChain));
                }
            }
        }
    }

    public static Map<SootField, Set<Stmt>> getStaticVarAccessMap(SootMethod method) {
        Map<SootField, Set<Stmt>> fieldAccessMap = new HashMap<>();
        if (!method.hasActiveBody()) return fieldAccessMap;
        SootClass sootClass = method.getDeclaringClass();
        Body body = method.retrieveActiveBody();

        for (Unit unit : body.getUnits()) {
            Stmt stmt = (Stmt) unit;
            List<ValueBox> boxes = new ArrayList<>();
            boxes.addAll(stmt.getUseBoxes());
            boxes.addAll(stmt.getDefBoxes());

            for (ValueBox box : boxes) {
                Value value = box.getValue();
                if (value instanceof StaticFieldRef) {
                    SootField field = ((StaticFieldRef) value).getField();
//                    if (field != null) {
//                        Log.i(field.getSignature().contains("org.slf4j"), field.isFinal(), field.isPrivate(), isAllUpperCase(field.getName()));
//                    }
                    if (field == null || field.getSignature().contains("org.slf4j") || field.isFinal() || field.isPrivate() || isAllUpperCase(field.getName())) continue;
                    if (!fieldAccessMap.containsKey(field)) {
                        fieldAccessMap.put(field, new HashSet<Stmt>(){{add(stmt);}});
                    } else {
                        fieldAccessMap.get(field).add(stmt);
                    }

//                    if (field.getDeclaringClass().equals(sootClass)) {
//                        if (!fieldAccessMap.containsKey(field)) {
//                            fieldAccessMap.put(field, new HashSet<Stmt>(){{add(stmt);}});
//                        } else {
//                            fieldAccessMap.get(field).add(stmt);
//                        }
//                    }
                }
            }
        }
        return fieldAccessMap;
    }

    public static boolean isAllUpperCase(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

}
