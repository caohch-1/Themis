package io.themis.fuzz.runtime;

import io.themis.core.model.SymptomPattern;
import io.themis.fuzz.instrumentation.ProbeCatalog;

import java.util.ArrayList;
import java.util.List;

public class InterleavingRuntimeHooks {
    private static final Object LOCK = new Object();
    private static final ProbeCatalog CATALOG = new ProbeCatalog();
    private static final List<String> PLAN = new ArrayList<String>();
    private static int INDEX = 0;
    private static boolean ENABLED = false;
    private static long WAIT_TIMEOUT_MS = 1000L;

    static {
        configureFromSystemProperties();
    }

    public static void configureFromSystemProperties() {
        synchronized (LOCK) {
            PLAN.clear();
            INDEX = 0;
            String enabled = System.getProperty("themis.interleaving.enabled", "false");
            ENABLED = "true".equalsIgnoreCase(enabled);
            WAIT_TIMEOUT_MS = parseTimeout(System.getProperty("themis.interleaving.wait.timeout.ms", "1000"));
            String encodedPlan = System.getProperty("themis.interleaving.plan", "");
            if (encodedPlan.trim().isEmpty()) {
                return;
            }
            for (String token : encodedPlan.split(",")) {
                String value = token.trim();
                if (!value.isEmpty()) {
                    PLAN.add(value);
                }
            }
        }
    }

    public static void beforeProbe(String probeId) {
        synchronized (LOCK) {
            if (!ENABLED || PLAN.isEmpty()) {
                return;
            }
            int position = PLAN.indexOf(probeId);
            if (position < 0 || position < INDEX) {
                return;
            }
            if (position == INDEX) {
                return;
            }
            long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
            while (INDEX < position) {
                long now = System.currentTimeMillis();
                if (now >= deadline) {
                    System.out.println(ProbeCatalog.SYMPTOM_EVENT_PREFIX + "TO|interleaving_timeout:" + probeId);
                    return;
                }
                try {
                    LOCK.wait(Math.min(10L, deadline - now));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public static void afterProbe(String probeId) {
        synchronized (LOCK) {
            if (!ENABLED || PLAN.isEmpty()) {
                return;
            }
            if (INDEX < PLAN.size() && PLAN.get(INDEX).equals(probeId)) {
                INDEX++;
                LOCK.notifyAll();
            }
        }
    }

    public static String probe(String target) {
        String probeId = CATALOG.probeIdForTarget(target);
        beforeProbe(probeId);
        System.out.println(ProbeCatalog.PROBE_EVENT_PREFIX + probeId);
        afterProbe(probeId);
        return probeId;
    }

    public static String probeId(String probeId) {
        if (probeId == null || probeId.trim().isEmpty()) {
            return "";
        }
        beforeProbe(probeId);
        System.out.println(ProbeCatalog.PROBE_EVENT_PREFIX + probeId);
        afterProbe(probeId);
        return probeId;
    }

    public static void symptom(SymptomPattern pattern, String statement) {
        String payload = pattern.name() + "|" + statement;
        System.out.println(ProbeCatalog.SYMPTOM_EVENT_PREFIX + payload);
    }

    private static long parseTimeout(String raw) {
        try {
            long value = Long.parseLong(raw);
            return Math.max(1L, value);
        } catch (NumberFormatException e) {
            return 1000L;
        }
    }
}
