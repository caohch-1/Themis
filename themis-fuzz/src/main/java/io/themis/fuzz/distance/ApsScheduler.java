package io.themis.fuzz.distance;

import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ApsScheduler {
    public List<ParameterSeed> scoreAndSelect(List<ParameterSeed> seeds,
                                              List<Long> distances,
                                              long elapsedMillis,
                                              int budgetMillis,
                                              int batchSize) {
        List<ParameterSeed> scored = new ArrayList<>();
        double exploreWeight = Math.max(0.1, 1.0 - ((double) elapsedMillis / Math.max(1, budgetMillis)));
        double exploitWeight = 1.0 - exploreWeight;
        for (int i = 0; i < seeds.size(); i++) {
            ParameterSeed seed = seeds.get(i);
            long distance = distances.get(i);
            double distanceScore = distance == Long.MAX_VALUE ? 0.0 : 1.0 / (1.0 + distance);
            double noveltyScore = 1.0 / (1.0 + i);
            double finalScore = noveltyScore * exploreWeight + distanceScore * exploitWeight;
            scored.add(seed.withScore(finalScore));
        }
        scored.sort(Comparator.comparingDouble(ParameterSeed::getScore).reversed());
        if (scored.size() <= batchSize) {
            return scored;
        }
        return new ArrayList<>(scored.subList(0, batchSize));
    }
}
