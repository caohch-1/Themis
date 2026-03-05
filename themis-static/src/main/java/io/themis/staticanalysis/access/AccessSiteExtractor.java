package io.themis.staticanalysis.access;

import io.themis.core.model.AccessOperation;
import io.themis.core.model.AccessScope;
import io.themis.core.model.AccessSite;
import io.themis.core.model.SystemSide;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SwitchStmt;
import soot.tagkit.LineNumberTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccessSiteExtractor {
    private final AccessClassifier classifier;
    private final PointsToResolver pointsToResolver;
    private final AsyncExecutionDetector asyncExecutionDetector;

    public AccessSiteExtractor(AccessClassifier classifier, PointsToResolver pointsToResolver) {
        this(classifier, pointsToResolver, new AsyncExecutionDetector());
    }

    public AccessSiteExtractor(AccessClassifier classifier,
                               PointsToResolver pointsToResolver,
                               AsyncExecutionDetector asyncExecutionDetector) {
        this.classifier = classifier;
        this.pointsToResolver = pointsToResolver;
        this.asyncExecutionDetector = asyncExecutionDetector;
    }

    public List<AccessSite> extract(SootMethod method, SystemSide side, boolean reachableFromRpc, int publicCallerDepth) {
        List<AccessSite> sites = new ArrayList<AccessSite>();
        if (!method.hasActiveBody()) {
            return sites;
        }
        int index = 0;
        boolean asyncMethod = asyncExecutionDetector.methodHasAsyncExecution(method);
        for (Unit unit : method.getActiveBody().getUnits()) {
            boolean asyncUnit = asyncExecutionDetector.isAsyncSite(method, unit);
            List<Value> values = enumerateValues(unit);
            for (Value value : values) {
                Set<String> variables = new LinkedHashSet<String>(pointsToResolver.resolveReferencedVariables(value));
                for (String variable : variables) {
                    AccessOperation operation = classifier.classifyOperation(unit, value);
                    AccessScope scope = classifier.classifyScope(value);
                    Map<String, String> attrs = new LinkedHashMap<String, String>();
                    attrs.put("declaringClass", method.getDeclaringClass().getName());
                    attrs.put("rawUnit", unit.toString());
                    attrs.put("scope", scope.name());
                    attrs.put("operation", operation.name());
                    attrs.put("valueType", value.getType().toString());
                    attrs.put("asyncMethod", Boolean.toString(asyncMethod));
                    attrs.put("asyncUnit", Boolean.toString(asyncUnit));
                    String id = method.getDeclaringClass().getName() + "::" + method.getSubSignature() + "::" + (index++);
                    sites.add(new AccessSite(
                        id,
                        method.getDeclaringClass().getName(),
                        method.getSignature(),
                        unit.toString(),
                        lineOf(unit),
                        variable,
                        scope,
                        operation,
                        side,
                        reachableFromRpc,
                        publicCallerDepth,
                        attrs));
                }
            }
        }
        return sites;
    }

    private List<Value> enumerateValues(Unit unit) {
        List<Value> values = new ArrayList<Value>();
        if (unit instanceof AssignStmt) {
            values.add(((AssignStmt) unit).getLeftOp());
            values.add(((AssignStmt) unit).getRightOp());
        } else if (unit instanceof InvokeStmt) {
            InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
            if (classifier.isReceiverOrParameterFlow(invokeExpr)) {
                if (invokeExpr instanceof soot.jimple.InstanceInvokeExpr) {
                    values.add(((soot.jimple.InstanceInvokeExpr) invokeExpr).getBase());
                }
                values.addAll(invokeExpr.getArgs());
            }
        } else if (unit instanceof IfStmt || unit instanceof SwitchStmt) {
            for (ValueBox box : unit.getUseBoxes()) {
                values.add(box.getValue());
            }
        } else {
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                values.add(box.getValue());
            }
        }
        return values;
    }

    private int lineOf(Unit unit) {
        LineNumberTag tag = (LineNumberTag) unit.getTag("LineNumberTag");
        if (tag == null) {
            return -1;
        }
        return tag.getLineNumber();
    }

    public List<AccessSite> extractFromClass(SootClass sootClass, SystemSide side, boolean reachableFromRpc) {
        List<AccessSite> sites = new ArrayList<AccessSite>();
        for (SootMethod method : sootClass.getMethods()) {
            sites.addAll(extract(method, side, reachableFromRpc, Integer.MAX_VALUE));
        }
        return sites;
    }
}
