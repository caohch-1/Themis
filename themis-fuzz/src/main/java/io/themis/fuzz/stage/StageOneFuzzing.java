package io.themis.fuzz.stage;

import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.FuzzStage;
import io.themis.core.model.ParameterSeed;
import io.themis.core.model.SeedPoolState;
import io.themis.fuzz.distance.DistanceMetric;
import io.themis.fuzz.engine.DirectedFuzzingEngine;
import io.themis.fuzz.engine.FuzzExecutionResult;
import io.themis.fuzz.seed.SeedPoolManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StageOneFuzzing {
    private final DirectedFuzzingEngine engine;
    private final DistanceMetric distanceMetric;
    private final int batchSize;

    public StageOneFuzzing(DirectedFuzzingEngine engine, DistanceMetric distanceMetric, int batchSize) {
        this.engine = engine;
        this.distanceMetric = distanceMetric;
        this.batchSize = batchSize;
    }

    public StageOneResult run(ExecutionPathBundle bundle,
                              String testClass,
                              String testMethod,
                              List<String> extraTargets,
                              SeedPoolManager seedPool,
                              int budgetMinutes,
                              List<String> jqfArgs) {
        long budgetMillis = budgetMinutes * 60L * 1000L;
        long start = System.currentTimeMillis();
        List<String> targets = stageOneTargets(bundle, extraTargets);
        int iteration = 0;
        long bestDistance = Long.MAX_VALUE;
        ParameterSeed reachedSeed = null;

        while (System.currentTimeMillis() - start < budgetMillis) {
            List<ParameterSeed> seeds = seedPool.top(batchSize);
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
                seedPool.add(result.getSeed().withScore(scoreFor(result, d)));
                if (result.isReachedTargets() || d == 0) {
                    reachedSeed = result.getSeed();
                    SeedPoolState poolState = new SeedPoolState(bundle.getViolationId(), FuzzStage.STAGE_I, iteration, seedPool.all(), bestDistance);
                    return new StageOneResult(true, reachedSeed, poolState, result.getTrace());
                }
            }
            iteration++;
        }
        SeedPoolState poolState = new SeedPoolState(bundle.getViolationId(), FuzzStage.STAGE_I, iteration, seedPool.all(), bestDistance);
        return new StageOneResult(false, reachedSeed, poolState, new ArrayList<String>());
    }

    private double scoreFor(FuzzExecutionResult result, long distance) {
        if (distance == Long.MAX_VALUE) {
            return 0.0;
        }
        return 1.0 / (1 + distance) + (result.isReachedTargets() ? 1.0 : 0.0);
    }

    public List<String> stageOneTargets(ExecutionPathBundle bundle, List<String> extraTargets) {
        Set<String> targets = new LinkedHashSet<>();
        if (extraTargets != null && !extraTargets.isEmpty()) {
            targets.addAll(extraTargets);
            return new ArrayList<>(targets);
        }
        targets.add(bundle.getRpcPair().getClientClass() + ":" + bundle.getRpcPair().getClientMethod());
        targets.add(bundle.getRpcPair().getServerClass() + ":" + bundle.getRpcPair().getServerMethod());
        targets.addAll(bundle.getViolationTuple().getEnclosingFunctions());
        bundle.getViolationTuple().getAccessSites().forEach(site -> {
            targets.add(site.getStatement());
            targets.add(site.getId());
        });
        targets.addAll(bundle.getCallChain());
        return new ArrayList<>(targets);
    }
}
