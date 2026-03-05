package io.themis.staticanalysis;

import io.themis.core.model.AccessOperation;
import io.themis.core.model.AccessScope;
import io.themis.core.model.AccessSite;
import io.themis.core.model.SharedVariable;
import io.themis.core.model.SystemSide;
import io.themis.core.model.ViolationTuple;
import io.themis.staticanalysis.rules.ViolationRuleEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class ViolationRuleEngineTest {
    @Test
    public void detectsRule1OrderViolation() {
        SharedVariable variable = new SharedVariable(
            "v1",
            "map",
            "A",
            "<A:m1>",
            AccessScope.COLLECTION,
            SystemSide.SERVER,
            "collection",
            Collections.<String>emptyList(),
            Collections.<String>emptyList());
        AccessSite s1 = new AccessSite("s1", "A", "<A:m1>", "map.get(k)", 10, "map", AccessScope.COLLECTION, AccessOperation.READ, SystemSide.SERVER, true, 0, new LinkedHashMap<String, String>());
        AccessSite s2 = new AccessSite("s2", "A", "<A:m2>", "map.remove(k)", 12, "map", AccessScope.COLLECTION, AccessOperation.WRITE, SystemSide.SERVER, true, 0, new LinkedHashMap<String, String>());
        List<ViolationTuple> tuples = new ViolationRuleEngine().detect(variable, Arrays.asList(s1, s2), "rpc1");
        Assertions.assertFalse(tuples.isEmpty());
    }

    @Test
    public void detectsAtomicityPattern() {
        SharedVariable variable = new SharedVariable(
            "v2",
            "obj",
            "A",
            "<A:m1>",
            AccessScope.INSTANCE,
            SystemSide.CLIENT,
            "object",
            Collections.<String>emptyList(),
            Collections.<String>emptyList());
        AccessSite s1 = new AccessSite("s1", "A", "<A:m1>", "obj.read", 20, "obj", AccessScope.INSTANCE, AccessOperation.READ, SystemSide.CLIENT, true, 0, new LinkedHashMap<String, String>());
        AccessSite s2 = new AccessSite("s2", "A", "<A:m1>", "obj.write", 21, "obj", AccessScope.INSTANCE, AccessOperation.WRITE, SystemSide.CLIENT, true, 0, new LinkedHashMap<String, String>());
        AccessSite s3 = new AccessSite("s3", "A", "<A:m2>", "obj.write2", 22, "obj", AccessScope.INSTANCE, AccessOperation.WRITE, SystemSide.CLIENT, true, 0, new LinkedHashMap<String, String>());
        List<ViolationTuple> tuples = new ViolationRuleEngine().detect(variable, Arrays.asList(s1, s2, s3), "rpc1");
        Assertions.assertTrue(tuples.stream().anyMatch(t -> t.getType().name().equals("ATOMICITY")));
    }
}
