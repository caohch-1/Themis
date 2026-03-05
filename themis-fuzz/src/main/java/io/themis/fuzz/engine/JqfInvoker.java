package io.themis.fuzz.engine;

import io.themis.core.model.ParameterSeed;
import io.themis.fuzz.instrumentation.ProbeCatalog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JqfInvoker {
    private final String jqfRepoPath;
    private final ProbeCatalog probeCatalog = new ProbeCatalog();
    private final SeedSerializer seedSerializer = new SeedSerializer();

    public JqfInvoker(String jqfRepoPath) {
        this.jqfRepoPath = jqfRepoPath;
    }

    public FuzzExecutionResult execute(String testClass,
                                       String method,
                                       ParameterSeed seed,
                                       List<String> targets,
                                       List<String> extraArgs) {
        List<String> trace = new ArrayList<String>();
        boolean reached = false;
        Path outDir = null;
        try {
            outDir = Files.createTempDirectory("themis-jqf-");
            InvocationConfig invocation = parseArgs(extraArgs);
            Set<String> targetProbes = targetProbes(targets);
            Path seedFile = seedSerializer.writeSeed(outDir, seed);
            List<String> command = new ArrayList<String>();
            command.add("bash");
            command.add("-lc");
            command.add(buildCommand(testClass, method, seed, invocation, outDir, seedFile, targetProbes));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(jqfRepoPath));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    trace.add("RAW:" + line);
                    String probeId = probeCatalog.parseProbeIdFromOutput(line);
                    if (!probeId.isEmpty()) {
                        trace.add("PROBE:" + probeId);
                        if (targetProbes.contains(probeId)) {
                            reached = true;
                        }
                    }
                    String symptom = probeCatalog.parseSymptomPayloadFromOutput(line);
                    if (!symptom.isEmpty()) {
                        trace.add("SYMPTOM:" + symptom);
                    }
                }
            }

            int exit = process.waitFor();
            if (exit != 0) {
                trace.add("jqf-exit=" + exit);
            }
        } catch (IOException e) {
            trace.add("jqf-io-error=" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            trace.add("jqf-interrupted=" + e.getMessage());
        } finally {
            if (outDir != null) {
                try {
                    deleteRecursively(outDir);
                } catch (IOException ignored) {
                }
            }
        }
        return new FuzzExecutionResult(trace, reached, seed);
    }

    private InvocationConfig parseArgs(List<String> args) {
        InvocationConfig config = new InvocationConfig();
        if (args == null) {
            config.selectFuzzArgs = "";
            return config;
        }
        List<String> selectArgs = new ArrayList<String>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("-cp=")) {
                config.classPath = arg.substring("-cp=".length());
                continue;
            }
            if ("-cp".equals(arg) || "--classpath".equals(arg)) {
                if (i + 1 < args.size()) {
                    config.classPath = args.get(i + 1);
                    i++;
                    continue;
                }
            }
            if (arg.startsWith("-D")) {
                config.jvmOptions.add(arg);
                continue;
            }
            if ("-v".equals(arg) || arg.startsWith("-t")) {
                config.passthrough.add(arg);
                if ("-t".equals(arg) && i + 1 < args.size()) {
                    config.passthrough.add(args.get(i + 1));
                    i++;
                }
                continue;
            }
            selectArgs.add(arg);
        }
        config.selectFuzzArgs = String.join(" ", selectArgs);
        return config;
    }

    private String buildCommand(String testClass,
                                String method,
                                ParameterSeed seed,
                                InvocationConfig config,
                                Path outDir,
                                Path seedFile,
                                Set<String> targetProbes) {
        String classPath = config.classPath == null || config.classPath.trim().isEmpty() ? "." : config.classPath;
        String seedInline = seedSerializer.inline(seed);
        List<String> jvmOptions = new ArrayList<String>(config.jvmOptions);
        jvmOptions.add("-Dthemis.seed.values=" + encodeProperty(seedInline));
        jvmOptions.add("-Dthemis.seed.file=" + encodeProperty(seedFile.toString()));
        if (config.selectFuzzArgs != null && !config.selectFuzzArgs.isEmpty()) {
            jvmOptions.add("-Dthemis.selectfuzz.args=" + encodeProperty(config.selectFuzzArgs));
        }
        if (!targetProbes.isEmpty()) {
            jvmOptions.add("-Dthemis.target.probes=" + encodeProperty(String.join(",", targetProbes)));
        }
        jvmOptions.add("-Dthemis.probe.event.prefix=" + ProbeCatalog.PROBE_EVENT_PREFIX);
        jvmOptions.add("-Dthemis.symptom.event.prefix=" + ProbeCatalog.SYMPTOM_EVENT_PREFIX);

        StringBuilder builder = new StringBuilder();
        builder.append("set -euo pipefail;");
        builder.append("if [ ! -x ./bin/jqf-zest ]; then echo 'missing jqf-zest'; exit 127; fi;");
        builder.append("if [ ! -f ./fuzz/target/jqf-fuzz-2.2-SNAPSHOT.jar ] || [ ! -f ./instrument/target/jqf-instrument-2.2-SNAPSHOT.jar ]; then ");
        builder.append("mvn -q -DskipTests package >/dev/null;");
        builder.append("fi;");
        builder.append("JQF_SEED_VALUES=").append(shellQuote(seedInline)).append(" ");
        builder.append("THEMIS_SEED_FILE=").append(shellQuote(seedFile.toString())).append(" ");
        if (config.selectFuzzArgs != null && !config.selectFuzzArgs.isEmpty()) {
            builder.append("THEMIS_SELECTFUZZ_ARGS=").append(shellQuote(config.selectFuzzArgs)).append(" ");
        }
        builder.append("JVM_OPTS=").append(shellQuote(String.join(" ", jvmOptions))).append(" ");
        builder.append("./bin/jqf-zest -c ").append(shellQuote(classPath)).append(" ");
        builder.append(shellQuote(testClass)).append(" ");
        builder.append(shellQuote(method)).append(" ");
        builder.append(shellQuote(outDir.toString()));
        for (String arg : config.passthrough) {
            builder.append(" ").append(arg);
        }
        return builder.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private Set<String> targetProbes(List<String> targets) {
        Set<String> probeIds = new LinkedHashSet<String>();
        if (targets == null) {
            return probeIds;
        }
        for (String target : targets) {
            if (probeCatalog.isProbeId(target)) {
                probeIds.add(target);
            }
        }
        return probeIds;
    }

    private String encodeProperty(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            java.util.stream.Stream<Path> stream = Files.list(path);
            try {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    deleteRecursively(child);
                }
            } finally {
                stream.close();
            }
        }
        Files.deleteIfExists(path);
    }

    private static class InvocationConfig {
        private String classPath;
        private String selectFuzzArgs;
        private final List<String> jvmOptions = new ArrayList<String>();
        private final List<String> passthrough = new ArrayList<String>();
    }
}
