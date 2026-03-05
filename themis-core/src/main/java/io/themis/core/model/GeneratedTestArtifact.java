package io.themis.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GeneratedTestArtifact {
    private final String id;
    private final String violationId;
    private final String testClassName;
    private final String code;
    private final PublicInterfacePair publicInterfacePair;
    private final Map<String, String> primaryParameters;
    private final List<Map<String, String>> candidateParameterSets;
    private final boolean executable;
    private final String source;

    public GeneratedTestArtifact(String id,
                                 String violationId,
                                 String testClassName,
                                 String code,
                                 PublicInterfacePair publicInterfacePair,
                                 Map<String, String> primaryParameters,
                                 List<Map<String, String>> candidateParameterSets,
                                 boolean executable,
                                 String source) {
        this.id = id;
        this.violationId = violationId;
        this.testClassName = testClassName;
        this.code = code;
        this.publicInterfacePair = publicInterfacePair;
        this.primaryParameters = primaryParameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(primaryParameters);
        this.candidateParameterSets = candidateParameterSets == null ? new ArrayList<>() : new ArrayList<>(candidateParameterSets);
        this.executable = executable;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getViolationId() {
        return violationId;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getCode() {
        return code;
    }

    public PublicInterfacePair getPublicInterfacePair() {
        return publicInterfacePair;
    }

    public Map<String, String> getPrimaryParameters() {
        return new LinkedHashMap<>(primaryParameters);
    }

    public List<Map<String, String>> getCandidateParameterSets() {
        return new ArrayList<>(candidateParameterSets);
    }

    public boolean isExecutable() {
        return executable;
    }

    public String getSource() {
        return source;
    }

    public List<ParameterSeed> toSeeds() {
        List<ParameterSeed> seeds = new ArrayList<>();
        int idx = 0;
        for (Map<String, String> params : candidateParameterSets) {
            seeds.add(new ParameterSeed(violationId + "-llm-" + idx, params, 0.0, "llm"));
            idx++;
        }
        if (seeds.isEmpty() && !primaryParameters.isEmpty()) {
            seeds.add(new ParameterSeed(violationId + "-llm-primary", primaryParameters, 0.0, "llm"));
        }
        return seeds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeneratedTestArtifact)) {
            return false;
        }
        GeneratedTestArtifact that = (GeneratedTestArtifact) o;
        return executable == that.executable
            && Objects.equals(id, that.id)
            && Objects.equals(violationId, that.violationId)
            && Objects.equals(testClassName, that.testClassName)
            && Objects.equals(code, that.code)
            && Objects.equals(publicInterfacePair, that.publicInterfacePair)
            && Objects.equals(primaryParameters, that.primaryParameters)
            && Objects.equals(candidateParameterSets, that.candidateParameterSets)
            && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, violationId, testClassName, code, publicInterfacePair, primaryParameters, candidateParameterSets, executable, source);
    }
}
