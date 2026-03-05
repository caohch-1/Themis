package io.themis.staticanalysis;

import io.themis.core.model.AccessOperation;
import io.themis.core.model.AccessScope;
import io.themis.core.model.AccessSite;
import io.themis.core.model.PublicInterfacePair;
import io.themis.core.model.SystemSide;
import io.themis.core.model.ViolationTuple;
import io.themis.core.model.ViolationType;
import io.themis.staticanalysis.symptom.ImplicitPatternEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

public class ImplicitPatternEngineTest {
    @Test
    public void matchesMrAndNp() {
        AccessSite site = new AccessSite("s1", "A", "<A:m>", "x = map.remove(k)", 1, "map", AccessScope.COLLECTION, AccessOperation.READ_WRITE, SystemSide.SERVER, true, 0, new LinkedHashMap<String, String>());
        ViolationTuple tuple = new ViolationTuple(
            "v",
            "map",
            Collections.singletonList("<A:m>"),
            Collections.singletonList(site),
            ViolationType.ATOMICITY,
            "R2",
            "rpc",
            SystemSide.SERVER,
            new PublicInterfacePair("p", "q", Arrays.asList("p"), Arrays.asList("q")),
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            new LinkedHashMap<String, String>());
        Assertions.assertFalse(new ImplicitPatternEngine().match(tuple).isEmpty());
    }
}
