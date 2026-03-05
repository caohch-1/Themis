package indi.dc.rw;

import cn.ac.ios.bridge.util.Log;
import indi.dc.access.AccessSite;
import indi.dc.access.SameAccessSite;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.*;

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
        if (sameAccessSite.sameAccessLocalVar != null) {
            rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccessLocal(sameAccessSite.accessSite1);
            rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccessLocal(sameAccessSite.accessSite2);
        } else if (sameAccessSite.sameAccessClsVar.isStatic()) {
            rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccessStatic(sameAccessSite.accessSite1);
            rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccessStatic(sameAccessSite.accessSite2);
        } else {
            rwSameAccessSite.accessSite1RWTypeMap = getReadWriteAccess(sameAccessSite.accessSite1);
            rwSameAccessSite.accessSite2RWTypeMap = getReadWriteAccess(sameAccessSite.accessSite2);
        }
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

    public static Map<Stmt, RWSameAccessSite.RWType> getReadWriteAccessLocal(AccessSite accessSite) {
        Local accessLocalVar = accessSite.accessVariableLocal;
        SootMethod accessMethod = accessSite.sootMethod;
        Map<Stmt, RWSameAccessSite.RWType>  rwTypeMap = new HashMap<Stmt, RWSameAccessSite.RWType>();
        try {
            if (!accessMethod.hasActiveBody() || accessMethod.isStatic()) return rwTypeMap;
            Body jimpleBody = accessMethod.retrieveActiveBody();
            boolean isNonPrimitive = !(accessLocalVar.getType() instanceof PrimType);
            Set<Local> relatedLocals = new HashSet<>();
            relatedLocals.add(accessLocalVar);

            // 新增：递归检查方法调用链的辅助集合
            Set<MethodParamPair> analyzedPairs = new HashSet<>();

            for (Unit unit : jimpleBody.getUnits()) {
                Stmt stmt = (Stmt) unit;
                boolean read = false;
                boolean write = false;

                // 检查是否被传入函数调用
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    List<Value> args = invokeExpr.getArgs();
                    for (int argIndex = 0; argIndex < args.size(); argIndex++) {
                        Value arg = args.get(argIndex);
                        // 判断参数是否与目标字段相关
                        boolean isRelated = arg instanceof Local && relatedLocals.contains(arg);
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
                        read = value instanceof Local && relatedLocals.contains(value);
                    }

                    for (ValueBox valueBox : unit.getDefBoxes()) {
                        Value value = valueBox.getValue();
                        write = value instanceof Local && relatedLocals.contains(value);
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

    public static Map<Stmt, RWSameAccessSite.RWType> getReadWriteAccessStatic(AccessSite accessSite) {
        SootField accessClsVar = accessSite.accessVariable;
        SootMethod accessMethod = accessSite.sootMethod;
        SootClass declaringClass = accessMethod.getDeclaringClass();
        Map<Stmt, RWSameAccessSite.RWType> rwTypeMap = new HashMap<>();

        try {
            if (!accessMethod.hasActiveBody()) return rwTypeMap;
            Body jimpleBody = accessMethod.retrieveActiveBody();

            boolean isNonPrimitive = !(accessClsVar.getType() instanceof PrimType);
            Set<Local> fieldLocals = new HashSet<>();
            Set<MethodParamPair> analyzedPairs = new HashSet<>();

            for (Unit unit : jimpleBody.getUnits()) {
                Stmt stmt = (Stmt) unit;
                boolean read = false;
                boolean write = false;

                if (unit instanceof JAssignStmt) {
                    JAssignStmt assign = (JAssignStmt) unit;

                    // 静态字段读检测
                    if (assign.getRightOp() instanceof StaticFieldRef) {
                        StaticFieldRef fieldRef = (StaticFieldRef) assign.getRightOp();
                        if (isValidStaticAccess(fieldRef, accessClsVar, declaringClass)) {
                            read = true;
                            // 静态字段可能被存入局部变量
                            if (assign.getLeftOp() instanceof Local) {
                                fieldLocals.add((Local) assign.getLeftOp());
                            }
                        }
                    }

                    // 静态字段写检测
                    if (assign.getLeftOp() instanceof StaticFieldRef) {
                        StaticFieldRef fieldRef = (StaticFieldRef) assign.getLeftOp();
                        if (isValidStaticAccess(fieldRef, accessClsVar, declaringClass)) {
                            write = true;
                        }
                    }

                    // 参数传递检测（与实例字段逻辑相同）
                    if (assign.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        for (int argIndex = 0; argIndex < invokeExpr.getArgs().size(); argIndex++) {
                            Value arg = invokeExpr.getArgs().get(argIndex);
                            boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg))
                                    || (arg instanceof StaticFieldRef && isValidStaticAccess((StaticFieldRef) arg, accessClsVar, declaringClass));

                            if (isRelated && isNonPrimitive) {
                                if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                    write = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // 其他语句类型检测（如直接调用中的参数）
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        for (int argIndex = 0; argIndex < invokeExpr.getArgs().size(); argIndex++) {
                            Value arg = invokeExpr.getArgs().get(argIndex);
                            boolean isRelated = (arg instanceof Local && fieldLocals.contains(arg))
                                    || (arg instanceof StaticFieldRef && isValidStaticAccess((StaticFieldRef) arg, accessClsVar, declaringClass));

                            if (isRelated && isNonPrimitive) {
                                if (checkParamWriteInCallee(invokeExpr.getMethod(), adjustParamIndex(invokeExpr, argIndex), analyzedPairs)) {
                                    write = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // 其他读操作（如条件判断中的使用）
                        for (ValueBox valueBox : unit.getUseBoxes()) {
                            Value value = valueBox.getValue();
                            if (value instanceof StaticFieldRef && isValidStaticAccess((StaticFieldRef) value, accessClsVar, declaringClass)) {
                                read = true;
                            }
                        }
                    }
                }

                // 合并读写状态
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
        }
        return rwTypeMap;
    }

    // 在类定义顶部添加最大深度常量
    private static final int MAX_RECURSION_DEPTH = 50; // 可配置的递归深度

    // 原方法保持调用兼容性
    private static boolean checkParamWriteInCallee(SootMethod callee, int paramIndex, Set<MethodParamPair> analyzedPairs) {
        return checkParamWriteInCallee(callee, paramIndex, analyzedPairs, 0);
    }

    private static boolean checkParamWriteInCallee(SootMethod callee, int paramIndex, Set<MethodParamPair> analyzedPairs, int currentDepth) {
        // 深度超过限制时停止递归
        if (currentDepth > MAX_RECURSION_DEPTH) {
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

    public static boolean isValidAccess(JInstanceFieldRef fieldRef, Local targetLocal, SootField targetField, SootClass declaringClass) {
        return fieldRef.getBase().equals(targetLocal)
                && fieldRef.getField().equals(targetField)
                && fieldRef.getField().getDeclaringClass().equals(declaringClass);
    }

    public static boolean isValidStaticAccess(StaticFieldRef fieldRef, SootField targetField, SootClass declaringClass) {
        return fieldRef.getField().equals(targetField) && fieldRef.getField().getDeclaringClass().equals(declaringClass);
    }

}
