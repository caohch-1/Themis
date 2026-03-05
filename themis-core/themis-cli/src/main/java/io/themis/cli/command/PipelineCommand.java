package io.themis.cli.command;

import io.themis.cli.orchestration.PipelineOrchestrator;
import io.themis.cli.util.ConfigLoader;
import io.themis.core.config.ThemisConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "pipeline", mixinStandardHelpOptions = true)
public class PipelineCommand implements Callable<Integer> {
    @Option(names = "--config", required = true)
    private Path configFile;

    @Option(names = "--out", required = false, defaultValue = "output/pipeline")
    private Path outDir;

    @Override
    public Integer call() throws Exception {
        ThemisConfig config = new ConfigLoader().load(configFile);
        new PipelineOrchestrator().runPipeline(config, outDir);
        return 0;
    }
}
