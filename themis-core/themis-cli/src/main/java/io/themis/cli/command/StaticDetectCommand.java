package io.themis.cli.command;

import io.themis.cli.orchestration.PipelineOrchestrator;
import io.themis.cli.util.ConfigLoader;
import io.themis.core.config.ThemisConfig;
import io.themis.core.io.JsonIo;
import io.themis.core.model.ViolationTuple;
import io.themis.core.report.ArtifactWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "static-detect", mixinStandardHelpOptions = true)
public class StaticDetectCommand implements Callable<Integer> {
    @Option(names = "--config", required = true)
    private Path configFile;

    @Option(names = "--out", required = false, defaultValue = "output/static")
    private Path outDir;

    @Override
    public Integer call() throws Exception {
        ThemisConfig config = new ConfigLoader().load(configFile);
        PipelineOrchestrator orchestrator = new PipelineOrchestrator();
        List<ViolationTuple> tuples = orchestrator.staticDetect(config);
        Files.createDirectories(outDir);
        ArtifactWriter writer = new ArtifactWriter(new JsonIo());
        writer.writeViolations(outDir, tuples);
        writer.writeRpcPairs(outDir, orchestrator.getLastRpcPairs());
        return 0;
    }
}
