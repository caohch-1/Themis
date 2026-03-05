package io.themis.fuzz.seed;

import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SeedPoolManager {
    private final Map<String, ParameterSeed> pool = new LinkedHashMap<>();

    public void add(ParameterSeed seed) {
        if (seed == null) {
            return;
        }
        pool.put(seed.getId(), seed);
    }

    public void addAll(List<ParameterSeed> seeds) {
        for (ParameterSeed seed : seeds) {
            add(seed);
        }
    }

    public List<ParameterSeed> top(int count) {
        List<ParameterSeed> seeds = new ArrayList<>(pool.values());
        seeds.sort(Comparator.comparingDouble(ParameterSeed::getScore).reversed());
        if (seeds.size() <= count) {
            return seeds;
        }
        return new ArrayList<>(seeds.subList(0, count));
    }

    public List<ParameterSeed> all() {
        return new ArrayList<>(pool.values());
    }

    public void replace(List<ParameterSeed> seeds) {
        pool.clear();
        addAll(seeds);
    }

    public int size() {
        return pool.size();
    }

    public List<ParameterSeed> randomBatch(int count) {
        List<ParameterSeed> seeds = new ArrayList<>(pool.values());
        Collections.shuffle(seeds);
        if (seeds.size() <= count) {
            return seeds;
        }
        return new ArrayList<>(seeds.subList(0, count));
    }
}
