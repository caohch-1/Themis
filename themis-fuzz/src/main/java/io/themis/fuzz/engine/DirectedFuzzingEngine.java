package io.themis.fuzz.engine;

import io.themis.core.model.ParameterSeed;
import io.themis.fuzz.distance.ApsScheduler;
import io.themis.fuzz.distance.DistanceMetric;

import java.util.ArrayList;
import java.util.List;

public class DirectedFuzzingEngine {
    private final SelectFuzzMutator selectFuzzMutator;
    private final JqfInvoker jqfInvoker;
    private final DistanceMetric distanceMetric;
    private final ApsScheduler apsScheduler;
    private final int mutationBatchSize;

    public DirectedFuzzingEngine(SelectFuzzMutator selectFuzzMutator,
                                 JqfInvoker jqfInvoker,
                                 DistanceMetric distanceMetric,
                                 ApsScheduler apsScheduler,
                                 int mutationBatchSize) {
        this.selectFuzzMutator = selectFuzzMutator;
        this.jqfInvoker = jqfInvoker;
        this.distanceMetric = distanceMetric;
        this.apsScheduler = apsScheduler;
        this.mutationBatchSize = mutationBatchSize;
    }

    public List<FuzzExecutionResult> fuzzRound(String testClass,
                                               String method,
                                               List<ParameterSeed> seeds,
                                               List<String> targets,
                                               List<String> jqfArgs,
                                               long elapsedMillis,
                                               int budgetMillis) {
        List<ParameterSeed> mutated = selectFuzzMutator.mutateBatch(seeds, mutationBatchSize, jqfArgs, targets);
        List<FuzzExecutionResult> results = new ArrayList<>();
        List<Long> distances = new ArrayList<>();
        for (ParameterSeed seed : mutated) {
            FuzzExecutionResult result = jqfInvoker.execute(testClass, method, seed, targets, jqfArgs);
            results.add(result);
            long distance = distanceMetric.distance(result.getTrace(), targets);
            distances.add(distance);
        }
        List<ParameterSeed> rescored = apsScheduler.scoreAndSelect(mutated, distances, elapsedMillis, budgetMillis, mutationBatchSize);
        List<FuzzExecutionResult> ordered = new ArrayList<>();
        for (ParameterSeed selected : rescored) {
            for (FuzzExecutionResult result : results) {
                if (result.getSeed().getId().equals(selected.getId())) {
                    ordered.add(result);
                    break;
                }
            }
        }
        return ordered;
    }

    public JqfInvoker getJqfInvoker() {
        return jqfInvoker;
    }
}
