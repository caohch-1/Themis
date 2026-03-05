package io.themis.fuzz.instrumentation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ProbeCatalog {
    public static final String PROBE_ID_PREFIX = "themis-probe-";
    public static final String PROBE_EVENT_PREFIX = "THEMIS_PROBE:";
    public static final String SYMPTOM_EVENT_PREFIX = "THEMIS_SYMPTOM:";

    public String probeIdForTarget(String target) {
        String normalized = target == null ? "" : target.trim();
        return PROBE_ID_PREFIX + sha256(normalized).substring(0, 16);
    }

    public boolean isProbeId(String value) {
        return value != null && value.startsWith(PROBE_ID_PREFIX);
    }

    public String parseProbeIdFromOutput(String line) {
        if (line == null) {
            return "";
        }
        int idx = line.indexOf(PROBE_EVENT_PREFIX);
        if (idx < 0) {
            return "";
        }
        int start = idx + PROBE_EVENT_PREFIX.length();
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (Character.isWhitespace(c) || c == ',' || c == ';' || c == ']') {
                break;
            }
            end++;
        }
        if (end <= start) {
            return "";
        }
        String value = line.substring(start, end).trim();
        return isProbeId(value) ? value : "";
    }

    public String parseSymptomPayloadFromOutput(String line) {
        if (line == null) {
            return "";
        }
        int idx = line.indexOf(SYMPTOM_EVENT_PREFIX);
        if (idx < 0) {
            return "";
        }
        return line.substring(idx + SYMPTOM_EVENT_PREFIX.length()).trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
