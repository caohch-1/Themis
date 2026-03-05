package io.themis.fuzz.stage;

import io.themis.core.model.ViolationTuple;
import io.themis.fuzz.interleaving.InterleavingPlan;

import java.util.Map;

public interface InterleavingRuntimeExecutor {
    InterleavingRuntimeResult execute(ViolationTuple tuple,
                                      InterleavingPlan plan,
                                      Map<String, String> runtimeProperties);
}
