package io.themis.staticanalysis.rpc;

import io.themis.core.model.RpcPair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RpcBridgeCommandClient implements RpcBridgeClient {
    private final String command;
    private final RpcFallbackExtractor fallbackExtractor = new RpcFallbackExtractor();
    private final List<String> targetSourceRoots;

    public RpcBridgeCommandClient(String command) {
        this(command, Collections.<String>emptyList());
    }

    public RpcBridgeCommandClient(String command, List<String> targetSourceRoots) {
        this.command = command;
        this.targetSourceRoots = targetSourceRoots == null ? Collections.<String>emptyList() : new ArrayList<String>(targetSourceRoots);
    }

    @Override
    public List<RpcPair> extractRpcPairs(String codeRoot) throws IOException, InterruptedException {
        if (!commandAvailable(command)) {
            return fallbackExtractor.extract(codeRoot, "rpcbridge_not_found:" + command, targetSourceRoots);
        }
        ProcessBuilder pb = new ProcessBuilder(command, "--root", codeRoot, "--format", "pipe");
        try {
            Process process = pb.start();
            List<RpcPair> result = new ArrayList<RpcPair>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    RpcPair pair = parse(line);
                    if (pair != null) {
                        result.add(pair);
                    }
                }
            }
            int exit = process.waitFor();
            if (exit != 0 || result.isEmpty()) {
                return fallbackExtractor.extract(codeRoot, "rpcbridge_failed_exit_" + exit, targetSourceRoots);
            }
            return result;
        } catch (IOException e) {
            return fallbackExtractor.extract(codeRoot, "rpcbridge_io_error:" + e.getMessage(), targetSourceRoots);
        }
    }

    private RpcPair parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 7) {
            return null;
        }
        List<String> clientKeys = parts[6].isEmpty() ? Collections.<String>emptyList() : Arrays.asList(parts[6].split(","));
        List<String> serverKeys = parts.length >= 8 && !parts[7].isEmpty() ? Arrays.asList(parts[7].split(",")) : Collections.<String>emptyList();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("raw", line);
        return new RpcPair(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], clientKeys, serverKeys, metadata);
    }

    private boolean commandAvailable(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return false;
        }
        String trimmed = rawCommand.trim();
        if (trimmed.contains("/") && Files.isExecutable(Paths.get(trimmed))) {
            return true;
        }
        try {
            Process process = new ProcessBuilder("bash", "-lc", "command -v " + shellEscape(trimmed) + " >/dev/null 2>&1").start();
            int exit = process.waitFor();
            return exit == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
