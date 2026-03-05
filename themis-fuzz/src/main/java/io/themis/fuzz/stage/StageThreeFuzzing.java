package io.themis.fuzz.stage;

import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.FuzzStage;
import io.themis.core.model.InterleavingShape;
import io.themis.core.model.ParameterSeed;
import io.themis.core.model.SeedPoolState;
import io.themis.core.model.SymptomCandidate;
import io.themis.fuzz.distance.DistanceMetric;
import io.themis.fuzz.engine.DirectedFuzzingEngine;
import io.themis.fuzz.engine.FuzzExecutionResult;
import io.themis.fuzz.engine.JqfInvoker;
import io.themis.fuzz.instrumentation.BasicBlockInstrumenter;
import io.themis.fuzz.instrumentation.SymptomConditionInstrumenter;
import io.themis.fuzz.seed.SeedPoolManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StageThreeFuzzing {
    private final DirectedFuzzingEngine engine;
    private final DistanceMetric distanceMetric;
    private final StageTwoInterleavingExplorer stageTwo;
    private final SymptomConditionInstrumenter conditionInstrumenter;
    private final BasicBlockInstrumenter blockInstrumenter;
    private final JqfInvoker jqfInvoker;
    private final int batchSize;

    public StageThreeFuzzing(DirectedFuzzingEngine engine,
                             DistanceMetric distanceMetric,
                             StageTwoInterleavingExplorer stageTwo,
                             SymptomConditionInstrumenter conditionInstrumenter,
                             BasicBlockInstrumenter blockInstrumenter,
                             JqfInvoker jqfInvoker,
                             int batchSize) {
        this.engine = engine;
        this.distanceMetric = distanceMetric;
        this.stageTwo = stageTwo;
        this.conditionInstrumenter = conditionInstrumenter;
        this.blockInstrumenter = blockInstrumenter;
        this.jqfInvoker = jqfInvoker;
        this.batchSize = batchSize;
    }

    public StageThreeResult run(ExecutionPathBundle bundle,
                                String testClass,
                                String testMethod,
                                List<String> extraTargets,
                                StageOneResult stageOne,
                                int budgetMinutes,
                                List<String> jqfArgs) {
        SeedPoolManager pool = new SeedPoolManager();
        pool.addAll(stageOne.getSeedPoolState().getSeeds());
        if (stageOne.getReachedSeed() != null) {
            pool.add(stageOne.getReachedSeed());
        }
        Set<String> targetSet = new LinkedHashSet<String>(new StageOneFuzzing(engine, distanceMetric, batchSize).stageOneTargets(bundle, extraTargets));
        for (String condition : conditionInstrumenter.synthesizeTargets(bundle.getViolationTuple().getSymptomCandidates())) {
            targetSet.add(blockInstrumenter.probeIdFor(condition));
        }
        for (SymptomCandidate candidate : bundle.getViolationTuple().getSymptomCandidates()) {
            String statement = candidate.getStatement();
            if (statement != null && !statement.trim().isEmpty()) {
                targetSet.add(blockInstrumenter.probeIdFor(statement.trim()));
            }
        }
        List<String> targets = new ArrayList<String>(targetSet);

        long budgetMillis = budgetMinutes * 60L * 1000L;
        long start = System.currentTimeMillis();
        long bestDistance = Long.MAX_VALUE;
        int iteration = 0;

        while (System.currentTimeMillis() - start < budgetMillis) {
            List<ParameterSeed> seeds = pool.top(batchSize);
            if (seeds.isEmpty()) {
                break;
            }
            List<FuzzExecutionResult> results = engine.fuzzRound(
                testClass,
                testMethod,
                seeds,
                targets,
                jqfArgs,
                System.currentTimeMillis() - start,
                (int) budgetMillis);

            for (FuzzExecutionResult result : results) {
                long d = distanceMetric.distance(result.getTrace(), targets);
                if (d < bestDistance) {
                    bestDistance = d;
                }
                pool.add(result.getSeed().withScore(1.0 / (1 + Math.max(0, d))));

                if (d == 0 || result.isReachedTargets()) {
                    InterleavingRuntimeExecutor runtimeExecutor = new JqfInterleavingRuntimeExecutor(
                        jqfInvoker,
                        testClass,
                        testMethod,
                        result.getSeed(),
                        jqfArgs);
                    StageTwoResult stageTwoResult = stageTwo.run(bundle.getViolationTuple(), result.getTrace(), runtimeExecutor);
                    if (stageTwoResult.isAllInterleavingsReached() && stageTwoResult.isExposedInStageTwo()) {
                        FuzzOutcome outcome = buildOutcome(bundle, true, stageTwoResult, pool, iteration, bestDistance);
                        return new StageThreeResult(true, outcome);
                    }
                }
            }
            iteration++;
        }
        FuzzOutcome outcome = buildOutcome(bundle, false, new StageTwoResult(false, false, new ArrayList<io.themis.fuzz.check.BehaviorObservation>(), new ArrayList<io.themis.fuzz.interleaving.InterleavingPlan>()), pool, iteration, bestDistance);
        return new StageThreeResult(false, outcome);
    }

    private FuzzOutcome buildOutcome(ExecutionPathBundle bundle,
                                     boolean exposed,
                                     StageTwoResult stageTwo,
                                     SeedPoolManager pool,
                                     int iteration,
                                     long bestDistance) {
        Set<String> reachedTargets = new LinkedHashSet<>();
        for (String c : bundle.getCallChain()) {
            reachedTargets.add(c);
        }
        Set<InterleavingShape> interleavings = new LinkedHashSet<>();
        stageTwo.getFeasiblePlans().forEach(plan -> interleavings.add(plan.getShape()));
        List<SymptomCandidate> symptoms = new ArrayList<>();
        stageTwo.getObservations().forEach(ob -> symptoms.addAll(ob.getSymptoms()));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("bestDistance", Long.toString(bestDistance));
        metadata.put("iteration", Integer.toString(iteration));
        return new FuzzOutcome(
            bundle.getViolationId(),
            exposed,
            exposed ? FuzzStage.STAGE_III : FuzzStage.STAGE_II,
            new ArrayList<>(reachedTargets),
            new ArrayList<>(interleavings),
            symptoms,
            new SeedPoolState(bundle.getViolationId(), FuzzStage.STAGE_III, iteration, pool.all(), bestDistance),
            metadata);
    }
}
