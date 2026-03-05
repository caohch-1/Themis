package io.themis.staticanalysis.access;

import soot.Body;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AsyncExecutionDetector {
    private final Map<String, Boolean> methodAsyncCache = new HashMap<String, Boolean>();

    public boolean isAsyncSite(SootMethod containingMethod, Unit unit) {
        if (containingMethod == null || unit == null) {
            return false;
        }
        if (isAsyncMethod(containingMethod)) {
            return true;
        }
        if (!(unit instanceof Stmt)) {
            return methodHasAsyncExecution(containingMethod);
        }
        Stmt stmt = (Stmt) unit;
        if (!stmt.containsInvokeExpr()) {
            return methodHasAsyncExecution(containingMethod);
        }
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if (isAsyncApi(invokeExpr.getMethod())) {
            return true;
        }
        if (isThreadCtor(invokeExpr) && threadStartExists(containingMethod, invokeExpr)) {
            return true;
        }
        return methodHasAsyncExecution(containingMethod);
    }

    public boolean methodHasAsyncExecution(SootMethod method) {
        if (method == null) {
            return false;
        }
        String signature = method.getSignature();
        if (methodAsyncCache.containsKey(signature)) {
            return methodAsyncCache.get(signature);
        }
        boolean result = false;
        if (isAsyncMethod(method)) {
            result = true;
        } else if (method.hasActiveBody()) {
            Body body = method.getActiveBody();
            for (Unit unit : body.getUnits()) {
                if (!(unit instanceof Stmt)) {
                    continue;
                }
                Stmt stmt = (Stmt) unit;
                if (!stmt.containsInvokeExpr()) {
                    continue;
                }
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                if (isAsyncApi(invokeExpr.getMethod()) || (isThreadCtor(invokeExpr) && threadStartExists(method, invokeExpr))) {
                    result = true;
                    break;
                }
            }
        }
        methodAsyncCache.put(signature, result);
        return result;
    }

    public boolean isAsyncMethod(SootMethod method) {
        if (method == null) {
            return false;
        }
        String methodName = method.getName();
        if ("run".equals(methodName) && method.getParameterCount() == 0) {
            SootClass cls = method.getDeclaringClass();
            if (cls.implementsInterface("java.lang.Runnable")) {
                return true;
            }
            if (inheritsFrom(cls, "java.lang.Thread")) {
                return true;
            }
        }
        if ("call".equals(methodName) && method.getParameterCount() == 0) {
            SootClass cls = method.getDeclaringClass();
            return cls.implementsInterface("java.util.concurrent.Callable");
        }
        return false;
    }

    private boolean isAsyncApi(SootMethod method) {
        if (method == null) {
            return false;
        }
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String lowerClass = className.toLowerCase(Locale.ROOT);
        String lowerMethod = methodName.toLowerCase(Locale.ROOT);

        if ("java.lang.Thread".equals(className) && "start".equals(methodName)) {
            return true;
        }
        if (lowerClass.startsWith("java.util.concurrent.executor") && ("execute".equals(methodName) || "submit".equals(methodName) || "invokeall".equals(lowerMethod) || "invokeany".equals(lowerMethod))) {
            return true;
        }
        if ("java.util.concurrent.ExecutorService".equals(className) && ("execute".equals(methodName) || "submit".equals(methodName))) {
            return true;
        }
        if ("java.util.concurrent.CompletableFuture".equals(className) && ("runAsync".equals(methodName) || "supplyAsync".equals(methodName))) {
            return true;
        }
        if (lowerClass.contains("forkjoin") && lowerMethod.contains("fork")) {
            return true;
        }
        return false;
    }

    private boolean isThreadCtor(InvokeExpr invokeExpr) {
        return invokeExpr instanceof InstanceInvokeExpr && invokeExpr.getMethod().isConstructor() && "java.lang.Thread".equals(invokeExpr.getMethod().getDeclaringClass().getName());
    }

    private boolean threadStartExists(SootMethod containingMethod, InvokeExpr ctorInvoke) {
        if (!containingMethod.hasActiveBody()) {
            return false;
        }
        if (!(ctorInvoke instanceof InstanceInvokeExpr)) {
            return false;
        }
        Value base = ((InstanceInvokeExpr) ctorInvoke).getBase();
        if (!(base instanceof Local)) {
            return false;
        }
        Body body = containingMethod.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (!(unit instanceof Stmt)) {
                continue;
            }
            Stmt stmt = (Stmt) unit;
            if (!stmt.containsInvokeExpr()) {
                continue;
            }
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            if (!(invokeExpr instanceof InstanceInvokeExpr)) {
                continue;
            }
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            if (!"start".equals(instanceInvokeExpr.getMethod().getName())) {
                continue;
            }
            if (!base.equivTo(instanceInvokeExpr.getBase())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean inheritsFrom(SootClass cls, String ancestorName) {
        SootClass current = cls;
        while (current != null) {
            if (ancestorName.equals(current.getName())) {
                return true;
            }
            if (!current.hasSuperclass()) {
                break;
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
