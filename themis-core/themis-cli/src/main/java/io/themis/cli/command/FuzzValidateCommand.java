package io.themis.cli.command;

import io.themis.cli.orchestration.PipelineOrchestrator;
import io.themis.cli.util.ConfigLoader;
import io.themis.core.config.ThemisConfig;
import io.themis.core.io.JsonIo;
import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.GeneratedTestArtifact;
import io.themis.core.model.RpcPair;
import io.themis.core.model.ViolationTuple;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "fuzz-validate", mixinStandardHelpOptions = true)
public class FuzzValidateCommand implements Callable<Integer> {
    @Option(names = "--config", required = true)
    private Path configFile;

    @Option(names = "--violations", required = true)
    private Path violationsFile;

    @Option(names = "--rpc-pairs", required = true)
    private Path rpcPairsFile;

    @Option(names = "--tests", required = true)
    private Path testsFile;

    @Option(names = "--out", required = false, defaultValue = "output/fuzz")
    private Path outDir;

    @Override
    public Integer call() throws Exception {
        ThemisConfig config = new ConfigLoader().load(configFile);
        JsonIo io = new JsonIo();
        GeneratedTestArtifact[] tests = io.read(testsFile, GeneratedTestArtifact[].class);
        ViolationTuple[] violations = io.read(violationsFile, ViolationTuple[].class);
        RpcPair[] rpcPairs = io.read(rpcPairsFile, RpcPair[].class);

        PipelineOrchestrator orchestrator = new PipelineOrchestrator();
        List<ExecutionPathBundle> bundles = orchestrator.buildExecutionBundles(Arrays.asList(violations), Arrays.asList(rpcPairs));
        List<FuzzOutcome> outcomes = orchestrator.fuzzValidate(config, bundles, Arrays.asList(tests));
        io.write(outDir.resolve("fuzz_outcomes.json"), outcomes);
        return 0;
    }
}
