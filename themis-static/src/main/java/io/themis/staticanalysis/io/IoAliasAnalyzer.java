package io.themis.staticanalysis.io;

import io.themis.core.model.AccessSite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IoAliasAnalyzer {
    private final StringPathApproximator approximator;

    public IoAliasAnalyzer(StringPathApproximator approximator) {
        this.approximator = approximator;
    }

    public Map<String, List<AccessSite>> groupByApproximateResource(List<AccessSite> ioSites) {
        Map<String, List<AccessSite>> grouped = new LinkedHashMap<>();
        Map<String, String> canonical = new HashMap<>();
        for (AccessSite site : ioSites) {
            String text = site.getStatement();
            List<String> paths = approximator.approximatePaths(text);
            if (paths.isEmpty()) {
                paths.add("unknown");
            }
            for (String path : paths) {
                String key = canonical.computeIfAbsent(path, this::canonicalize);
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(site);
            }
        }
        return grouped;
    }

    private String canonicalize(String path) {
        String normalized = path.trim().replace("\\", "/");
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }
}
