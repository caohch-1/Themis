package indi.dc.extraction.impact;

import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.sameaccess.AccessSite;
import indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis;
import indi.dc.extraction.readwrite.RWSameAccessSite;
import indi.dc.extraction.readwrite.ReadWriteAnalysis;
import indi.dc.extraction.utils.MethodOverrideChecker;
import indi.dc.extraction.utils.RPCUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

import static indi.dc.extraction.readwrite.CollectionConfig.type2writeFunc;

public class ImpactAnalysis {
    public static Set<ImpactRWSameAccessSite> getImpactSitesWhole(Set<RWSameAccessSite> rwSameAccessSites) {
        Set<ImpactRWSameAccessSite>  impactSites = new HashSet<ImpactRWSameAccessSite>();
        for (RWSameAccessSite rwSameAccessSite : rwSameAccessSites) {
            Set<ImpactRWSameAccessSite> temp = getImpactSitesSameAccess(rwSameAccessSite);
            impactSites.addAll(temp);
        }

        return impactSites;
    }

    public static Set<ImpactRWSameAccessSite> getImpactSitesSameAccess(RWSameAccessSite rwSameAccessSite) {
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = new HashSet<>();
        SootField rootCauseField = rwSameAccessSite.sameAccessClsVar;
        Set<ImpactRWSameAccessSite.SymptomSite> symptomSites = new HashSet<>();

        if (rootCauseField != null && !rootCauseField.isStatic()) {
            symptomSites.addAll(getImpactSitesAccess(rwSameAccessSite.accessSite1));
            symptomSites.addAll(getImpactSitesAccess(rwSameAccessSite.accessSite2));

            // 如果该函数是RPCImpl中的函数，进一步分析该RPCImpl中的其他access该field的RPC函数（除了两个root cause function外）会不会lead symptom
            if (RPCUtils.isRPCImplField(rootCauseField)) {
                SootMethod rootCauseMethod1 = rwSameAccessSite.accessSite1.sootMethod;
                SootMethod rootCauseMethod2 = rwSameAccessSite.accessSite2.sootMethod;
                for (SootMethod potientialSymptomMethod : rootCauseField.getDeclaringClass().getMethods()) {
                    if (!potientialSymptomMethod.getSignature().equals(rootCauseMethod1.getSignature()) && !potientialSymptomMethod.getSignature().equals(rootCauseMethod2.getSignature()) && !MethodOverrideChecker.isOverrideMethod(potientialSymptomMethod)) continue;
                    Map<SootField, Set<Stmt>> fieldAccessMap = SameClassVariableAccessAnalysis.getInsVarAccessMap(potientialSymptomMethod);
                    if (fieldAccessMap.containsKey(rootCauseField)) {
                        symptomSites.addAll(getImpactSitesAccess(new AccessSite(potientialSymptomMethod, fieldAccessMap.get(rootCauseField), rootCauseField, null)));
                    }
                }
            }
        } else if (rootCauseField != null && rootCauseField.isStatic()) {
            symptomSites.addAll(getImpactSitesAccessStatic(rwSameAccessSite.accessSite1));
            symptomSites.addAll(getImpactSitesAccessStatic(rwSameAccessSite.accessSite2));

            // 如果该函数是RPCImpl中的函数，进一步分析该RPCImpl中的其他access该field的RPC函数（除了两个root cause function外）会不会lead symptom
            if (RPCUtils.isRPCImplField(rootCauseField)) {
                SootMethod rootCauseMethod1 = rwSameAccessSite.accessSite1.sootMethod;
                SootMethod rootCauseMethod2 = rwSameAccessSite.accessSite2.sootMethod;
                for (SootMethod potientialSymptomMethod : rootCauseField.getDeclaringClass().getMethods()) {
                    if (!potientialSymptomMethod.getSignature().equals(rootCauseMethod1.getSignature()) && !potientialSymptomMethod.getSignature().equals(rootCauseMethod2.getSignature()) && !MethodOverrideChecker.isOverrideMethod(potientialSymptomMethod)) continue;
                    Map<SootField, Set<Stmt>> fieldAccessMap = SameClassVariableAccessAnalysis.getInsVarAccessMap(potientialSymptomMethod);
                    if (fieldAccessMap.containsKey(rootCauseField)) {
                        symptomSites.addAll(getImpactSitesAccessStatic(new AccessSite(potientialSymptomMethod, fieldAccessMap.get(rootCauseField), rootCauseField, null)));
                    }
                }
            }
        }


        for (ImpactRWSameAccessSite.SymptomSite symptomSite : symptomSites) {
            impactRWSameAccessSites.add(new ImpactRWSameAccessSite(rwSameAccessSite, symptomSite));
        }

        return impactRWSameAccessSites;
    }

