package io.themis.fuzz.stage;

import io.themis.core.model.AccessSite;
import io.themis.core.model.InterleavingShape;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ViolationType;
import io.themis.fuzz.check.BehaviorChecker;
import io.themis.fuzz.check.BehaviorObservation;
import io.themis.fuzz.instrumentation.ProbeCatalog;
import io.themis.fuzz.interleaving.InterleavingFeasibilityPruner;
import io.themis.fuzz.interleaving.InterleavingPlan;
import io.themis.fuzz.interleaving.SignalWaitController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StageTwoInterleavingExplorer {
    private final SignalWaitController controller;
    private final InterleavingFeasibilityPruner pruner;
    private final BehaviorChecker checker;
    private final ProbeCatalog probeCatalog = new ProbeCatalog();

    public StageTwoInterleavingExplorer(SignalWaitController controller,
                                        InterleavingFeasibilityPruner pruner,
                                        BehaviorChecker checker) {
        this.controller = controller;
        this.pruner = pruner;
        this.checker = checker;
    }

    public StageTwoResult run(ViolationTuple tuple,
                              List<String> trace,
                              InterleavingRuntimeExecutor runtimeExecutor) {
        List<String> safeTrace = trace == null ? new ArrayList<String>() : trace;
        List<InterleavingPlan> plans = plansFor(tuple);
        if (plans.isEmpty()) {
            return new StageTwoResult(false, false, new ArrayList<BehaviorObservation>(), plans);
        }
        List<InterleavingPlan> feasible = pruner.pruneUnreachable(plans, safeTrace);
        List<BehaviorObservation> observations = new ArrayList<>();
        boolean allReached = feasible.size() == plans.size();
        for (InterleavingPlan plan : feasible) {
            InterleavingRuntimeResult runtime = runtimeExecutor.execute(tuple, plan, controller.runtimeProperties(plan));
            if (!runtime.isReachedPlannedInterleaving() || !controller.reachedPlan(runtime.getTrace(), plan)) {
                allReached = false;
            }
            observations.add(new BehaviorObservation(plan.getShape(), runtime.getObservedSymptoms()));
        }
        InterleavingShape buggy = tuple.getInterleavingShapes().isEmpty() ? defaultBuggy(tuple.getType()) : tuple.getInterleavingShapes().get(0);
        boolean exposed = checker.manifestsOnlyUnderBuggyInterleaving(tuple.getType(), observations, buggy);
        boolean threadUnsafeOnly = checker.onlyThreadUnsafeEvidence(observations);
        return new StageTwoResult(allReached, exposed || threadUnsafeOnly, observations, feasible);
    }

    private InterleavingShape defaultBuggy(ViolationType type) {
        return type == ViolationType.ORDER ? InterleavingShape.ORDER_FORWARD : InterleavingShape.ATOMICITY_MIDDLE;
    }

    private List<InterleavingPlan> plansFor(ViolationTuple tuple) {
        List<AccessSite> sites = tuple.getAccessSites();
        if (sites.size() < 2) {
            return new ArrayList<InterleavingPlan>();
        }
        if (tuple.getType() == ViolationType.ORDER) {
            return Arrays.asList(
                new InterleavingPlan(InterleavingShape.ORDER_FORWARD, sequence(probe(sites.get(0)), probe(sites.get(1)))),
                new InterleavingPlan(InterleavingShape.ORDER_REVERSE, sequence(probe(sites.get(1)), probe(sites.get(0)))));
        }
        if (sites.size() < 3) {
            return new ArrayList<InterleavingPlan>();
        }
        return Arrays.asList(
            new InterleavingPlan(InterleavingShape.ATOMICITY_BEFORE, sequence(probe(sites.get(2)), probe(sites.get(0)), probe(sites.get(1)))),
            new InterleavingPlan(InterleavingShape.ATOMICITY_MIDDLE, sequence(probe(sites.get(0)), probe(sites.get(2)), probe(sites.get(1)))),
            new InterleavingPlan(InterleavingShape.ATOMICITY_AFTER, sequence(probe(sites.get(0)), probe(sites.get(1)), probe(sites.get(2)))));
    }

    private String probe(AccessSite site) {
        return probeCatalog.probeIdForTarget(site.getId());
    }

    private List<String> sequence(String a, String b) {
        List<String> sequence = new ArrayList<>();
        sequence.add(a);
        sequence.add(b);
        return sequence;
    }

    private List<String> sequence(String a, String b, String c) {
        List<String> sequence = sequence(a, b);
        sequence.add(c);
        return sequence;
    }
}
