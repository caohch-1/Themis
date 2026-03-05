package io.themis.fuzz.interleaving;

import io.themis.fuzz.instrumentation.ProbeCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class SignalWaitController {
    private final Map<String, CountDownLatch> before = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> after = new ConcurrentHashMap<>();
    private final ProbeCatalog probeCatalog = new ProbeCatalog();

    public void registerPoint(String pointId) {
        before.put(pointId, new CountDownLatch(1));
        after.put(pointId, new CountDownLatch(1));
    }

    public void awaitBefore(String pointId) throws InterruptedException {
        CountDownLatch latch = before.get(pointId);
        if (latch != null) {
            latch.await();
        }
    }

    public void awaitAfter(String pointId) throws InterruptedException {
        CountDownLatch latch = after.get(pointId);
        if (latch != null) {
            latch.await();
        }
    }

    public void signalBefore(String pointId) {
        CountDownLatch latch = before.get(pointId);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void signalAfter(String pointId) {
        CountDownLatch latch = after.get(pointId);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void reset() {
        before.clear();
        after.clear();
    }

    public Map<String, String> runtimeProperties(InterleavingPlan plan) {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        reset();
        for (String point : plan.getSequence()) {
            registerPoint(point);
        }
        properties.put("themis.interleaving.enabled", "true");
        properties.put("themis.interleaving.plan", String.join(",", plan.getSequence()));
        properties.put("themis.interleaving.sync", "signal_wait");
        return properties;
    }

    public boolean reachedPlan(List<String> trace, InterleavingPlan plan) {
        int index = 0;
        for (String event : trace) {
            String probeId = probe(event);
            if (probeId.isEmpty()) {
                continue;
            }
            if (index < plan.getSequence().size() && probeId.equals(plan.getSequence().get(index))) {
                index++;
            }
        }
        return index == plan.getSequence().size();
    }

    private String probe(String event) {
        if (event == null) {
            return "";
        }
        if (event.startsWith("PROBE:")) {
            String id = event.substring("PROBE:".length()).trim();
            return probeCatalog.isProbeId(id) ? id : "";
        }
        if (probeCatalog.isProbeId(event)) {
            return event;
        }
        return probeCatalog.parseProbeIdFromOutput(event);
    }
}