    public static Set<ImpactRWSameAccessSite.SymptomSite>  getImpactSitesAccess(AccessSite accessSite) {
        SootField rootCauseClsVar = accessSite.accessVariable;
        SootMethod rootCauseMethod = accessSite.sootMethod;
        SootClass declaringClass = rootCauseMethod.getDeclaringClass();
        Set<ImpactRWSameAccessSite.SymptomSite> symptomSites = new HashSet<>();

//        if (accessSite.accessVariable != null) {
//            if (type2writeFunc.containsKey(accessSite.accessVariable.getType().toString())) {
//                System.out.println(accessSite.accessVariable);
//                System.out.println(accessSite.sootMethod);
//            }
//        }

        try {
            if (!rootCauseMethod.hasActiveBody()) return symptomSites;
            Body jimpleBody = rootCauseMethod.retrieveActiveBody();
            Set<Value> fieldLocals = new HashSet<>();

            Local thisClsRef = (Local) jimpleBody.getThisLocal();
            for (Unit unit : jimpleBody.getUnits()) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;
                    // 检查右值是否为目标字段的读操作（如 r1 = r0.<MyClass: int field>）
                    if (assign.getRightOp() instanceof JInstanceFieldRef) {
                        JInstanceFieldRef fieldRef = (JInstanceFieldRef) assign.getRightOp();
                        if (ReadWriteAnalysis.isValidAccess(fieldRef, thisClsRef, rootCauseClsVar, declaringClass)) {
                            fieldLocals.add((Local) assign.getLeftOp());
                        }
                    } else if (assign.getRightOp() instanceof Local) {
                        Local local = (Local) assign.getRightOp();
                        if (fieldLocals.contains(local)) {
                            fieldLocals.add((Local) assign.getLeftOp());
                        }
                    }
                }
            }
            analyzeMethodImpact(rootCauseMethod, fieldLocals, rootCauseClsVar, symptomSites, new HashSet<>(), 1, new ArrayList<>(), new ArrayList<>());

        } catch (Exception e) {
//            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
            e.printStackTrace();
        }
        return symptomSites;
    }

    public static Set<ImpactRWSameAccessSite.SymptomSite>  getImpactSitesAccessStatic(AccessSite accessSite) {
        SootField rootCauseClsVar = accessSite.accessVariable;
        Set<ImpactRWSameAccessSite.SymptomSite> symptomSites = new HashSet<>();
        for (List<SootMethod> chain : accessSite.chain2StaticAccess) {
            SootMethod rootCauseMethod = chain.get(chain.size() - 1);
            SootClass declaringClass = rootCauseMethod.getDeclaringClass();

            try {
                if (!rootCauseMethod.hasActiveBody()) continue;
                Body jimpleBody = rootCauseMethod.retrieveActiveBody();
                Set<Value> fieldLocals = new HashSet<>();

                for (Unit unit : jimpleBody.getUnits()) {
                    if (unit instanceof JAssignStmt) {
                        JAssignStmt assign = (JAssignStmt) unit;
                        // 检查右值是否为目标字段的读操作（如 r1 = r0.<MyClass: int field>）
                        if (assign.getRightOp() instanceof StaticFieldRef) {
                            StaticFieldRef fieldRef = (StaticFieldRef) assign.getRightOp();
                            if (ReadWriteAnalysis.isValidAccessStatic(fieldRef, rootCauseClsVar, declaringClass)) {
                                fieldLocals.add(assign.getLeftOp());
                            }
                        }
                    }
                }
                analyzeMethodImpact(rootCauseMethod, fieldLocals, rootCauseClsVar, symptomSites, new HashSet<>(), 1, new ArrayList<>(), new ArrayList<>());

            } catch (Exception e) {
//            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
                e.printStackTrace();
            }

        }

        return symptomSites;
    }

    public static Set<ImpactRWSameAccessSite> getImpactSitesWholeClient(Set<RWSameAccessSite> rwSameAccessSites) {
        Set<ImpactRWSameAccessSite>  impactSites = new HashSet<ImpactRWSameAccessSite>();
        for (RWSameAccessSite rwSameAccessSite : rwSameAccessSites) {
            Set<ImpactRWSameAccessSite> temp = getImpactSitesSameAccessClient(rwSameAccessSite);
            impactSites.addAll(temp);
        }

        return impactSites;
    }

    public static Set<ImpactRWSameAccessSite> getImpactSitesSameAccessClient(RWSameAccessSite rwSameAccessSite) {
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = new HashSet<>();
        SootField rootCauseField = rwSameAccessSite.sameAccessClsVar;
        Set<ImpactRWSameAccessSite.SymptomSite> symptomSites = new HashSet<>();

        symptomSites.addAll(getImpactSitesAccessClient(rwSameAccessSite.accessSite1));
        symptomSites.addAll(getImpactSitesAccessClient(rwSameAccessSite.accessSite2));

        for (ImpactRWSameAccessSite.SymptomSite symptomSite : symptomSites) {
            impactRWSameAccessSites.add(new ImpactRWSameAccessSite(rwSameAccessSite, symptomSite));
        }

        return impactRWSameAccessSites;
    }

    public static Set<ImpactRWSameAccessSite.SymptomSite>  getImpactSitesAccessClient(AccessSite accessSite) {
        SootField rootCauseClsVar = accessSite.accessVariable;
        SootMethod rootCauseMethod = accessSite.sootMethod;
        SootClass declaringClass = rootCauseMethod.getDeclaringClass();
        Set<ImpactRWSameAccessSite.SymptomSite> symptomSites = new HashSet<>();

        try {
            if (!rootCauseMethod.hasActiveBody()) return symptomSites;
            Body jimpleBody = rootCauseMethod.retrieveActiveBody();
            Set<Value> fieldLocals = new HashSet<>();

            Local thisClsRef = (Local) jimpleBody.getThisLocal();
            for (Unit unit : jimpleBody.getUnits()) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;
                    // 检查右值是否为目标字段的读操作（如 r1 = r0.<MyClass: int field>）
                    if (assign.getRightOp() instanceof FieldRef) {
                        FieldRef fieldRef = (FieldRef) assign.getRightOp();
                        if (ReadWriteAnalysis.isValidAccessCommon(fieldRef, thisClsRef, rootCauseClsVar, declaringClass)) {
                            fieldLocals.add((Local) assign.getLeftOp());
                        }
                    }
                }
            }

            analyzeMethodImpactClient(rootCauseMethod, fieldLocals, rootCauseClsVar, symptomSites, new HashSet<>(), 0, new ArrayList<>(), new ArrayList<>());

        } catch (Exception e) {
//            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
            e.printStackTrace();
        }
        return symptomSites;
    }

    private static final int MAX_RECURSION_DEPTH = 3;

    public static void analyzeMethodImpactClient(SootMethod method, Set<Value> paramLocals, SootField targetField, Set<ImpactRWSameAccessSite.SymptomSite> results, Set<SootMethod> visitedMethods, int depth, List<SootMethod> callChian, List<Stmt> rpcCallSiteChain) {
        // 终止条件检查
        if (depth > MAX_RECURSION_DEPTH) {
//            Log.i("[Warn] ", "Max recursion depth reached for: " + method.getName());
            return;
        }
        if (visitedMethods.contains(method)) return;


        visitedMethods.add(method);


        try {
            if (!method.hasActiveBody()) return;
            Body body = method.retrieveActiveBody();
            UnitGraph cfg = new BriefUnitGraph(body);
            callChian.add(method);

            // 分析当前method中的ifStmt是否涉及兴趣变量以及导致symptom
            analyzeConditionsInMethod(body, paramLocals, targetField, results, cfg, callChian, rpcCallSiteChain);

            Set<Value> retValues = new HashSet<>();
            for (Unit unit : body.getUnits()) {
                if (((Stmt) unit).containsInvokeExpr()) {
                    if (unit instanceof AssignStmt) {
                        retValues.add(((AssignStmt) unit).getLeftOp());
                    }
                }

                if (unit instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) unit;
                    Value returnValue = returnStmt.getOp();
                    // 检查返回值是否与兴趣变量相关
                    if (isValueRelated(returnValue, paramLocals, targetField)) {
                        processReturnValueImpact(method, targetField, results, new HashSet<>(visitedMethods), depth, callChian, rpcCallSiteChain);
                    }
                }
            }

            analyzeConditionsInMethod(body, retValues, targetField, results, cfg, callChian, rpcCallSiteChain);


        } catch (Exception e) {
//            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
            e.printStackTrace();
        }
    }

    private static void processReturnValueImpact(SootMethod calleeMethod, SootField targetField, Set<ImpactRWSameAccessSite.SymptomSite> results, Set<SootMethod> visitedMethods, int depth, List<SootMethod> callChain, List<Stmt> rpcCallSiteChain) {CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> edges = cg.edgesInto(calleeMethod);

        while (edges.hasNext()) {
            Edge edge = edges.next();
            Stmt callSiteStmt = edge.srcStmt();
            SootMethod callerMethod = edge.getSrc().method();

            if (callSiteStmt instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) callSiteStmt;
                Value leftOp = assignStmt.getLeftOp();
                if (leftOp instanceof FieldRef) {
                    FieldRef callerFieldRef = (FieldRef) leftOp;
                    SootField callerField = callerFieldRef.getField();

                    if (callChain.contains(callerMethod)) return;
                    // 构建新的调用链并递归分析调用者方法
                    List<SootMethod> newCallChain = new ArrayList<>(callChain);
                    newCallChain.add(callerMethod);
                    analyzeMethodImpactClient(callerMethod, new HashSet<>(), callerField, results, new HashSet<>(visitedMethods), depth + 1, newCallChain, rpcCallSiteChain);
                } else {
                    Set<Value> newParamLocals = new HashSet<>();
                    newParamLocals.add(leftOp);

                    if (callChain.contains(callerMethod)) return;
                    // 构建新的调用链并递归分析调用者方法
                    List<SootMethod> newCallChain = new ArrayList<>(callChain);
                    newCallChain.add(callerMethod);
                    analyzeMethodImpactClient(callerMethod, newParamLocals, targetField, results, new HashSet<>(visitedMethods), depth + 1, newCallChain, rpcCallSiteChain);
                }
            }
        }
    }

    public static void analyzeMethodImpact(SootMethod method, Set<Value> paramLocals, SootField targetField, Set<ImpactRWSameAccessSite.SymptomSite> results, Set<SootMethod> visitedMethods, int depth, List<SootMethod> callChian, List<Stmt> rpcCallSiteChain) {
        // 终止条件检查
        if (depth > MAX_RECURSION_DEPTH || method.isJavaLibraryMethod()) {
//            Log.i("[Warn] ", "Max recursion depth reached for: " + method.getName());
            return;
        }
        if (visitedMethods.contains(method)) return;


        visitedMethods.add(method);


        try {
            if (!method.hasActiveBody()) return;
            Body body = method.retrieveActiveBody();
            UnitGraph cfg = new BriefUnitGraph(body);
            callChian.add(method);
            LoopFinder loopFinder = new LoopFinder();
            Collection<Loop> loops = loopFinder.getLoops(body);
            Set<Value> retValues = new HashSet<>();

            for (Unit unit : body.getUnits()) {
                 // 变量传递 保守
//                if (unit instanceof AssignStmt && !((AssignStmt) unit).containsInvokeExpr()) {
//                    AssignStmt assignStmt = (AssignStmt) unit;
//                    Value leftOp = assignStmt.getLeftOp();
//
//                    for (ValueBox useBox : unit.getUseBoxes()) {
//                        Value useValue = useBox.getValue();
//                        if (targetField != null && useValue instanceof FieldRef) {
//                            FieldRef targetFieldRef = (FieldRef) useValue;
//                            if (targetFieldRef.getField().getName().equals(targetField.getName())) {
//                                paramLocals.add(leftOp);
//                            }
//                        } else if (useValue instanceof Local && paramLocals.contains((Local) useValue)) {
//                            paramLocals.add(leftOp);
//                        }
//                    }
//                } else if (unit instanceof AssignStmt && ((AssignStmt) unit).containsInvokeExpr()) {
//                    AssignStmt assignStmt = (AssignStmt) unit;
//                    Value leftOp = assignStmt.getLeftOp();
//
//                    for (ValueBox useBox : unit.getUseBoxes()) {
//                        Value useValue = useBox.getValue();
//
//                        if (type2writeFunc.containsKey(useValue.getType().toString())) {
//                            if (targetField != null && useValue instanceof FieldRef) {
//                                FieldRef targetFieldRef = (FieldRef) useValue;
//                                if (targetFieldRef.getField().getName().equals(targetField.getName())) {
//                                    paramLocals.add(leftOp);
//                                }
//                            } else if (useValue instanceof Local && paramLocals.contains((Local) useValue)) {
//                                paramLocals.add(leftOp);
//                            }
//                        }
//                    }
//                }

                // 变量传递 激进
                if (unit instanceof AssignStmt) {
                    AssignStmt assignStmt = (AssignStmt) unit;
                    Value leftOp = assignStmt.getLeftOp();

                    for (ValueBox useBox : unit.getUseBoxes()) {
                        Value useValue = useBox.getValue();
                        if (targetField != null && useValue instanceof FieldRef) {
                            FieldRef targetFieldRef = (FieldRef) useValue;
                            if (targetFieldRef.getField().getName().equals(targetField.getName())) {
                                paramLocals.add(leftOp);
                            }
                        } else if (useValue instanceof Local && paramLocals.contains((Local) useValue)) {
                            paramLocals.add(leftOp);
                        }
                    }
                }


                // 分析当前method中的ifStmt是否涉及兴趣变量以及导致symptom
                if (unit instanceof IfStmt) {
                    IfStmt ifStmt = (IfStmt) unit;
                    if (isConditionRelated(ifStmt.getCondition(), paramLocals, targetField)) {
                        checkExceptionBranch(ifStmt.getTarget(), results, cfg, callChian, rpcCallSiteChain);
                        checkExceptionBranch(body.getUnits().getSuccOf(unit), results, cfg, callChian, rpcCallSiteChain);
                    }
                }
                for (Loop loop : loops) {
                    for (Unit exits : loop.getLoopExits()) {
                        if (unit.equals(exits) && exits instanceof IfStmt) {
                            IfStmt ifStmt = (IfStmt) exits;
                            if (isConditionRelated(ifStmt.getCondition(), paramLocals, targetField)) {
                                boolean modified = checkLoopVariableModification(loop.getLoopStatements(), paramLocals, targetField);
                                if (!modified) {
                                    results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, ifStmt, callChian, ImpactRWSameAccessSite.SymptomType.Loop));
                                }
                            }
                        }
                    }
                }

                // 分析当前method中的invokeStmt是否涉及兴趣变量，并进入其中递归分析
                if (((Stmt) unit).containsInvokeExpr()) {
                    processMethodInvoke((Stmt) unit, paramLocals, targetField, results, new HashSet<>(visitedMethods), depth, new ArrayList<>(callChian), rpcCallSiteChain);
                    if (unit instanceof AssignStmt) {
                        retValues.add(((AssignStmt) unit).getLeftOp());
                    }
                }
            }



