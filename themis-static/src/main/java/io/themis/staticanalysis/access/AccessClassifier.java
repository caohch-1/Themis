package io.themis.staticanalysis.access;

import io.themis.core.model.AccessOperation;
import io.themis.core.model.AccessScope;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.SwitchStmt;

import java.util.Locale;

public class AccessClassifier {
    private final CollectionWriteCatalog collectionWriteCatalog;
    private final ParameterMutationAnalyzer parameterMutationAnalyzer;

    public AccessClassifier() {
        this(new CollectionWriteCatalog(), new ParameterMutationAnalyzer());
    }

    public AccessClassifier(CollectionWriteCatalog collectionWriteCatalog, ParameterMutationAnalyzer parameterMutationAnalyzer) {
        this.collectionWriteCatalog = collectionWriteCatalog;
        this.parameterMutationAnalyzer = parameterMutationAnalyzer;
    }

    public AccessOperation classifyOperation(Unit unit) {
        return classifyOperation(unit, null);
    }

    public AccessOperation classifyOperation(Unit unit, Value focus) {
        if (unit instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) unit;
            if (assignStmt.containsInvokeExpr()) {
                boolean focusOnLeft = focus == null ? isWriteTarget(assignStmt.getLeftOp()) : valueEquivalent(assignStmt.getLeftOp(), focus);
                return classifyInvoke(assignStmt.getInvokeExpr(), focus, focusOnLeft);
            }
            Value left = assignStmt.getLeftOp();
            Value right = assignStmt.getRightOp();
            boolean write = focus == null ? isWriteTarget(left) : valueEquivalent(left, focus);
            boolean read = focus == null ? isReadSource(right) : valueEquivalent(right, focus) || appearsInUseBoxes(right, focus);
            if (write && read) {
                return AccessOperation.READ_WRITE;
            }
            if (write) {
                return AccessOperation.WRITE;
            }
            if (read) {
                return AccessOperation.READ;
            }
            return AccessOperation.READ;
        }
        if (unit instanceof InvokeStmt) {
            return classifyInvoke(((InvokeStmt) unit).getInvokeExpr(), focus, false);
        }
        if (unit instanceof ReturnStmt || unit instanceof SwitchStmt) {
            return AccessOperation.READ;
        }
        if (focus != null) {
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                if (valueEquivalent(box.getValue(), focus)) {
                    return AccessOperation.READ;
                }
            }
        }
        return AccessOperation.READ;
    }

    public AccessScope classifyScope(Value value) {
        if (value instanceof ParameterRef) {
            return AccessScope.PARAMETER;
        }
        if (value instanceof ArrayRef) {
            return AccessScope.ARRAY;
        }
        if (value instanceof StaticFieldRef) {
            return AccessScope.STATIC;
        }
        if (value instanceof FieldRef) {
            SootField field = ((FieldRef) value).getField();
            String typeName = field.getType().toString().toLowerCase(Locale.ROOT);
            if (typeName.contains("java.io") || typeName.contains("java.nio")) {
                return AccessScope.IO_OBJECT;
            }
            if (collectionWriteCatalog.isCollectionType(typeName)) {
                return AccessScope.COLLECTION;
            }
            return AccessScope.INSTANCE;
        }
        String valueType = value.getType().toString().toLowerCase(Locale.ROOT);
        if (valueType.contains("java.io") || valueType.contains("java.nio")) {
            return AccessScope.IO_OBJECT;
        }
        if (collectionWriteCatalog.isCollectionType(valueType)) {
            return AccessScope.COLLECTION;
        }
        return AccessScope.LOCAL;
    }

    private AccessOperation classifyInvoke(InvokeExpr invokeExpr, Value focus, boolean focusOnLeftAssign) {
        if (focus == null) {
            if (collectionWriteCatalog.isWriteLikeInvocation(invokeExpr.getMethod()) || isMutator(invokeExpr.getMethod().getName())) {
                return AccessOperation.WRITE;
            }
            if (isLibraryOrThirdParty(invokeExpr.getMethod())) {
                return AccessOperation.READ;
            }
            return AccessOperation.READ;
        }

        boolean focusOnReceiver = false;
        boolean focusOnArgument = false;
        boolean read = false;
        boolean write = false;

        if (invokeExpr instanceof InstanceInvokeExpr) {
            Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
            focusOnReceiver = valueEquivalent(base, focus);
            if (focusOnReceiver) {
                read = true;
                if (collectionWriteCatalog.isWriteLikeInvocation(invokeExpr.getMethod()) || parameterMutationAnalyzer.invocationMutatesReceiver(invokeExpr)) {
                    write = true;
                }
            }
        }

        for (int i = 0; i < invokeExpr.getArgs().size(); i++) {
            Value arg = invokeExpr.getArgs().get(i);
            if (!valueEquivalent(arg, focus)) {
                continue;
            }
            focusOnArgument = true;
            read = true;
            if (parameterMutationAnalyzer.invocationMutatesArgument(invokeExpr, i)) {
                write = true;
            }
        }

        if (!focusOnReceiver && !focusOnArgument) {
            if (focusOnLeftAssign) {
                return AccessOperation.WRITE;
            }
            return AccessOperation.READ;
        }

        if (write && read) {
            return AccessOperation.READ_WRITE;
        }
        if (write) {
            return AccessOperation.WRITE;
        }
        if (read) {
            return AccessOperation.READ;
        }
        return AccessOperation.READ;
    }

    private boolean isWriteTarget(Value value) {
        return value instanceof FieldRef || value instanceof ArrayRef || value.toString().contains("=");
    }

    private boolean isReadSource(Value value) {
        return !(value instanceof ParameterRef && value.toString().isEmpty());
    }

    private boolean isMutator(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("put") || lower.contains("remove") || lower.contains("clear") || lower.contains("set") || lower.contains("write") || lower.contains("add") || lower.contains("update") || lower.contains("append");
    }

    private boolean isLibraryOrThirdParty(SootMethod method) {
        String pkg = method.getDeclaringClass().getPackageName();
        return pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("org.apache.commons") || pkg.startsWith("com.google");
    }

    private boolean appearsInUseBoxes(Value root, Value focus) {
        if (root == null || focus == null) {
            return false;
        }
        for (ValueBox box : root.getUseBoxes()) {
            if (valueEquivalent(box.getValue(), focus)) {
                return true;
            }
        }
        return false;
    }

    private boolean valueEquivalent(Value left, Value right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.equivTo(right)) {
            return true;
        }
        return left.toString().equals(right.toString());
    }

    public boolean isReceiverOrParameterFlow(InvokeExpr invokeExpr) {
        if (invokeExpr instanceof InstanceInvokeExpr) {
            return true;
        }
        for (Value arg : invokeExpr.getArgs()) {
            if (arg instanceof ParameterRef || arg.toString().startsWith("$r")) {
                return true;
            }
        }
        return false;
    }
}
