package indi.dc.access;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.util.Log;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.JimpleBody;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;

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

    public static Set<SameAccessSite> findSameClsVarAccesses(SootMethod method1, SootMethod method2, Set<Analyzer.CallSite> rpcCallSites) {
        Map<SootField, Set<Stmt>> fields1 = getClsVarAccessMap(method1);
        Map<SootField, Set<Stmt>> fields2 = getClsVarAccessMap(method2);

        Set<SootField> commonFields = new HashSet<>(fields1.keySet());
        commonFields.retainAll(fields2.keySet());

        Set<SameAccessSite> sameAccessSites = new HashSet<>();
        for (SootField field : commonFields) {

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

    public static Set<SameAccessSite> findSameClsVarAccessesWhole(Set<SootClass> targetClasses) {
        Set<SootField> staticFields = extractStaticFields(targetClasses);
        Set<SameAccessSite> sameClsAccesses = findAccessingMethodPairs(targetClasses, staticFields);
        return sameClsAccesses;
    }

    private static Set<SootField> extractStaticFields(Set<SootClass> classes) {
        Set<SootField> staticFields = new HashSet<>();
        for (SootClass sc : classes) {
            for (SootField sf : sc.getFields()) {
                if (sf.isStatic() && !sf.isFinal()) {
                    staticFields.add(sf);
                }
            }
        }
        return staticFields;
    }

    public static Set<SameAccessSite> findAccessingMethodPairs(Set<SootClass> classes, Set<SootField> staticFields) {
        Map<SootField, Map<SootMethod, Set<Stmt>>> fieldToMethods = new HashMap<>();

        // 为每个静态字段查找访问方法
        for (SootField field : staticFields) {
            Map<SootMethod, Set<Stmt>> methodAccessMap = new HashMap<>();
            for (SootClass sc : classes) {
                for (SootMethod method : sc.getMethods()) {
                    if (!method.isConcrete() || !method.hasActiveBody() || method.getName().equals("<init>") || method.getName().equals("<clinit>")) continue;
                    try {
                        Body body = method.retrieveActiveBody();
                        Set<Stmt> accessStmts = findAccessesStmts(body, field);
                        if (!accessStmts.isEmpty()) {
                            methodAccessMap.put(method, accessStmts);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            fieldToMethods.put(field, methodAccessMap);
        }

        // 生成方法配对
        Set<SameAccessSite> sameAccessSites = new HashSet<>();
        for (Map.Entry<SootField, Map<SootMethod, Set<Stmt>>> entry : fieldToMethods.entrySet()) {
            Map<SootMethod, Set<Stmt>> methodWithAccessStmts = entry.getValue();
            List<SootMethod> sootMethods = new ArrayList<>(methodWithAccessStmts.keySet());

            for (int i = 0; i < sootMethods.size(); i++) {
                for (int j = i + 1; j < sootMethods.size(); j++) {
                    SootMethod m1 = sootMethods.get(i);
                    SootMethod m2 = sootMethods.get(j);
                    sameAccessSites.add(new SameAccessSite(
                            new AccessSite(m1, methodWithAccessStmts.get(m1), entry.getKey(), null),
                            new AccessSite(m2, methodWithAccessStmts.get(m2), entry.getKey(), null))
                    );
                }
            }

        }
        return sameAccessSites;
    }

    private static Set<Stmt> findAccessesStmts(Body body, SootField targetField) {
        Set<Stmt> result = new HashSet<>();
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
                    if (field.equals(targetField)) result.add(stmt);
                }
            }
        }
        return result;
    }

    public static Set<SameAccessSite> findLocalVarAccesses(SootMethod sootMethod) {
        Set<SameAccessSite> sameLocalAccesses = findAccessingMethodPairs(sootMethod);
        return sameLocalAccesses;
    }

    public static Set<SameAccessSite> findAccessingMethodPairs(SootMethod sootMethod) {
        Map<Local, Set<Stmt>> localToMethods = new HashMap<>();
        if (!sootMethod.hasActiveBody()) return new HashSet<>();

        for (Local local : sootMethod.retrieveActiveBody().getLocals()) {
            Set<Stmt> invokeStmtAccessLocal = findAccessesStmts(sootMethod.retrieveActiveBody(), local);

            localToMethods.put(local, invokeStmtAccessLocal);
        }

        // 生成方法配对
        Set<SameAccessSite> sameAccessSites = new HashSet<>();
        for (Map.Entry<Local, Set<Stmt>> entry : localToMethods.entrySet()) {
            Set<Stmt> accessStmts = entry.getValue();
            List<Stmt> accessStmtList = new ArrayList<>(accessStmts);


            for (int i = 0; i < accessStmtList.size(); i++) {
                for (int j = i + 1; j < accessStmtList.size(); j++) {
                    int finalI = i;
                    int finalJ = j;
                    sameAccessSites.add(new SameAccessSite(
                            new AccessSite(sootMethod, new HashSet<Stmt>(){{add(accessStmtList.get(finalI));}}, entry.getKey(), null),
                            new AccessSite(sootMethod, new HashSet<Stmt>(){{add(accessStmtList.get(finalJ));}}, entry.getKey(), null))
                    );
                }
            }

        }
        return sameAccessSites;
    }

    private static Set<Stmt> findAccessesStmts(Body body, Local tgtLocal) {
        Set<Stmt> result = new HashSet<>();
        for (Unit unit : body.getUnits()) {
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                List<ValueBox> boxes = new ArrayList<>();
                boxes.addAll(stmt.getUseBoxes());
                boxes.addAll(stmt.getDefBoxes());

                for (ValueBox box : boxes) {
                    Value value = box.getValue();
                    if (value instanceof Local) {
                        Local local = (Local) value;
                        if (local.getType().toString().contains("org.slf4j")) continue;
                        if (local.equals(tgtLocal)) {
                            result.add(stmt);
                        }
                    }
                }
            }

        }
        return result;
    }


}
