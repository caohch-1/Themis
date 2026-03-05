package indi.dc.extraction.readwrite;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.sameaccess.AccessSite;
import indi.dc.extraction.sameaccess.SameAccessSite;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.*;

import static indi.dc.extraction.readwrite.CollectionConfig.type2writeFunc;
import static indi.dc.extraction.utils.RPCUtils.findRPCImplMethods;
import static indi.dc.extraction.utils.RPCUtils.isRPCProtoMethod;

public class ReadWriteAnalysis {
    public static class MethodParamPair {
        final SootMethod method;
        final int paramIndex;

        MethodParamPair(SootMethod method, int paramIndex) {
            this.method = method;
            this.paramIndex = paramIndex;
        }
    }


    public static Set<RWSameAccessSite> getReadWriteWhole(Set<SameAccessSite> sameClsVarAccessSites) {
        Set<RWSameAccessSite> rwSameAccessSites = new HashSet<>();
        for (SameAccessSite sameAccessSite : sameClsVarAccessSites) {
            RWSameAccessSite rwSameAccessSite = getReadWriteSameAccess(sameAccessSite);
            if (rwSameAccessSite != null) {
                rwSameAccessSites.add(rwSameAccessSite);
            }
        }
        return rwSameAccessSites;
    }

    public static RWSameAccessSite getReadWriteSameAccess(SameAccessSite sameAccessSite) {
        RWSameAccessSite rwSameAccessSite = new RWSameAccessSite(sameAccessSite.accessSite1, sameAccessSite.accessSite2);
        rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccess(sameAccessSite.accessSite1);
        rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccess(sameAccessSite.accessSite2);
        if (rwSameAccessSite.accessSite1RWTypeMap.isEmpty() || rwSameAccessSite.accessSite2RWTypeMap.isEmpty()) {
            return null;
        }

        boolean ifRead = false;
        boolean ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite1RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Write;
        }

