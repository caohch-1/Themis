package io.themis.staticanalysis.access;

import soot.Body;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.HashSet;
import java.util.Set;

public class ParameterMutationAnalyzer {
    private final int maxRecursionDepth;

    public ParameterMutationAnalyzer() {
        this(7);
    }

    public ParameterMutationAnalyzer(int maxRecursionDepth) {
        this.maxRecursionDepth = maxRecursionDepth;
    }

    public boolean invocationMutatesReceiver(InvokeExpr invokeExpr) {
        if (!(invokeExpr instanceof InstanceInvokeExpr)) {
            return false;
        }
        return checkParamWriteInCallee(invokeExpr.getMethod(), -1, new HashSet<String>(), 0);
    }

    public boolean invocationMutatesArgument(InvokeExpr invokeExpr, int argumentIndex) {
        SootMethod callee = invokeExpr.getMethod();
        Set<String> visited = new HashSet<String>();
        if (checkParamWriteInCallee(callee, argumentIndex, visited, 0)) {
            return true;
        }
        int adjusted = adjustParamIndex(invokeExpr, argumentIndex);
        if (adjusted != argumentIndex) {
            return checkParamWriteInCallee(callee, adjusted, visited, 0);
        }
        return false;
    }

    private boolean checkParamWriteInCallee(SootMethod callee,
                                            int paramIndex,
                                            Set<String> visited,
                                            int depth) {
        if (callee == null) {
            return false;
        }
        if (depth > maxRecursionDepth || callee.isJavaLibraryMethod()) {
            return false;
        }
        String key = callee.getSignature() + "::" + paramIndex;
        if (visited.contains(key)) {
            return false;
        }
        visited.add(key);
        if (!callee.hasActiveBody()) {
            return false;
        }
        try {
            Body body = callee.getActiveBody();
            Local tracked = resolveTrackedLocal(body, paramIndex);
            if (tracked == null) {
                return false;
            }
            for (Unit unit : body.getUnits()) {
                if (unit instanceof AssignStmt) {
                    AssignStmt assign = (AssignStmt) unit;
                    if (writesTracked(assign.getLeftOp(), tracked)) {
                        return true;
                    }
                }
                if (unit instanceof Stmt) {
                    Stmt stmt = (Stmt) unit;
                    if (!stmt.containsInvokeExpr()) {
                        continue;
                    }
                    InvokeExpr innerInvoke = stmt.getInvokeExpr();
                    if (innerInvoke instanceof InstanceInvokeExpr) {
                        Value base = ((InstanceInvokeExpr) innerInvoke).getBase();
                        if (equivalent(base, tracked) && checkParamWriteInCallee(innerInvoke.getMethod(), -1, visited, depth + 1)) {
                            return true;
                        }
                    }
                    for (int i = 0; i < innerInvoke.getArgs().size(); i++) {
                        Value arg = innerInvoke.getArgs().get(i);
                        if (!equivalent(arg, tracked)) {
                            continue;
                        }
                        if (checkParamWriteInCallee(innerInvoke.getMethod(), i, visited, depth + 1)) {
                            return true;
                        }
                        int adjusted = adjustParamIndex(innerInvoke, i);
                        if (adjusted != i && checkParamWriteInCallee(innerInvoke.getMethod(), adjusted, visited, depth + 1)) {
                            return true;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public int adjustParamIndex(InvokeExpr invokeExpr, int originalIndex) {
        if (invokeExpr instanceof InstanceInvokeExpr) {
            return originalIndex == 0 ? -1 : originalIndex - 1;
        }
        return originalIndex;
    }

    private Local resolveTrackedLocal(Body body, int paramIndex) {
        if (paramIndex == -1) {
            if (body.getMethod().isStatic()) {
                return null;
            }
            return body.getThisLocal();
        }
        if (paramIndex < 0 || paramIndex >= body.getMethod().getParameterCount()) {
            return null;
        }
        return body.getParameterLocal(paramIndex);
    }

    private boolean writesTracked(Value left, Local tracked) {
        if (equivalent(left, tracked)) {
            return true;
        }
        if (left instanceof InstanceFieldRef) {
            return equivalent(((InstanceFieldRef) left).getBase(), tracked);
        }
        if (left instanceof ArrayRef) {
            return equivalent(((ArrayRef) left).getBase(), tracked);
        }
        return false;
    }

    private boolean equivalent(Value a, Value b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equivTo(b)) {
            return true;
        }
        return a.toString().equals(b.toString());
    }
}
