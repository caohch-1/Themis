package indi.dc.extraction.utils;

import cn.ac.ios.bridge.util.Log;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.util.*;
import java.util.*;

public class AsyncChecker {

    public static boolean areAsyncExecuted(SootMethod method, Stmt stmt1, Stmt stmt2) {
        return isAsyncExecuted(method, stmt1) || isAsyncExecuted(method, stmt2);
    }


    private static boolean isAsyncExecuted(SootMethod containingMethod, Stmt stmt) {
        // 获取stmt所在的method（可能是内部类的方法）
        SootMethod stmtMethod = stmt.getInvokeExpr().getMethod();
        if (!stmtMethod.hasActiveBody() || !containingMethod.hasActiveBody()) return false;

        // 在containingMethod中查找所有可能被异步执行的实例
        Body containerBody = containingMethod.retrieveActiveBody();
        Set<Local> asyncInstances = findAsyncInstances(containerBody, stmtMethod);

        if (asyncInstances.isEmpty()) return false;

        // Todo: 注释掉的话只是看containingMethod中有没有Async相关操作
        // 判断该method是否是异步执行的方法（Runnable.run, Callable.call, Thread.run）
        if (!isAsyncMethod(stmtMethod)) return false;

        // 检查这些实例是否被传递给异步执行器
        for (Local instance : asyncInstances) {
            if (isInstanceAsyncExecuted(containerBody, instance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsyncMethod(SootMethod method) {
        SootClass clazz = method.getDeclaringClass();
        String methodName = method.getName();
        int paramCount = method.getParameterCount();

        if (methodName.equals("run") && paramCount == 0) {
            return clazz.implementsInterface("java.lang.Runnable") || (clazz.hasSuperclass() && clazz.getSuperclass().getName().equals("java.lang.Thread"));
        } else if (methodName.equals("call") && paramCount == 0) {
            return clazz.implementsInterface("java.util.concurrent.Callable");
        }
        return false;
    }

    private static Set<Local> findAsyncInstances(Body body, SootMethod targetMethod) {
        Set<Local> instances = new HashSet<>();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof JAssignStmt) {
                JAssignStmt assign = (JAssignStmt) unit;
                if (assign.getRightOp() instanceof JNewExpr) {
                    JNewExpr newExpr = (JNewExpr) assign.getRightOp();
                    SootClass newClass = newExpr.getBaseType().getSootClass();
                    if (isClassMatch(newClass, targetMethod)) {
                        instances.add((Local) assign.getLeftOp());
                    }
                }
            }
        }
        return instances;
    }

    private static boolean isClassMatch(SootClass clazz, SootMethod targetMethod) {
        // 检查类是否包含目标方法
        return clazz.declaresMethod(targetMethod.getSubSignature());
    }

    private static boolean isInstanceAsyncExecuted(Body body, Local instance) {
        for (Unit unit : body.getUnits()) {
            // 检查线程池提交或Thread启动
            if (unit instanceof JInvokeStmt) {
                JInvokeStmt invokeStmt = (JInvokeStmt) unit;
                if (checkAsyncInvocation(invokeStmt.getInvokeExpr(), instance)) {
                    return true;
                }
            } else if (unit instanceof JAssignStmt) {
                JAssignStmt assign = (JAssignStmt) unit;
                if (assign.getRightOp() instanceof InvokeExpr) {
                    InvokeExpr invoke = (InvokeExpr) assign.getRightOp();
                    if (checkAsyncInvocation(invoke, instance)) {
                        // 处理类似thread.start()直接调用的情况
                        if (isThreadStartChain(assign.getLeftOp(), body)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean checkAsyncInvocation(InvokeExpr invoke, Local instance) {
        SootMethod method = invoke.getMethod();
        // 检查参数是否包含目标实例
        for (Value arg : invoke.getArgs()) {
            if (arg.equivTo(instance)) {
                // 判断是否是异步执行方法
                String className = method.getDeclaringClass().getName();
                String methodName = method.getName();
                if (className.equals("java.lang.Thread") && method.isConstructor()) {
                    return checkThreadStart(invoke, instance, method);
                } else if (className.equals("java.util.concurrent.ExecutorService")) {
                    return methodName.equals("execute") || methodName.equals("submit");
                } else if (className.equals("java.util.concurrent.CompletableFuture")) {
                    return methodName.equals("runAsync") || methodName.equals("supplyAsync");
                }
            }
        }
        return false;
    }

    private static boolean checkThreadStart(InvokeExpr invoke, Local instance, SootMethod method) {
        // 检查Thread实例是否启动
        if (invoke instanceof SpecialInvokeExpr) {
            SpecialInvokeExpr sie = (SpecialInvokeExpr) invoke;
            for (Unit u : method.getActiveBody().getUnits()) {
                if (u instanceof JInvokeStmt) {
                    JInvokeStmt is = (JInvokeStmt) u;
                    // 添加类型检查及转换
                    if (is.getInvokeExpr() instanceof InstanceInvokeExpr) {
                        InstanceInvokeExpr iie = (InstanceInvokeExpr) is.getInvokeExpr();
                        if (iie.getMethod().getName().equals("start") && iie.getBase().equivTo(sie.getBase())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isThreadStartChain(Value threadInstance, Body body) {
        // 检查Thread实例是否调用了start()
        for (Unit unit : body.getUnits()) {
            if (unit instanceof JInvokeStmt) {
                JInvokeStmt is = (JInvokeStmt) unit;
                // 添加类型检查及转换
                if (is.getInvokeExpr() instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr iie = (InstanceInvokeExpr) is.getInvokeExpr();
                    if (iie.getMethod().getName().equals("start") && iie.getBase().equivTo(threadInstance)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
