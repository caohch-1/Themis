package io.themis.fuzz.seed;

import io.themis.core.model.GeneratedTestArtifact;
import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.List;

public class LlmSeedSource {
    public List<ParameterSeed> fromArtifact(GeneratedTestArtifact artifact) {
        if (artifact == null) {
            return new ArrayList<>();
        }
        return artifact.toSeeds();
    }
}
