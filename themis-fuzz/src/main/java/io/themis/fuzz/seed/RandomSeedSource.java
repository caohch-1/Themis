package io.themis.fuzz.seed;

import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomSeedSource {
    public List<ParameterSeed> generate(String violationId, List<String> parameterNames, int count) {
        List<ParameterSeed> seeds = new ArrayList<>();
        Random random = new Random(violationId.hashCode());
        for (int i = 0; i < count; i++) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String param : parameterNames) {
                values.put(param, Integer.toString(random.nextInt(1024)));
            }
            seeds.add(new ParameterSeed(violationId + "-rand-" + i, values, 0.0, "random"));
        }
        return seeds;
    }
}
