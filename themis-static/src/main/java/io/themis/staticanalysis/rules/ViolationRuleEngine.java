package io.themis.staticanalysis.rules;

import io.themis.core.model.AccessOperation;
import io.themis.core.model.AccessSite;
import io.themis.core.model.InterleavingShape;
import io.themis.core.model.PublicInterfacePair;
import io.themis.core.model.SharedVariable;
import io.themis.core.model.SystemSide;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ViolationType;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ViolationRuleEngine {
    public List<ViolationTuple> detect(SharedVariable variable, List<AccessSite> accessSites, String rpcPairId) {
        List<ViolationTuple> tuples = new ArrayList<>();
        List<AccessSite> rpcSites = new ArrayList<>();
        for (AccessSite site : accessSites) {
            if (site.isReachableFromRpc()) {
                rpcSites.add(site);
            }
        }
        if (rpcSites.isEmpty()) {
            return tuples;
        }
        tuples.addAll(applyRule1(variable, accessSites, rpcPairId));
        tuples.addAll(applyRule2(variable, accessSites, rpcPairId));
        tuples.addAll(applyRule3(variable, accessSites, rpcPairId));
        if (variable.getSide() == SystemSide.CLIENT) {
            tuples.addAll(applyRule4(variable, accessSites, rpcPairId));
            tuples.addAll(applyRule5(variable, accessSites, rpcPairId));
        }
        return deduplicate(tuples);
    }

    private List<ViolationTuple> applyRule1(SharedVariable variable, List<AccessSite> sites, String rpcPairId) {
        List<ViolationTuple> result = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                AccessSite s1 = sites.get(i);
                AccessSite s2 = sites.get(j);
                if (sameMethod(s1, s2)) {
                    continue;
                }
                if (callerCallee(s1.getMethodSignature(), s2.getMethodSignature())) {
                    continue;
                }
                if (!hasWrite(s1, s2)) {
                    continue;
                }
                List<AccessSite> set = list(s1, s2);
                result.add(build(variable, set, ViolationType.ORDER, "R1", rpcPairId));
            }
        }
        return result;
    }

    private List<ViolationTuple> applyRule2(SharedVariable variable, List<AccessSite> sites, String rpcPairId) {
        List<ViolationTuple> result = new ArrayList<>();
        Map<String, List<AccessSite>> byMethod = byMethod(sites);
        for (Map.Entry<String, List<AccessSite>> entry : byMethod.entrySet()) {
            List<AccessSite> local = sortByLine(entry.getValue());
            if (local.size() < 2) {
                continue;
            }
            for (int i = 0; i < local.size() - 1; i++) {
                AccessSite s1 = local.get(i);
                AccessSite s2 = local.get(i + 1);
                for (AccessSite s3 : sites) {
                    if (s3.getMethodSignature().equals(s1.getMethodSignature())) {
                        continue;
                    }
                    if (callerCallee(s1.getMethodSignature(), s3.getMethodSignature())) {
                        continue;
                    }
                    List<InterleavingShape> shapes = atomicityShapes(s1, s2, s3);
                    if (shapes.isEmpty()) {
                        continue;
                    }
                    List<AccessSite> set = list(s1, s2, s3);
                    result.add(build(variable, set, ViolationType.ATOMICITY, "R2", rpcPairId, shapes));
                }
            }
        }
        return result;
    }

    private List<ViolationTuple> applyRule3(SharedVariable variable, List<AccessSite> sites, String rpcPairId) {
        List<ViolationTuple> result = new ArrayList<>();
        Map<String, List<AccessSite>> byMethod = byMethod(sites);
        for (Map.Entry<String, List<AccessSite>> entry : byMethod.entrySet()) {
            List<AccessSite> local = sortByLine(entry.getValue());
            if (local.size() < 2) {
                continue;
            }
            for (int i = 0; i < local.size() - 1; i++) {
                AccessSite s1 = local.get(i);
                AccessSite s2 = local.get(i + 1);
                AccessSite s3a = cloneForConcurrentInvocation(s1);
                AccessSite s3b = cloneForConcurrentInvocation(s2);
                List<InterleavingShape> shapesA = atomicityShapes(s1, s2, s3a);
                if (!shapesA.isEmpty()) {
                    result.add(build(variable, list(s1, s2, s3a), ViolationType.ATOMICITY, "R3", rpcPairId, shapesA));
                }
                List<InterleavingShape> shapesB = atomicityShapes(s1, s2, s3b);
                if (!shapesB.isEmpty()) {
                    result.add(build(variable, list(s1, s2, s3b), ViolationType.ATOMICITY, "R3", rpcPairId, shapesB));
                }
            }
        }
        return result;
    }

    private List<ViolationTuple> applyRule4(SharedVariable variable, List<AccessSite> sites, String rpcPairId) {
        List<ViolationTuple> result = new ArrayList<>();
        Map<String, List<AccessSite>> byMethod = byMethod(sites);
        for (Map.Entry<String, List<AccessSite>> entry : byMethod.entrySet()) {
            List<AccessSite> local = sortByLine(entry.getValue());
            for (int i = 0; i < local.size() - 1; i++) {
                AccessSite s1 = local.get(i);
                AccessSite s2 = local.get(i + 1);
                if (!isAsyncContext(s1) && !isAsyncContext(s2)) {
                    continue;
                }
                if (!hasWrite(s1, s2)) {
                    continue;
                }
                result.add(build(variable, list(s1, s2), ViolationType.ORDER, "R4", rpcPairId));
            }
        }
        return result;
    }

    private List<ViolationTuple> applyRule5(SharedVariable variable, List<AccessSite> sites, String rpcPairId) {
        List<ViolationTuple> result = new ArrayList<>();
        Map<String, List<AccessSite>> byMethod = byMethod(sites);
        for (Map.Entry<String, List<AccessSite>> entry : byMethod.entrySet()) {
            List<AccessSite> local = sortByLine(entry.getValue());
            if (local.size() < 3) {
                continue;
            }
            for (int i = 0; i < local.size() - 2; i++) {
                AccessSite s1 = local.get(i);
                AccessSite s2 = local.get(i + 1);
                AccessSite s3 = local.get(i + 2);
                if (!isAsyncContext(s3)) {
                    continue;
                }
                List<InterleavingShape> shapes = atomicityShapes(s1, s2, s3);
                if (shapes.isEmpty()) {
                    continue;
                }
                result.add(build(variable, list(s1, s2, s3), ViolationType.ATOMICITY, "R5", rpcPairId, shapes));
            }
        }
        return result;
    }

    private ViolationTuple build(SharedVariable variable,
                                 List<AccessSite> sites,
                                 ViolationType type,
                                 String rule,
                                 String rpcPairId) {
        return build(variable, sites, type, rule, rpcPairId, defaultInterleavings(type));
    }

    private ViolationTuple build(SharedVariable variable,
                                 List<AccessSite> sites,
                                 ViolationType type,
                                 String rule,
                                 String rpcPairId,
                                 List<InterleavingShape> shapes) {
        List<String> functions = new ArrayList<>();
        for (AccessSite site : sites) {
            if (!functions.contains(site.getMethodSignature())) {
                functions.add(site.getMethodSignature());
            }
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("variable", variable.getName());
        String id = variable.getId() + "-" + rule + "-" + Math.abs(sites.hashCode());
        return new ViolationTuple(
            id,
            variable.getId(),
            functions,
            sites,
            type,
            rule,
            rpcPairId,
            variable.getSide(),
            new PublicInterfacePair("", "", new ArrayList<String>(), new ArrayList<String>()),
            shapes,
            new ArrayList<>(),
            false,
            metadata);
    }

    private List<InterleavingShape> atomicityShapes(AccessSite a, AccessSite b, AccessSite c) {
        List<InterleavingShape> shapes = new ArrayList<>();
        String p = encode(a.getOperation()) + encode(c.getOperation()) + encode(b.getOperation());
        if ("RWR".equals(p)) {
            shapes.add(InterleavingShape.RWR);
        }
        if ("WWR".equals(p)) {
            shapes.add(InterleavingShape.WWR);
        }
        if ("WRW".equals(p)) {
            shapes.add(InterleavingShape.WRW);
        }
        if ("RWW".equals(p)) {
            shapes.add(InterleavingShape.RWW);
        }
        return shapes;
    }

    private String encode(AccessOperation operation) {
        return operation == AccessOperation.WRITE || operation == AccessOperation.READ_WRITE ? "W" : "R";
    }

    private List<InterleavingShape> defaultInterleavings(ViolationType type) {
        if (type == ViolationType.ORDER) {
            return list(InterleavingShape.ORDER_FORWARD, InterleavingShape.ORDER_REVERSE);
        }
        return list(InterleavingShape.ATOMICITY_BEFORE, InterleavingShape.ATOMICITY_MIDDLE, InterleavingShape.ATOMICITY_AFTER);
    }

    private boolean callerCallee(String left, String right) {
        if (left.equals(right)) {
            return false;
        }
        if (!Scene.v().containsMethod(left) || !Scene.v().containsMethod(right) || !Scene.v().hasCallGraph()) {
            return false;
        }
        SootMethod l = Scene.v().getMethod(left);
        SootMethod r = Scene.v().getMethod(right);
        CallGraph graph = Scene.v().getCallGraph();
        Iterator<Edge> leftOut = graph.edgesOutOf(l);
        while (leftOut.hasNext()) {
            if (leftOut.next().tgt().equals(r)) {
                return true;
            }
        }
        Iterator<Edge> rightOut = graph.edgesOutOf(r);
        while (rightOut.hasNext()) {
            if (rightOut.next().tgt().equals(l)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWrite(AccessSite a, AccessSite b) {
        return a.isWriteLike() || b.isWriteLike();
    }

    private boolean sameMethod(AccessSite a, AccessSite b) {
        return a.getMethodSignature().equals(b.getMethodSignature());
    }

    private AccessSite cloneForConcurrentInvocation(AccessSite site) {
        Map<String, String> attributes = site.getAttributes();
        attributes.put("invocation", "concurrent");
        return new AccessSite(
            site.getId() + "-concurrent",
            site.getClassName(),
            site.getMethodSignature(),
            site.getStatement(),
            site.getLineNumber(),
            site.getVariable(),
            site.getScope(),
            site.getOperation(),
            site.getSide(),
            site.isReachableFromRpc(),
            site.getPublicCallerDepth(),
            attributes);
    }

    private boolean isAsyncContext(AccessSite site) {
        Map<String, String> attrs = site.getAttributes();
        String asyncMethod = attrs.get("asyncMethod");
        String asyncUnit = attrs.get("asyncUnit");
        if ("true".equalsIgnoreCase(asyncMethod) || "true".equalsIgnoreCase(asyncUnit)) {
            return true;
        }
        String text = site.getStatement().toLowerCase();
        return text.contains("thread") || text.contains("runnable") || text.contains("future") || text.contains("callable") || text.contains("executor");
    }

    private Map<String, List<AccessSite>> byMethod(List<AccessSite> sites) {
        Map<String, List<AccessSite>> grouped = new HashMap<>();
        for (AccessSite site : sites) {
            grouped.computeIfAbsent(site.getMethodSignature(), k -> new ArrayList<>()).add(site);
        }
        return grouped;
    }

    private List<AccessSite> sortByLine(List<AccessSite> sites) {
        List<AccessSite> sorted = new ArrayList<>(sites);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.getLineNumber(), b.getLineNumber()));
        return sorted;
    }

    private List<ViolationTuple> deduplicate(List<ViolationTuple> tuples) {
        Map<String, ViolationTuple> unique = new LinkedHashMap<>();
        for (ViolationTuple tuple : tuples) {
            unique.put(tuple.getId(), tuple);
        }
        return new ArrayList<>(unique.values());
    }

    @SafeVarargs
    private final <T> List<T> list(T... values) {
        List<T> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }
}
