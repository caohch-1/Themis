package io.themis.cli.command;

import io.themis.cli.orchestration.PipelineOrchestrator;
import io.themis.cli.util.ConfigLoader;
import io.themis.core.config.ThemisConfig;
import io.themis.core.io.JsonIo;
import io.themis.core.model.GeneratedTestArtifact;
import io.themis.core.model.RpcPair;
import io.themis.core.model.ViolationTuple;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "generate-tests", mixinStandardHelpOptions = true)
public class GenerateTestsCommand implements Callable<Integer> {
    @Option(names = "--config", required = true)
    private Path configFile;

    @Option(names = "--violations", required = true)
    private Path violationsFile;

    @Option(names = "--rpc-pairs", required = true)
    private Path rpcPairsFile;

    @Option(names = "--out", required = false, defaultValue = "output/tests")
    private Path outDir;

    @Override
    public Integer call() throws Exception {
        ThemisConfig config = new ConfigLoader().load(configFile);
        ViolationTuple[] tuples = new JsonIo().read(violationsFile, ViolationTuple[].class);
        RpcPair[] rpcPairs = new JsonIo().read(rpcPairsFile, RpcPair[].class);
        List<GeneratedTestArtifact> tests = new PipelineOrchestrator().generateTests(config, Arrays.asList(tuples), Arrays.asList(rpcPairs), outDir);
        new JsonIo().write(outDir.resolve("tests.json"), tests);
        return 0;
    }
}
