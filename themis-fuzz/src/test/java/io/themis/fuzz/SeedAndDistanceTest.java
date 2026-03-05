package io.themis.fuzz;

import io.themis.core.model.ParameterSeed;
import io.themis.fuzz.distance.ApsScheduler;
import io.themis.fuzz.distance.DistanceMetric;
import io.themis.fuzz.seed.RandomSeedSource;
import io.themis.fuzz.seed.SeedPoolManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class SeedAndDistanceTest {
    @Test
    public void randomSeedAndSchedulingWorks() {
        List<ParameterSeed> seeds = new RandomSeedSource().generate("v1", Arrays.asList("a", "b"), 8);
        SeedPoolManager pool = new SeedPoolManager();
        pool.addAll(seeds);
        Assertions.assertEquals(8, pool.size());

        List<Long> distances = Arrays.asList(5L, 3L, 2L, 1L, 7L, 8L, 9L, 10L);
        List<ParameterSeed> selected = new ApsScheduler().scoreAndSelect(seeds, distances, 1000, 60000, 5);
        Assertions.assertEquals(5, selected.size());
    }

    @Test
    public void distanceHandlesMissingTargetsAsInfinite() {
        DistanceMetric metric = new DistanceMetric();
        long d = metric.distance(Arrays.asList("a", "b"), Arrays.asList("x", "y"));
        Assertions.assertEquals(Long.MAX_VALUE, d);
    }
}
