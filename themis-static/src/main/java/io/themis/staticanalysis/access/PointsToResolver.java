package io.themis.staticanalysis.access;

import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.Scene;
import soot.SootField;
import soot.Value;
import soot.jimple.InstanceFieldRef;
import soot.jimple.ParameterRef;

import java.util.LinkedHashSet;
import java.util.Set;

public class PointsToResolver {
    public Set<String> resolveReferencedVariables(Value value) {
        Set<String> names = new LinkedHashSet<>();
        if (value instanceof InstanceFieldRef) {
            SootField field = ((InstanceFieldRef) value).getField();
            names.add(field.getDeclaringClass().getName() + "." + field.getName());
            Value base = ((InstanceFieldRef) value).getBase();
            names.addAll(resolvePointsTo(base));
            return names;
        }
        if (value instanceof ParameterRef) {
            ParameterRef parameterRef = (ParameterRef) value;
            names.add("param:" + parameterRef.getIndex());
            return names;
        }
        names.addAll(resolvePointsTo(value));
        if (names.isEmpty()) {
            names.add(value.toString());
        }
        return names;
    }

    private Set<String> resolvePointsTo(Value value) {
        Set<String> values = new LinkedHashSet<>();
        if (!(value instanceof Local)) {
            return values;
        }
        Local local = (Local) value;
        PointsToAnalysis analysis = Scene.v().getPointsToAnalysis();
        if (analysis == null) {
            values.add(local.getName());
            return values;
        }
        PointsToSet set = analysis.reachingObjects(local);
        values.add(local.getName());
        values.add(set.toString());
        return values;
    }
}