        ifRead = false;
        ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite2RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Write;
        }

        return rwSameAccessSite;
    }

    public static Map<Stmt, RWSameAccessSite.RWType> getReadWriteAccess(AccessSite accessSite) {
        SootField accessClsVar = accessSite.accessVariable;
        SootMethod accessMethod = accessSite.sootMethod;
        SootClass declaringClass = accessMethod.getDeclaringClass();
        Map<Stmt, RWSameAccessSite.RWType>  rwTypeMap = new HashMap<Stmt, RWSameAccessSite.RWType>();
        try {
            if (!accessMethod.hasActiveBody() || accessMethod.isStatic()) return rwTypeMap;
            Body jimpleBody = accessMethod.retrieveActiveBody();
            Local thisClsRef = (Local) jimpleBody.getThisLocal();
            boolean isNonPrimitive = !(accessClsVar.getType() instanceof PrimType);
            Set<Local> fieldLocals = new HashSet<>();
            // 新增：递归检查方法调用链的辅助集合
            Set<MethodParamPair> analyzedPairs = new HashSet<>();

            for (Unit unit : jimpleBody.getUnits()) {
                Stmt stmt = (Stmt) unit;
                boolean read = false;
                boolean write = false;

                if (type2writeFunc.containsKey(accessClsVar.getType().toString())) {
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        if (invokeExpr instanceof InstanceInvokeExpr) {
                            InstanceInvokeExpr invokeExprIns = (InstanceInvokeExpr) invokeExpr;
                            String methodName = invokeExprIns.getMethod().getName();
                            Value base = invokeExprIns.getBase();

                            boolean isRelated = (base instanceof Local && fieldLocals.contains(base)) || (base instanceof JInstanceFieldRef && isValidAccess((JInstanceFieldRef) base, thisClsRef, accessClsVar, declaringClass));
                            if (isRelated) {
                                if (type2writeFunc.get(accessClsVar.getType().toString()).contains(methodName)) {
                                    write = true;
                                } else {
                                    read = true;
                                }
                            }
                        }
                    }
                }

                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;

                    // 检查右值是否为目标字段的读操作（如 r1 = r0.<MyClass: int field>）
                    if (assign.getRightOp() instanceof JInstanceFieldRef) {
                        JInstanceFieldRef fieldRef = (JInstanceFieldRef) assign.getRightOp();
                        if (isValidAccess(fieldRef, thisClsRef, accessClsVar, declaringClass)) {
                            read = true;
                            fieldLocals.add((Local) assign.getLeftOp());
                        }
                    }

                    // 检查左值是否为目标字段的写操作（如 r0.<MyClass: int field> = r1）
                    if (assign.getLeftOp() instanceof JInstanceFieldRef) {
                        JInstanceFieldRef fieldRef = (JInstanceFieldRef) assign.getLeftOp();
                        if (isValidAccess(fieldRef, thisClsRef, accessClsVar, declaringClass)) {
                            write = true;
                        }
                    }

                    // 检查是否被传入函数调用
                    if (assign.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        List<Value> args = invokeExpr.getArgs();

                        for (int argIndex = 0; argIndex < args.size(); argIndex++) {
                            Value arg = args.get(argIndex);
                            // 判断参数是否与目标字段相关
                            boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg)) || (arg instanceof JInstanceFieldRef && isValidAccess((JInstanceFieldRef) arg, thisClsRef, accessClsVar, declaringClass));

                            if (isRelated) read = true;

                            if (isRelated && isNonPrimitive) {
                                // 递归检查调用链
                                if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                    write = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // 检查是否被传入函数调用
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        List<Value> args = invokeExpr.getArgs();

                        for (int argIndex = 0; argIndex < args.size(); argIndex++) {
                            Value arg = args.get(argIndex);
                            // 判断参数是否与目标字段相关
                            boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg)) || (arg instanceof JInstanceFieldRef && isValidAccess((JInstanceFieldRef) arg, thisClsRef, accessClsVar, declaringClass));
                            if (isRelated) read = true;

                            if (isRelated && isNonPrimitive) {
                                // 递归检查调用链
                                if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                    write = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        for (ValueBox valueBox : unit.getUseBoxes()) {
                            Value value = valueBox.getValue();
                            read = (value instanceof Local && fieldLocals.contains(value)) || (value instanceof JInstanceFieldRef && isValidAccess((JInstanceFieldRef) value, thisClsRef, accessClsVar, declaringClass));
                        }
                    }
                }

                if (read && write) {
                    rwTypeMap.put(stmt, RWSameAccessSite.RWType.ReadWrite);
                } else if (read) {
                    rwTypeMap.put(stmt, RWSameAccessSite.RWType.Read);
                } else if (write) {
                    rwTypeMap.put(stmt, RWSameAccessSite.RWType.Write);
                }
            }
        } catch (Exception e) {
            Log.i("[Error] ", e.getMessage().replace("\n", ""));
//            e.printStackTrace();
        }
        return rwTypeMap;
    }

    public static boolean isValidAccess(InstanceFieldRef fieldRef, Local targetLocal, SootField targetField, SootClass declaringClass) {
        return fieldRef.getBase().equals(targetLocal)
                && fieldRef.getField().equals(targetField)
                && fieldRef.getField().getDeclaringClass().equals(declaringClass);
    }

    public static boolean isValidAccessCommon(FieldRef fieldRef, Local targetLocal, SootField targetField, SootClass declaringClass) {
        if (fieldRef instanceof StaticFieldRef) {
            return isValidAccessStatic((StaticFieldRef) fieldRef, targetField, declaringClass);
        } else if (fieldRef instanceof InstanceFieldRef) {
            return isValidAccess((InstanceFieldRef) fieldRef, targetLocal, targetField, declaringClass);
        }
        return false;
    }

    public static Set<RWSameAccessSite> getReadWriteWholeClient(Set<SameAccessSite> sameClsVarAccessSites) {
        Set<RWSameAccessSite> rwSameAccessSites = new HashSet<>();
        for (SameAccessSite sameAccessSite : sameClsVarAccessSites) {
            RWSameAccessSite rwSameAccessSite = getReadWriteSameAccessClient(sameAccessSite);
            if (rwSameAccessSite != null) {
                rwSameAccessSites.add(rwSameAccessSite);
            }
        }
        return rwSameAccessSites;
    }

    public static RWSameAccessSite getReadWriteSameAccessClient(SameAccessSite sameAccessSite) {
        RWSameAccessSite rwSameAccessSite = new RWSameAccessSite(sameAccessSite.accessSite1, sameAccessSite.accessSite2);
        rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccessClient(sameAccessSite.accessSite1);
        rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccess(sameAccessSite.accessSite2);
        if (rwSameAccessSite.accessSite1RWTypeMap.isEmpty() || rwSameAccessSite.accessSite2RWTypeMap.isEmpty()) {
            return null;
        }

        boolean ifRead = false;
        boolean ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite1RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Write;
        }

        ifRead = false;
        ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite2RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Write;
        }

        return rwSameAccessSite;
    }

    public static Map<Stmt, RWSameAccessSite.RWType> getReadWriteAccessClient(AccessSite accessSite) {
        SootField accessClsVar = accessSite.accessVariable;
        SootMethod accessMethod = accessSite.sootMethod;
        Analyzer.CallSite callSite = (new ArrayList<Analyzer.CallSite>(accessSite.callerSites)).get(0);
        Stmt callRPCStmt = callSite.getCallStmt();

        Map<Stmt, RWSameAccessSite.RWType>  rwTypeMap = new HashMap<Stmt, RWSameAccessSite.RWType>();

        Set<Value> field2Locals = new HashSet<>();
        if (!accessMethod.hasActiveBody() || accessMethod.isStatic()) return rwTypeMap;
        for (Unit unit : accessMethod.retrieveActiveBody().getUnits()) {
            if (unit instanceof AssignStmt) {
                Value rightOp = ((AssignStmt) unit).getRightOp();
                Value leftOp = ((AssignStmt) unit).getLeftOp();
                if (rightOp instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) rightOp;
                    SootField sootField = fieldRef.getField();
                    if (sootField.getSignature().equals(accessClsVar.getSignature())) {
                        field2Locals.add(leftOp);
                    }
                }
            }
        }
        if (callRPCStmt instanceof AssignStmt) {
            if (field2Locals.contains(((AssignStmt) callRPCStmt).getLeftOp())) {
                rwTypeMap.put(callRPCStmt, RWSameAccessSite.RWType.Write);
            }
        }

        SootClass declaringClass = accessMethod.getDeclaringClass();
        Body jimpleBody = accessMethod.retrieveActiveBody();
        Local thisClsRef = (Local) jimpleBody.getThisLocal();
        boolean isNonPrimitive = !(accessClsVar.getType() instanceof PrimType);
        Set<MethodParamPair> analyzedPairs = new HashSet<>();
        boolean read = false;
        boolean write = false;

        InvokeExpr invokeExpr = callRPCStmt.getInvokeExpr();
        List<Value> args = invokeExpr.getArgs();

        for (int argIndex = 0; argIndex < args.size(); argIndex++) {
            Value arg = args.get(argIndex);
            // 判断参数是否与目标字段相关
            boolean isRelated = (field2Locals.contains(arg)) || ((arg instanceof InstanceFieldRef && isValidAccess((InstanceFieldRef) arg, thisClsRef, accessClsVar, declaringClass)) || arg instanceof StaticFieldRef && isValidAccessStatic((StaticFieldRef) arg, accessClsVar, declaringClass));
            if (isRelated) read = true;

            if (isRelated && isNonPrimitive) {
                // 递归检查调用链
                if (isRPCProtoMethod(invokeExpr.getMethod())) {
                    Set<SootMethod> rpcMethods = findRPCImplMethods(callRPCStmt);
                    if (rpcMethods != null && !rpcMethods.isEmpty()) {
                        for (SootMethod rpcMethod : rpcMethods) {
                            if (checkParamWriteInCallee(rpcMethod, adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                write = true;
                            }
                        }
                    }
                }
            }
        }

        if (read && write) {
            rwTypeMap.put(callRPCStmt, RWSameAccessSite.RWType.ReadWrite);
        } else if (read) {
            rwTypeMap.put(callRPCStmt, RWSameAccessSite.RWType.Read);
        } else if (write) {
            rwTypeMap.put(callRPCStmt, RWSameAccessSite.RWType.Write);
        }

        return rwTypeMap;
    }

    public static Set<RWSameAccessSite> getReadWriteWholeStatic(Set<SameAccessSite> sameClsVarAccessSites) {
        Set<RWSameAccessSite> rwSameAccessSites = new HashSet<>();
        for (SameAccessSite sameAccessSite : sameClsVarAccessSites) {
            RWSameAccessSite rwSameAccessSite = getReadWriteSameAccessStatic(sameAccessSite);
            if (rwSameAccessSite != null) {
                rwSameAccessSites.add(rwSameAccessSite);
            }
        }
        return rwSameAccessSites;
    }

    public static RWSameAccessSite getReadWriteSameAccessStatic(SameAccessSite sameAccessSite) {
        RWSameAccessSite rwSameAccessSite = new RWSameAccessSite(sameAccessSite.accessSite1, sameAccessSite.accessSite2);
        rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccessStatic(sameAccessSite.accessSite1);
        rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccessStatic(sameAccessSite.accessSite2);
        if (rwSameAccessSite.accessSite1RWTypeMap.isEmpty() || rwSameAccessSite.accessSite2RWTypeMap.isEmpty()) {
            return null;
        }

        boolean ifRead = false;
        boolean ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite1RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType1 = RWSameAccessSite.RWType.Write;
        }

        ifRead = false;
        ifWrite = false;
        for (RWSameAccessSite.RWType rwType : rwSameAccessSite.accessSite2RWTypeMap.values()) {
            if (rwType.equals(RWSameAccessSite.RWType.Read)) {
                ifRead = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.Write)) {
                ifWrite = true;
            } else if (rwType.equals(RWSameAccessSite.RWType.ReadWrite)) {
                ifRead = true;
                ifWrite = true;
            }
        }
        if (ifRead && ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.ReadWrite;
        } else if (ifRead) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Read;
        } else if (ifWrite) {
            rwSameAccessSite.rwType2 = RWSameAccessSite.RWType.Write;
        }

        return rwSameAccessSite;
    }

    public static Map<Stmt, RWSameAccessSite.RWType> getReadWriteAccessStatic(AccessSite accessSite) {
        Map<Stmt, RWSameAccessSite.RWType>  rwTypeMap = new HashMap<Stmt, RWSameAccessSite.RWType>();

        SootField accessClsVar = accessSite.accessVariable;
        List<List<SootMethod>> methodCallChain = accessSite.chain2StaticAccess;
        for (List<SootMethod> chain : methodCallChain) {
            SootMethod accessMethod = chain.get(chain.size() - 1);
            SootClass declaringClass = accessMethod.getDeclaringClass();
            try {
                if (!accessMethod.hasActiveBody() || accessMethod.isStatic()) continue;
                Body jimpleBody = accessMethod.retrieveActiveBody();
                boolean isNonPrimitive = !(accessClsVar.getType() instanceof PrimType);
                Set<Local> fieldLocals = new HashSet<>();
                // 新增：递归检查方法调用链的辅助集合
                Set<MethodParamPair> analyzedPairs = new HashSet<>();

                for (Unit unit : jimpleBody.getUnits()) {
                    Stmt stmt = (Stmt) unit;
                    boolean read = false;
                    boolean write = false;

                    if (unit instanceof JAssignStmt) {
                        JAssignStmt assign = (JAssignStmt) unit;

                        // 检查右值是否为目标字段的读操作（如 r1 = r0.<MyClass: int field>）
                        if (assign.getRightOp() instanceof StaticFieldRef) {
                            StaticFieldRef fieldRef = (StaticFieldRef) assign.getRightOp();
                            if (isValidAccessStatic(fieldRef, accessClsVar, declaringClass)) {
                                read = true;
                                fieldLocals.add((Local) assign.getLeftOp());
                            }
                        }

                        // 检查左值是否为目标字段的写操作（如 r0.<MyClass: int field> = r1）
                        if (assign.getLeftOp() instanceof StaticFieldRef) {
                            StaticFieldRef fieldRef = (StaticFieldRef) assign.getLeftOp();
                            if (isValidAccessStatic(fieldRef, accessClsVar, declaringClass)) {
                                write = true;
                            }
                        }

                        // 检查是否被传入函数调用
                        if (assign.containsInvokeExpr()) {
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            List<Value> args = invokeExpr.getArgs();

                            for (int argIndex = 0; argIndex < args.size(); argIndex++) {
                                Value arg = args.get(argIndex);
                                // 判断参数是否与目标字段相关
                                boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg)) || (arg instanceof StaticFieldRef && isValidAccessStatic((StaticFieldRef) arg, accessClsVar, declaringClass));

                                if (isRelated) read = true;

                                if (isRelated && isNonPrimitive) {
                                    // 递归检查调用链
                                    if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                        write = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        // 检查是否被传入函数调用
                        if (stmt.containsInvokeExpr()) {
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            List<Value> args = invokeExpr.getArgs();

                            for (int argIndex = 0; argIndex < args.size(); argIndex++) {
                                Value arg = args.get(argIndex);
                                // 判断参数是否与目标字段相关
                                boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg)) || (arg instanceof StaticFieldRef && isValidAccessStatic((StaticFieldRef) arg, accessClsVar, declaringClass));
                                if (isRelated) read = true;

                                if (isRelated && isNonPrimitive) {
                                    // 递归检查调用链
                                    if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                        write = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            for (ValueBox valueBox : unit.getUseBoxes()) {
                                Value value = valueBox.getValue();
                                read = (value instanceof Local && fieldLocals.contains(value)) || (value instanceof StaticFieldRef && isValidAccessStatic((StaticFieldRef) value, accessClsVar, declaringClass));
                            }
                        }
                    }

                    if (read && write) {
                        rwTypeMap.put(stmt, RWSameAccessSite.RWType.ReadWrite);
                    } else if (read) {
                        rwTypeMap.put(stmt, RWSameAccessSite.RWType.Read);
                    } else if (write) {
                        rwTypeMap.put(stmt, RWSameAccessSite.RWType.Write);
                    }
                }
            } catch (Exception e) {
                Log.i("[Error] ", e.getMessage().replace("\n", ""));
//            e.printStackTrace();
            }
        }


        return rwTypeMap;
    }

    public static boolean isValidAccessStatic(StaticFieldRef fieldRef, SootField targetField, SootClass declaringClass) {
        return fieldRef.getField().equals(targetField) && fieldRef.getField().getDeclaringClass().equals(declaringClass);
    }

    // READ WRITE analysis depth
    private static final int MAX_RECURSION_DEPTH = 7; // 可配置的递归深度

    // 原方法保持调用兼容性
    private static boolean checkParamWriteInCallee(SootMethod callee, int paramIndex, Set<MethodParamPair> analyzedPairs) {
        return checkParamWriteInCallee(callee, paramIndex, analyzedPairs, 0);
    }

    private static boolean checkParamWriteInCallee(SootMethod callee, int paramIndex, Set<MethodParamPair> analyzedPairs, int currentDepth) {
        // 深度超过限制时停止递归
        if (currentDepth > MAX_RECURSION_DEPTH || callee.isJavaLibraryMethod()) {
//            Log.i("[Warn] ", "Max recursion depth reached for: " + callee.getName());
            return false; // 保守假设未修改
        }

        MethodParamPair pair = new MethodParamPair(callee, paramIndex);
        if (analyzedPairs.contains(pair)) return false;
        analyzedPairs.add(pair);

        try {
            if (!callee.hasActiveBody()) return false;
            Body body = callee.getActiveBody();
            Local param = paramIndex >= 0 ? body.getParameterLocal(paramIndex) : body.getThisLocal();

            for (Unit unit : body.getUnits()) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;
                    if (assign.getLeftOp() instanceof Local && assign.getLeftOp().equals(param)) {
                        return true;
                    }
                }

                if (unit instanceof Stmt) {
                    Stmt stmt = (Stmt) unit;
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr innerInvoke = stmt.getInvokeExpr();
                        for (int i = 0; i < innerInvoke.getArgs().size(); i++) {
                            Value arg = innerInvoke.getArgs().get(i);
                            if (arg.equals(param)) {
                                // 递归调用时增加深度
                                if (checkParamWriteInCallee(innerInvoke.getMethod(), adjustParamIndex(innerInvoke, i), analyzedPairs, currentDepth + 1)) { // 深度+1
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i("[Error] ", e.getMessage().replace("\n", ""));
        }
        return false;
    }

    public static int adjustParamIndex(InvokeExpr invokeExpr, int originalIndex) {
        if (invokeExpr instanceof InstanceInvokeExpr) {
            return originalIndex == 0 ? -1 : originalIndex - 1;
        }
        return originalIndex;
    }

}
