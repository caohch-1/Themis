package io.themis.staticanalysis.symptom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThreadUnsafeRegistry {
    private final Map<String, Set<String>> operations;

    public ThreadUnsafeRegistry() {
        this(resolveDefaultRegistryPath());
    }

    public ThreadUnsafeRegistry(String registryPath) {
        this(registryPath == null || registryPath.trim().isEmpty() ? null : Paths.get(registryPath));
    }

    public ThreadUnsafeRegistry(Path registryPath) {
        this.operations = loadRegistry(registryPath);
    }

    public boolean isThreadUnsafeOp(String type, String methodName) {
        for (Map.Entry<String, Set<String>> entry : operations.entrySet()) {
            if (!type.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue().contains(methodName)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Set<String>> loadRegistry(Path preferredPath) {
        Path path = preferredPath;
        if (path == null || !Files.exists(path)) {
            path = resolveDefaultRegistryPath();
        }
        Map<String, Set<String>> loaded = new LinkedHashMap<String, Set<String>>();
        if (path == null || !Files.exists(path)) {
            return loaded;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<String>> raw = mapper.readValue(Files.newInputStream(path), new TypeReference<Map<String, List<String>>>() {
            });
            for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                loaded.put(entry.getKey(), new HashSet<String>(entry.getValue()));
            }
        } catch (IOException ignored) {
            return new HashMap<String, Set<String>>();
        }
        return loaded;
    }

    private static Path resolveDefaultRegistryPath() {
        Path local = Paths.get("config/labels/thread_unsafe_objects.json");
        if (Files.exists(local)) {
            return local;
        }
        Path parent = Paths.get("../config/labels/thread_unsafe_objects.json");
        if (Files.exists(parent)) {
            return parent;
        }
        return local;
    }
}
