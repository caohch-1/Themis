package io.themis.cli.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PythonInvoker {
    public int runLlmGeneration(Path bundleFile,
                                Path adjacencyFile,
                                Path outputFile,
                                String model,
                                String apiKeyEnv,
                                int repairThreshold,
                                int restartCycles,
                                String compileClassPath,
                                String runtimeClassPath,
                                String buildOracleCommand) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("python3");
        cmd.add("-m");
        cmd.add("themis_llm.cli");
        cmd.add("--bundle");
        cmd.add(bundleFile.toString());
        cmd.add("--adjacency");
        cmd.add(adjacencyFile.toString());
        cmd.add("--output");
        cmd.add(outputFile.toString());
        cmd.add("--model");
        cmd.add(model);
        cmd.add("--api-key-env");
        cmd.add(apiKeyEnv);
        cmd.add("--repair-threshold");
        cmd.add(Integer.toString(repairThreshold));
        cmd.add("--restart-cycles");
        cmd.add(Integer.toString(restartCycles));
        cmd.add("--compile-classpath");
        cmd.add(compileClassPath == null ? "" : compileClassPath);
        cmd.add("--runtime-classpath");
        cmd.add(runtimeClassPath == null ? "" : runtimeClassPath);
        cmd.add("--build-oracle");
        cmd.add(buildOracleCommand == null ? "" : buildOracleCommand);

        Process process = new ProcessBuilder(cmd)
            .directory(Paths.get("themis-llm").toFile())
            .redirectErrorStream(true)
            .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("LLM generation failed with exit code " + exit + "\n" + output.toString());
        }
        return exit;
    }
}
