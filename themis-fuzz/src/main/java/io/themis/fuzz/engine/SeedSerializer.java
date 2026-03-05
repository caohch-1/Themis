package io.themis.fuzz.engine;

import io.themis.core.model.ParameterSeed;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class SeedSerializer {
    public Path writeSeed(Path outDir, ParameterSeed seed) throws IOException {
        Path file = outDir.resolve("themis-seed.properties");
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : seed.getValues().entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        Files.write(file, builder.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    public String inline(ParameterSeed seed) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : seed.getValues().entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    public Map<String, String> parseInline(String encoded) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return values;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            int idx = entry.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = decode(entry.substring(0, idx));
            String value = decode(entry.substring(idx + 1));
            values.put(key, value);
        }
        return values;
    }

    public Map<String, String> parseFile(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (file == null || !Files.exists(file)) {
            return values;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = decode(line.substring(0, idx));
            String value = decode(line.substring(idx + 1));
            values.put(key, value);
        }
        return values;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
