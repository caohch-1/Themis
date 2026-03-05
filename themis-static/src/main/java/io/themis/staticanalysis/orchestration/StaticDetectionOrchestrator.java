package io.themis.staticanalysis.orchestration;

import io.themis.core.config.ThemisConfig;
import io.themis.core.model.AccessScope;
import io.themis.core.model.AccessSite;
import io.themis.core.model.RpcPair;
import io.themis.core.model.SharedVariable;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.SystemSide;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ViolationType;
import io.themis.staticanalysis.access.AccessClassifier;
import io.themis.staticanalysis.access.AccessSiteExtractor;
import io.themis.staticanalysis.access.AccessSiteSetBuilder;
import io.themis.staticanalysis.access.PointsToResolver;
import io.themis.staticanalysis.filter.ClientVariableFilter;
import io.themis.staticanalysis.filter.ServerVariableFilter;
import io.themis.staticanalysis.publicapi.EscapabilityPruner;
import io.themis.staticanalysis.publicapi.PublicInterfaceExtractor;
import io.themis.staticanalysis.publicapi.SynchronizationAnalyzer;
import io.themis.staticanalysis.rpc.RpcPairExtractor;
import io.themis.staticanalysis.rules.ViolationRuleEngine;
import io.themis.staticanalysis.symptom.ExplicitSymptomDetector;
import io.themis.staticanalysis.symptom.ImplicitPatternEngine;
import io.themis.staticanalysis.symptom.RpcEnhancedCallGraph;
import io.themis.staticanalysis.symptom.SymptomPropagator;
import io.themis.staticanalysis.traversal.CallTraversalResult;
import io.themis.staticanalysis.traversal.MethodNeighborhoodCollector;
import io.themis.staticanalysis.traversal.RpcClientUpwardTraversal;
import io.themis.staticanalysis.traversal.RpcServerDownwardTraversal;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticDetectionOrchestrator {
    private final SootBootstrap sootBootstrap = new SootBootstrap();
    private List<RpcPair> lastRpcPairs = new ArrayList<>();

    public List<ViolationTuple> run(ThemisConfig config) throws IOException, InterruptedException {
        sootBootstrap.initialize(config.getClassPath(), config.getCodeRoot());
        RpcPairExtractor rpcPairExtractor = new RpcPairExtractor(
            config.getStaticConfig().getRpcBridgeCommand(),
            config.getStaticConfig().getTargetSourceRoots());
        List<RpcPair> pairs = rpcPairExtractor.extractAndPrune(config.getCodeRoot());
        this.lastRpcPairs = new ArrayList<>(pairs);

        AccessSiteExtractor accessSiteExtractor = new AccessSiteExtractor(new AccessClassifier(), new PointsToResolver());
        RpcClientUpwardTraversal upwardTraversal = new RpcClientUpwardTraversal();
        RpcServerDownwardTraversal downwardTraversal = new RpcServerDownwardTraversal();
        MethodNeighborhoodCollector neighborhoodCollector = new MethodNeighborhoodCollector();
        ServerVariableFilter serverFilter = new ServerVariableFilter();
        ClientVariableFilter clientFilter = new ClientVariableFilter();
        AccessSiteSetBuilder setBuilder = new AccessSiteSetBuilder();
        ViolationRuleEngine ruleEngine = new ViolationRuleEngine();
        PublicInterfaceExtractor publicInterfaceExtractor = new PublicInterfaceExtractor();
        EscapabilityPruner escapabilityPruner = new EscapabilityPruner();
        SynchronizationAnalyzer synchronizationAnalyzer = new SynchronizationAnalyzer();
        RpcEnhancedCallGraph rpcEnhancedCallGraph = new RpcEnhancedCallGraph();
        SymptomPropagator symptomPropagator = new SymptomPropagator();
        ExplicitSymptomDetector explicitSymptomDetector = new ExplicitSymptomDetector();
        ImplicitPatternEngine implicitPatternEngine = new ImplicitPatternEngine(config.getStaticConfig().getThreadUnsafeRegistryPath());

        List<ViolationTuple> allTuples = new ArrayList<>();
        int unresolvedPairCount = 0;
        List<String> unresolvedPairs = new ArrayList<String>();
        for (RpcPair pair : pairs) {
            SootMethod clientMethod = resolve(pair.getClientClass(), pair.getClientMethod());
            SootMethod serverMethod = resolve(pair.getServerClass(), pair.getServerMethod());
            if (clientMethod == null || serverMethod == null) {
                unresolvedPairCount++;
                unresolvedPairs.add(pair.getClientClass() + "#" + pair.getClientMethod() + "->" + pair.getServerClass() + "#" + pair.getServerMethod());
                continue;
            }

            CallTraversalResult clientUp = upwardTraversal.traverse(clientMethod, 12);
            CallTraversalResult serverDown = downwardTraversal.traverse(serverMethod, 12);
            CallTraversalResult clientReach = new CallTraversalResult(mergeDepthMaps(clientUp.getDepthByMethod(), neighborhoodCollector.collect(clientMethod, 3)));
            CallTraversalResult serverReach = new CallTraversalResult(mergeDepthMaps(serverDown.getDepthByMethod(), neighborhoodCollector.collect(serverMethod, 3)));

            Set<String> rpcReachableClientMethods = new LinkedHashSet<>(clientUp.getDepthByMethod().keySet());
            Set<String> rpcReachableServerMethods = new LinkedHashSet<>(serverDown.getDepthByMethod().keySet());
            List<AccessSite> clientSites = collectAccesses(clientReach, rpcReachableClientMethods, accessSiteExtractor, SystemSide.CLIENT);
            List<AccessSite> serverSites = collectAccesses(serverReach, rpcReachableServerMethods, accessSiteExtractor, SystemSide.SERVER);
            List<AccessSite> allSites = new ArrayList<>();
            allSites.addAll(clientSites);
            allSites.addAll(serverSites);

            List<SharedVariable> candidates = extractCandidateVariables(allSites);
            List<SharedVariable> filtered = new ArrayList<>();
            filtered.addAll(serverFilter.filter(selectBySide(candidates, SystemSide.SERVER)));
            filtered.addAll(clientFilter.filter(selectBySide(candidates, SystemSide.CLIENT)));

            Map<String, List<AccessSite>> siteSets = setBuilder.build(filtered, allSites);
            for (SharedVariable variable : filtered) {
                List<AccessSite> accessSet = siteSets.getOrDefault(variable.getId(), new ArrayList<AccessSite>());
                allTuples.addAll(ruleEngine.detect(variable, accessSet, pair.getId()));
            }
        }
        if (!pairs.isEmpty() && unresolvedPairCount == pairs.size()) {
            throw new IllegalStateException("All RPC endpoint pairs were unresolved by Soot. Check classpath/codeRoot/targetSourceRoots. Unresolved=" + unresolvedPairs);
        }

        List<ViolationTuple> withApis = new ArrayList<>();
        for (ViolationTuple tuple : allTuples) {
            if ("R4".equals(tuple.getRuleId()) || "R5".equals(tuple.getRuleId())) {
                withApis.add(tuple.withPublicInterfacePair(publicInterfaceExtractor.extractForRules45(tuple)));
            } else {
                withApis.add(tuple.withPublicInterfacePair(publicInterfaceExtractor.extractForRules123(tuple)));
            }
        }

        List<ViolationTuple> escaped = escapabilityPruner.prune(withApis);
        List<ViolationTuple> syncPruned = new ArrayList<>();
        for (ViolationTuple tuple : escaped) {
            if (tuple.getType() == ViolationType.ATOMICITY && synchronizationAnalyzer.isProtected(tuple.getAccessSites())) {
                continue;
            }
            syncPruned.add(tuple);
        }

        Map<String, List<String>> graph = rpcEnhancedCallGraph.build(pairs);
        List<ViolationTuple> symptomMatched = new ArrayList<>();
        int hopBound = config.getStaticConfig().getPropagationHopBound();
        for (ViolationTuple tuple : syncPruned) {
            Map<String, Integer> reachable = symptomPropagator.reachableWithinHops(tuple.getAccessSites(), graph, hopBound);
            List<SymptomCandidate> explicit = explicitSymptomDetector.detect(tuple, reachable, hopBound);
            List<SymptomCandidate> implicit = implicitPatternEngine.match(tuple);
            List<SymptomCandidate> merged = new ArrayList<>(explicit);
            merged.addAll(implicit);
            if (!merged.isEmpty()) {
                symptomMatched.add(tuple.withSymptoms(merged));
            }
        }
        return deduplicate(symptomMatched);
    }

    private List<ViolationTuple> deduplicate(List<ViolationTuple> tuples) {
        Map<String, ViolationTuple> map = new LinkedHashMap<>();
        for (ViolationTuple tuple : tuples) {
            map.put(tuple.getId(), tuple);
        }
        return new ArrayList<>(map.values());
    }

    private List<AccessSite> collectAccesses(CallTraversalResult traversal,
                                             Set<String> rpcReachableMethods,
                                             AccessSiteExtractor extractor,
                                             SystemSide side) {
        List<AccessSite> collected = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : traversal.getDepthByMethod().entrySet()) {
            if (!Scene.v().containsMethod(entry.getKey())) {
                continue;
            }
            SootMethod method = Scene.v().getMethod(entry.getKey());
            boolean reachableFromRpc = rpcReachableMethods.contains(entry.getKey());
            collected.addAll(extractor.extract(method, side, reachableFromRpc, entry.getValue()));
        }
        return collected;
    }

    private List<SharedVariable> extractCandidateVariables(List<AccessSite> sites) {
        Map<String, SharedVariable> map = new HashMap<>();
        for (AccessSite site : sites) {
            String id = site.getClassName() + "::" + site.getVariable() + "::" + site.getScope().name();
            SharedVariable current = map.get(id);
            if (current == null) {
                List<String> paths = new ArrayList<>();
                if (site.getScope() == AccessScope.IO_OBJECT || site.getScope() == AccessScope.NIO_OBJECT) {
                    paths.add(site.getStatement());
                }
                List<String> accessSiteIds = new ArrayList<>();
                accessSiteIds.add(site.getId());
                current = new SharedVariable(
                    id,
                    site.getVariable(),
                    site.getClassName(),
                    site.getMethodSignature(),
                    site.getScope(),
                    site.getSide(),
                    inferType(site),
                    paths,
                    accessSiteIds);
            } else {
                current = current.withAccessSite(site.getId());
            }
            map.put(id, current);
        }
        return new ArrayList<>(map.values());
    }

    private String inferType(AccessSite site) {
        String statement = site.getStatement().toLowerCase();
        if (statement.contains("hashmap") || statement.contains("arraylist") || statement.contains("map") || statement.contains("list")) {
            return "collection";
        }
        if (statement.contains("[") && statement.contains("]")) {
            return "array";
        }
        if (statement.contains("java.io") || statement.contains("java.nio") || statement.contains("readline") || statement.contains("file")) {
            return "io";
        }
        return "object";
    }

    private List<SharedVariable> selectBySide(List<SharedVariable> variables, SystemSide side) {
        List<SharedVariable> selected = new ArrayList<>();
        for (SharedVariable variable : variables) {
            if (variable.getSide() == side) {
                selected.add(variable);
            }
        }
        return selected;
    }

    private SootMethod resolve(String className, String methodName) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        SootMethod direct = resolveInClass(className, methodName);
        if (direct != null) {
            return direct;
        }
        String nestedPrefix = className + "$";
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            if (!sootClass.getName().startsWith(nestedPrefix)) {
                continue;
            }
            for (SootMethod method : sootClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
        }
        for (SootClass sootClass : Scene.v().getLibraryClasses()) {
            if (!sootClass.getName().startsWith(nestedPrefix)) {
                continue;
            }
            for (SootMethod method : sootClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
        }
        return null;
    }

    private SootMethod resolveInClass(String className, String methodName) {
        if (!Scene.v().containsClass(className)) {
            try {
                SootClass resolved = Scene.v().forceResolve(className, SootClass.BODIES);
                if (resolved != null && !resolved.isPhantom()) {
                    resolved.setApplicationClass();
                }
                Scene.v().loadClassAndSupport(className);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (!Scene.v().containsClass(className)) {
            return null;
        }
        SootClass sootClass = Scene.v().getSootClass(className);
        if (sootClass.isPhantom()) {
            return null;
        }
        for (SootMethod method : sootClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    public Map<String, List<String>> buildAdjacency() {
        Map<String, List<String>> adjacency = new HashMap<>();
        Set<String> methods = new LinkedHashSet<>();
        for (SootClass c : Scene.v().getApplicationClasses()) {
            for (SootMethod m : c.getMethods()) {
                methods.add(m.getSignature());
            }
        }
        for (String method : methods) {
            adjacency.put(method, new ArrayList<String>());
        }
        if (Scene.v().hasCallGraph()) {
            CallGraph cg = Scene.v().getCallGraph();
            java.util.Iterator<Edge> iterator = cg.iterator();
            while (iterator.hasNext()) {
                Edge edge = iterator.next();
                String src = edge.src().getSignature();
                String tgt = edge.tgt().getSignature();
                adjacency.computeIfAbsent(src, k -> new ArrayList<String>());
                adjacency.computeIfAbsent(tgt, k -> new ArrayList<String>());
                adjacency.get(src).add(tgt);
            }
        }
        return adjacency;
    }

    private Map<String, Integer> mergeDepthMaps(Map<String, Integer> first, Map<String, Integer> second) {
        Map<String, Integer> merged = new HashMap<>();
        merged.putAll(first);
        for (Map.Entry<String, Integer> entry : second.entrySet()) {
            Integer existing = merged.get(entry.getKey());
            if (existing == null || entry.getValue() < existing) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    public List<RpcPair> getLastRpcPairs() {
        return new ArrayList<>(lastRpcPairs);
    }
}
