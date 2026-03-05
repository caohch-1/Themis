package io.themis.fuzz.orchestration;

import io.themis.core.config.ThemisConfig;
import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.GeneratedTestArtifact;
import io.themis.core.model.ParameterSeed;
import io.themis.fuzz.check.BehaviorChecker;
import io.themis.fuzz.distance.ApsScheduler;
import io.themis.fuzz.distance.DistanceMetric;
import io.themis.fuzz.engine.DirectedFuzzingEngine;
import io.themis.fuzz.engine.JqfInvoker;
import io.themis.fuzz.engine.SelectFuzzMutator;
import io.themis.fuzz.instrumentation.BasicBlockInstrumenter;
import io.themis.fuzz.instrumentation.SymptomConditionInstrumenter;
import io.themis.fuzz.interleaving.InterleavingFeasibilityPruner;
import io.themis.fuzz.interleaving.SignalWaitController;
import io.themis.fuzz.runtime.GeneratedTestMaterializer;
import io.themis.fuzz.runtime.GeneratedTestRuntimeArtifact;
import io.themis.fuzz.seed.ConstantSeedSource;
import io.themis.fuzz.seed.LlmSeedSource;
import io.themis.fuzz.seed.RandomSeedSource;
import io.themis.fuzz.seed.SeedPoolManager;
import io.themis.fuzz.stage.StageOneFuzzing;
import io.themis.fuzz.stage.StageOneResult;
import io.themis.fuzz.stage.StageThreeFuzzing;
import io.themis.fuzz.stage.StageThreeResult;
import io.themis.fuzz.stage.InterleavingRuntimeExecutor;
import io.themis.fuzz.stage.JqfInterleavingRuntimeExecutor;
import io.themis.fuzz.stage.StageTwoInterleavingExplorer;
import io.themis.fuzz.stage.StageTwoResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FuzzValidationOrchestrator {
    public List<FuzzOutcome> run(ThemisConfig config,
                                 List<ExecutionPathBundle> bundles,
                                 List<GeneratedTestArtifact> tests) {
        DirectedFuzzingEngine engine = new DirectedFuzzingEngine(
            new SelectFuzzMutator(),
            new JqfInvoker("third_party/jqf"),
            new DistanceMetric(),
            new ApsScheduler(),
            config.getFuzzConfig().getMutationBatchSize());
        JqfInvoker jqfInvoker = engine.getJqfInvoker();
        StageTwoInterleavingExplorer stageTwo = new StageTwoInterleavingExplorer(
            new SignalWaitController(),
            new InterleavingFeasibilityPruner(),
            new BehaviorChecker());
        StageThreeFuzzing stageThree = new StageThreeFuzzing(
            engine,
            new DistanceMetric(),
            stageTwo,
            new SymptomConditionInstrumenter(),
            new BasicBlockInstrumenter(),
            jqfInvoker,
            config.getFuzzConfig().getMutationBatchSize());

        BasicBlockInstrumenter blockInstrumenter = new BasicBlockInstrumenter();
        GeneratedTestMaterializer testMaterializer = new GeneratedTestMaterializer();

        List<FuzzOutcome> outcomes = new ArrayList<>();
        for (ExecutionPathBundle bundle : bundles) {
            GeneratedTestArtifact testArtifact = findTest(bundle, tests);
            if (testArtifact == null || !testArtifact.isExecutable()) {
                outcomes.add(new FuzzOutcome(
                    bundle.getViolationId(),
                    false,
                    io.themis.core.model.FuzzStage.STAGE_I,
                    new ArrayList<String>(),
                    new ArrayList<io.themis.core.model.InterleavingShape>(),
                    new ArrayList<io.themis.core.model.SymptomCandidate>(),
                    new io.themis.core.model.SeedPoolState(bundle.getViolationId(), io.themis.core.model.FuzzStage.STAGE_I, 0, new ArrayList<io.themis.core.model.ParameterSeed>(), Long.MAX_VALUE),
                    Collections.singletonMap("pruned", "missing_executable_test")));
                continue;
            }

            GeneratedTestRuntimeArtifact runtimeTest = testMaterializer.materialize(
                testArtifact,
                bundle,
                testMaterializer.mergeClassPath(config.getClassPath(), System.getProperty("java.class.path", "")),
                config.getCodeRoot());
            if (!runtimeTest.isCompiled()) {
                String reason = runtimeTest.getError() == null ? "" : runtimeTest.getError();
                String pruned = reason.contains("compile_failed:") || reason.contains("missing_source:")
                    ? "target_instrumentation_failed:" + reason
                    : "generated_test_compile_failed:" + reason;
                outcomes.add(new FuzzOutcome(
                    bundle.getViolationId(),
                    false,
                    io.themis.core.model.FuzzStage.STAGE_I,
                    new ArrayList<String>(),
                    new ArrayList<io.themis.core.model.InterleavingShape>(),
                    new ArrayList<io.themis.core.model.SymptomCandidate>(),
                    new io.themis.core.model.SeedPoolState(bundle.getViolationId(), io.themis.core.model.FuzzStage.STAGE_I, 0, new ArrayList<io.themis.core.model.ParameterSeed>(), Long.MAX_VALUE),
                    Collections.singletonMap("pruned", pruned)));
                continue;
            }

            List<String> plannedTargets = new ArrayList<String>();
            bundle.getViolationTuple().getAccessSites().forEach(site -> plannedTargets.add(site.getId()));
            if (bundle.getTargetSymptomStatement() != null && !bundle.getTargetSymptomStatement().trim().isEmpty()) {
                plannedTargets.add(bundle.getTargetSymptomStatement());
            }
            List<String> probes = blockInstrumenter.instrument(plannedTargets);
            List<String> stageTargets = new ArrayList<String>(probes);

            SeedPoolManager pool = initializePool(bundle, testArtifact);
            StageOneFuzzing stageOne = new StageOneFuzzing(engine, new DistanceMetric(), config.getFuzzConfig().getMutationBatchSize());

            String testClass = runtimeTest.getQualifiedClassName();
            String testMethod = pickEntryMethod(testArtifact);
            List<String> jqfArgs = mergedArgs(config, testArtifact, testMaterializer, runtimeTest.getClassesDir().toString());

            StageOneResult stageOneResult = stageOne.run(
                bundle,
                testClass,
                testMethod,
                stageTargets,
                pool,
                config.getFuzzConfig().getStageTimeBudgetMinutes(),
                jqfArgs);

            if (!stageOneResult.isReachedTargets()) {
                outcomes.add(new FuzzOutcome(bundle.getViolationId(), false, io.themis.core.model.FuzzStage.STAGE_I, new ArrayList<String>(), new ArrayList<io.themis.core.model.InterleavingShape>(), new ArrayList<io.themis.core.model.SymptomCandidate>(), stageOneResult.getSeedPoolState(), Collections.singletonMap("pruned", "stage1")));
                continue;
            }

            ParameterSeed interleavingSeed = stageOneResult.getReachedSeed();
            if (interleavingSeed == null) {
                interleavingSeed = fallbackSeed(stageOneResult);
            }
            InterleavingRuntimeExecutor runtimeExecutor = new JqfInterleavingRuntimeExecutor(
                jqfInvoker,
                testClass,
                testMethod,
                interleavingSeed,
                jqfArgs);
            StageTwoResult stageTwoResult = stageTwo.run(bundle.getViolationTuple(), stageOneResult.getReachedTrace(), runtimeExecutor);
            if (stageTwoResult.isAllInterleavingsReached() && stageTwoResult.isExposedInStageTwo()) {
                outcomes.add(new FuzzOutcome(bundle.getViolationId(), true, io.themis.core.model.FuzzStage.STAGE_II, bundle.getCallChain(), new ArrayList<io.themis.core.model.InterleavingShape>(), new BehaviorChecker().flattenSymptoms(stageTwoResult.getObservations()), stageOneResult.getSeedPoolState(), Collections.singletonMap("exposed", "stage2")));
                continue;
            }

            StageThreeResult stageThreeResult = stageThree.run(
                bundle,
                testClass,
                testMethod,
                stageTargets,
                stageOneResult,
                config.getFuzzConfig().getStageTimeBudgetMinutes(),
                jqfArgs);
            outcomes.add(stageThreeResult.getOutcome());
        }
        return outcomes;
    }

    private ParameterSeed fallbackSeed(StageOneResult stageOneResult) {
        List<ParameterSeed> seeds = stageOneResult.getSeedPoolState().getSeeds();
        if (seeds.isEmpty()) {
            return new ParameterSeed("stage2-fallback", Collections.singletonMap("p0", "1"), 0.0, "fallback");
        }
        return seeds.get(0);
    }

    private List<String> mergedArgs(ThemisConfig config,
                                    GeneratedTestArtifact testArtifact,
                                    GeneratedTestMaterializer materializer,
                                    String generatedClassesDir) {
        List<String> args = new ArrayList<String>();
        args.addAll(sanitizeArgs(config.getFuzzConfig().getJqfArgs()));
        args.addAll(config.getFuzzConfig().getSelectFuzzArgs());
        String configuredClassPath = materializer.mergeClassPath(
            generatedClassesDir,
            config.getClassPath(),
            System.getProperty("java.class.path", ""));
        if (configuredClassPath != null && !configuredClassPath.trim().isEmpty()) {
            args.add("-cp");
            args.add(configuredClassPath);
        }
        if (testArtifact.getCode() != null && !testArtifact.getCode().isEmpty()) {
            args.add("-Dthemis.test.id=" + testArtifact.getId());
        }
        return args;
    }

    private List<String> sanitizeArgs(List<String> args) {
        List<String> sanitized = new ArrayList<String>();
        if (args == null) {
            return sanitized;
        }
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-cp".equals(arg) || "--classpath".equals(arg)) {
                if (i + 1 < args.size()) {
                    i++;
                }
                continue;
            }
            if (arg.startsWith("-cp=") || arg.startsWith("--classpath=")) {
                continue;
            }
            sanitized.add(arg);
        }
        return sanitized;
    }

    private String pickEntryMethod(GeneratedTestArtifact artifact) {
        String code = artifact.getCode() == null ? "" : artifact.getCode();
        if (code.contains("@Fuzz") && code.contains("(") && code.contains(")")) {
            String[] lines = code.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].contains("@Fuzz") || i + 1 >= lines.length) {
                    continue;
                }
                String next = lines[i + 1].trim();
                int idx = next.indexOf('(');
                if (idx > 0) {
                    String[] parts = next.substring(0, idx).trim().split("\\s+");
                    if (parts.length > 0) {
                        return parts[parts.length - 1];
                    }
                }
            }
        }
        if (code.contains(" main(")) {
            return "main";
        }
        return "main";
    }

    private GeneratedTestArtifact findTest(ExecutionPathBundle bundle, List<GeneratedTestArtifact> tests) {
        for (GeneratedTestArtifact test : tests) {
            if (test.getViolationId().equals(bundle.getViolationId())) {
                return test;
            }
        }
        return null;
    }

    private SeedPoolManager initializePool(ExecutionPathBundle bundle, GeneratedTestArtifact artifact) {
        SeedPoolManager pool = new SeedPoolManager();
        RandomSeedSource randomSource = new RandomSeedSource();
        LlmSeedSource llmSource = new LlmSeedSource();
        ConstantSeedSource constantSource = new ConstantSeedSource();
        List<String> parameterNames = new ArrayList<>();
        if (artifact != null) {
            parameterNames.addAll(artifact.getPrimaryParameters().keySet());
        }
        if (parameterNames.isEmpty()) {
            parameterNames.add("p0");
            parameterNames.add("p1");
            parameterNames.add("p2");
        }
        List<ParameterSeed> randomSeeds = randomSource.generate(bundle.getViolationId(), parameterNames, 16);
        pool.addAll(randomSeeds);
        if (artifact != null) {
            pool.addAll(llmSource.fromArtifact(artifact));
        }
        pool.addAll(constantSource.fromExecutionPath(bundle, parameterNames));
        return pool;
    }

    private Map<String, List<String>> buildBundleAdjacency(ExecutionPathBundle bundle) {
        Map<String, List<String>> adjacency = new HashMap<String, List<String>>();
        List<String> chain = bundle.getCallChain();
        for (int i = 0; i < chain.size(); i++) {
            adjacency.computeIfAbsent(chain.get(i), k -> new ArrayList<String>());
            if (i + 1 < chain.size()) {
                adjacency.get(chain.get(i)).add(chain.get(i + 1));
            }
        }
        for (String function : bundle.getViolationTuple().getEnclosingFunctions()) {
            adjacency.computeIfAbsent(function, k -> new ArrayList<String>());
        }
        Set<String> statements = new LinkedHashSet<String>();
        bundle.getViolationTuple().getAccessSites().forEach(site -> statements.add(site.getStatement()));
        for (String statement : statements) {
            adjacency.computeIfAbsent(statement, k -> new ArrayList<String>());
        }
        return adjacency;
    }
}