//            analyzeConditionsInMethod(body, retValues, targetField, results, cfg, callChian, rpcCallSiteChain);
        } catch (Exception e) {
//            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
            e.printStackTrace();
        }
    }

    public static void analyzeConditionsInMethod(Body body, Set<Value> currentLocals, SootField targetField, Set<ImpactRWSameAccessSite.SymptomSite> results, UnitGraph cfg, List<SootMethod> callChian, List<Stmt> rpcCallSiteChain) {
        // 造成Exception, Error Log, or System abort
        for (Unit unit : body.getUnits()) {
            if (unit instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) unit;
                if (isConditionRelated(ifStmt.getCondition(), currentLocals, targetField)) {
                    checkExceptionBranch(ifStmt.getTarget(), results, cfg, callChian, rpcCallSiteChain);
                    checkExceptionBranch(body.getUnits().getSuccOf(unit), results, cfg, callChian, rpcCallSiteChain);
                }
            }
        }

        // 造成Infinte Loop
        // Todo: 可能有问题比如嵌套循环
        LoopFinder loopFinder = new LoopFinder();
        Collection<Loop> loops = loopFinder.getLoops(body);
        for (Loop loop : loops) {
            for (Unit exits : loop.getLoopExits()) {
                if (exits instanceof IfStmt) {
                    IfStmt ifStmt = (IfStmt) exits;
                    if (isConditionRelated(ifStmt.getCondition(), currentLocals, targetField)) {
                        boolean modified = checkLoopVariableModification(loop.getLoopStatements(), currentLocals, targetField);
                        if (!modified) {
                            results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, ifStmt, callChian, ImpactRWSameAccessSite.SymptomType.Loop));
                        }
                    }
                }
            }
        }
    }
    // Todo: collection variable write
    private static boolean checkLoopVariableModification(List<Stmt> loopBody, Set<Value> locals, SootField field) {
        for (Unit unit : loopBody) {
            if (unit instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) unit;
                Value lhs = assign.getLeftOp();

                // 检查局部变量修改
                if (locals.contains(lhs)) {
                    return true;
                }

                // 检查目标字段修改
                if (field != null) {
                    if (lhs instanceof FieldRef) {
                        FieldRef ref = (FieldRef) lhs;
                        if (ref.getField().equals(field)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isConditionRelated(Value condition, Set<Value> fieldLocals, SootField rootCauseClsVar) {
        Queue<Value> queue = new LinkedList<>();
        queue.add(condition);
        Set<Value> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Value current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current instanceof Local || current instanceof FieldRef || current instanceof ArrayRef) {
                if (fieldLocals.contains(current)) {
                    return true;
                }
            }

            if (rootCauseClsVar != null) {
                if (current instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) current;
                    if (fieldRef.getField().equals(rootCauseClsVar) || fieldLocals.contains(current)) {
                        return true;
                    }
                }
            }

            for (ValueBox box : current.getUseBoxes()) {
                queue.add(box.getValue());
            }
        }
        return false;
    }

    // 检查异常分支并记录结果
    private static void checkExceptionBranch(Unit branchStart, Set<ImpactRWSameAccessSite.SymptomSite> results, UnitGraph cfg, List<SootMethod> callChain, List<Stmt> rpcCallSiteChain) {
        if (branchStart == null) return;

        Queue<Unit> queue = new LinkedList<>();
        Set<Unit> visited = new HashSet<>();
        queue.add(branchStart);

        while (!queue.isEmpty()) {
            Unit current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current instanceof ThrowStmt) {
                ThrowStmt throwStmt = (ThrowStmt) current;
                Value exception = throwStmt.getOp();
                Type exceptionType = exception.getType();
                if (exceptionType instanceof RefType) {
                    SootClass exceptionClass = ((RefType) exceptionType).getSootClass();
                    if (isSubclassOfRuntimeException(exceptionClass)) {
                        results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, (Stmt) current, callChain, ImpactRWSameAccessSite.SymptomType.Exception));
                    }
                }
            } else if (current instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) current;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                SootMethod method = invokeExpr.getMethod();
                String methodClass = method.getDeclaringClass().getName();

                if (methodClass.equals("org.slf4j.Logger") && method.getName().equals("error")) {
                    results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, (Stmt) current, callChain, ImpactRWSameAccessSite.SymptomType.Log));
                } else if (methodClass.equals("java.lang.System") && method.getName().equals("exit")) {
                    Value exitCode = invokeExpr.getArg(0);
                    if (exitCode instanceof IntConstant) {
                        int code = ((IntConstant) exitCode).value;
                        if (code != 0) {
                            results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, (Stmt) current, callChain, ImpactRWSameAccessSite.SymptomType.Abort));
                        }
                    } else {
                        results.add(new ImpactRWSameAccessSite.SymptomSite(rpcCallSiteChain, (Stmt) current, callChain, ImpactRWSameAccessSite.SymptomType.Abort));
                    }
                }
            }

            for (Unit successUnit : cfg.getSuccsOf(current)) {
                if (visited.contains(successUnit)) continue;
                if (successUnit instanceof IfStmt) continue;
                queue.add(successUnit);
            }
        }
    }

    private static boolean isSubclassOfRuntimeException(SootClass cls) {
        SootClass runtimeExceptionClass = Scene.v().getSootClass("java.lang.RuntimeException");
        return Scene.v().getActiveHierarchy().isClassSubclassOf(cls, runtimeExceptionClass);
    }

    // 处理单个方法调用语句
    private static void processMethodInvoke(Stmt invoke, Set<Value> callerLocals, SootField targetField, Set<ImpactRWSameAccessSite.SymptomSite> results, Set<SootMethod> visitedMethods, int depth, List<SootMethod> callChian, List<Stmt> rpcCallSiteChain) {
        SootMethod callee = invoke.getInvokeExpr().getMethod();

        // RPC调用替换
        Set<SootMethod> realCallees = RPCUtils.findRPCImplMethods(invoke);

        if (realCallees == null || realCallees.isEmpty()) {
            // 获取与被调用方法参数对应的敏感变量
            Map<Integer, Local> paramMapping = mapRelevantParameters(invoke.getInvokeExpr(), callerLocals, targetField, callee);
            if (!paramMapping.isEmpty() && callee.hasActiveBody()) {
                // 构建被调用方法的新参数集合
                Set<Value> calleeParams = new HashSet<>(paramMapping.values());
                // 递归分析
                analyzeMethodImpact(callee, calleeParams, targetField, results, new HashSet<>(visitedMethods), depth + 1, callChian, rpcCallSiteChain);
            }
        } else {
            for (SootMethod realCallee : realCallees) {
                // 获取与被调用方法参数对应的敏感变量
                Map<Integer, Local> paramMapping = mapRelevantParameters(invoke.getInvokeExpr(), callerLocals, targetField, realCallee);
                if (!paramMapping.isEmpty() && realCallee.hasActiveBody()) {
                    // 构建被调用方法的新参数集合
                    Set<Value> calleeParams = new HashSet<>(paramMapping.values());
                    // 递归分析
                    rpcCallSiteChain.add(invoke);
                    analyzeMethodImpact(realCallee, calleeParams, targetField, results, new HashSet<>(visitedMethods), depth + 1, callChian, rpcCallSiteChain);
                }
            }
        }
    }

    // 关键方法：建立调用参数与被调用方法参数的映射
    private static Map<Integer, Local> mapRelevantParameters(InvokeExpr invokeExpr, Set<Value> callerLocals, SootField targetField, SootMethod callee) {
        Map<Integer, Local> paramMap = new HashMap<>();
        // 没有方法体则直接返回
        if (!callee.hasActiveBody()) return paramMap;

        List<Value> args = new ArrayList<>();

        // 处理不同调用类型
        if (invokeExpr instanceof InstanceInvokeExpr) {
            InstanceInvokeExpr iie = (InstanceInvokeExpr) invokeExpr;
            args.add(iie.getBase()); // 接收者对象作为第0个参数
            args.addAll(iie.getArgs());
        } else {
            args.addAll(invokeExpr.getArgs());
        }

        // 获取被调用方法的参数本地变量
        try {
            if (!callee.hasActiveBody()) return paramMap;
            Body calleeBody = callee.retrieveActiveBody();
            List<Local> calleeParams = calleeBody.getParameterLocals();

            for (int i = 0; i < args.size(); i++) {
                Value arg = args.get(i);
                // 检查参数是否与敏感变量相关
                if (isValueRelated(arg, callerLocals, targetField)) {
                    // 注意参数索引偏移：实例方法的第一个参数是接收者
                    int calleeParamIndex = adjustParamIndex(invokeExpr, i);
                    Local paramLocal = calleeParamIndex >= 0 ? calleeParams.get(calleeParamIndex) : calleeBody.getThisLocal();
                    paramMap.put(i, paramLocal);
                }

            }

        } catch (Exception e) {
            Log.i("[Error] ", "In ", new Object(){}.getClass().getEnclosingMethod().getName(), ": ", e.getMessage().replace("\n", ""));
        }

        return paramMap;
    }

    // 判断值是否与当前敏感变量相关
    private static boolean isValueRelated(Value value, Set<Value> currentLocals, SootField targetField) {
        // 广度优先搜索检查变量相关性
        Queue<Value> queue = new LinkedList<>();
        Set<Value> visited = new HashSet<>();
        queue.add(value);

        while (!queue.isEmpty()) {
            Value current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            // 检查本地变量
            if (currentLocals.contains(current)) {
                return true;
            }

            if (targetField != null){// 检查字段访问
                if (current instanceof FieldRef && ((FieldRef) current).getField().equals(targetField)) {
                    return true;
                }
            }

            // 递归检查子表达式
            for (ValueBox box : current.getUseBoxes()) {
                queue.add(box.getValue());
            }
        }
        return false;
    }

    // 调整参数索引的辅助方法
    private static int adjustParamIndex(InvokeExpr expr, int argIndex) {
        if (expr instanceof InstanceInvokeExpr) {
            // 实例方法：argIndex=0对应this，实际参数从1开始
            return argIndex == 0 ? -1 : argIndex - 1;
        }
        return argIndex; // 静态方法直接对应
    }

}
