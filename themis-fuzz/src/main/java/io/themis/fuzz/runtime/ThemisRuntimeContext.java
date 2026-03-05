package io.themis.fuzz.runtime;

import io.themis.fuzz.engine.SeedSerializer;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThemisRuntimeContext {
    private final SeedSerializer serializer = new SeedSerializer();

    public Map<String, String> seedValues() {
        String inline = decode(System.getProperty("themis.seed.values", System.getenv("JQF_SEED_VALUES") == null ? "" : System.getenv("JQF_SEED_VALUES")));
        Map<String, String> values = new LinkedHashMap<String, String>(serializer.parseInline(inline));
        String envSeedFile = System.getenv("THEMIS_SEED_FILE");
        String filePath = decode(System.getProperty("themis.seed.file", envSeedFile == null ? "" : envSeedFile));
        if (!filePath.isEmpty()) {
            try {
                values.putAll(serializer.parseFile(java.nio.file.Paths.get(filePath)));
            } catch (IOException ignored) {
            }
        }
        return values;
    }

    public List<String> selectFuzzArgs() {
        String envArgs = System.getenv("THEMIS_SELECTFUZZ_ARGS");
        String encoded = System.getProperty("themis.selectfuzz.args", envArgs == null ? "" : envArgs);
        String decoded = decode(encoded);
        List<String> args = new ArrayList<String>();
        if (decoded.trim().isEmpty()) {
            return args;
        }
        for (String part : decoded.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                args.add(part);
            }
        }
        return args;
    }

    public List<String> targetProbes() {
        String encoded = System.getProperty("themis.target.probes", "");
        String decoded = decode(encoded);
        List<String> probes = new ArrayList<String>();
        if (decoded.trim().isEmpty()) {
            return probes;
        }
        for (String part : decoded.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                probes.add(value);
            }
        }
        return probes;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}
