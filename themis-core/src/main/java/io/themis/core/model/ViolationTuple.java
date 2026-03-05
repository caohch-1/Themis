package io.themis.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ViolationTuple {
    private final String id;
    private final String variableId;
    private final List<String> enclosingFunctions;
    private final List<AccessSite> accessSites;
    private final ViolationType type;
    private final String ruleId;
    private final String rpcPairId;
    private final SystemSide side;
    private final PublicInterfacePair publicInterfacePair;
    private final List<InterleavingShape> interleavingShapes;
    private final List<SymptomCandidate> symptomCandidates;
    private final boolean synchronizedPruned;
    private final Map<String, String> metadata;

    public ViolationTuple(String id,
                          String variableId,
                          List<String> enclosingFunctions,
                          List<AccessSite> accessSites,
                          ViolationType type,
                          String ruleId,
                          String rpcPairId,
                          SystemSide side,
                          PublicInterfacePair publicInterfacePair,
                          List<InterleavingShape> interleavingShapes,
                          List<SymptomCandidate> symptomCandidates,
                          boolean synchronizedPruned,
                          Map<String, String> metadata) {
        this.id = id;
        this.variableId = variableId;
        this.enclosingFunctions = enclosingFunctions == null ? new ArrayList<>() : new ArrayList<>(enclosingFunctions);
        this.accessSites = accessSites == null ? new ArrayList<>() : new ArrayList<>(accessSites);
        this.type = type;
        this.ruleId = ruleId;
        this.rpcPairId = rpcPairId;
        this.side = side;
        this.publicInterfacePair = publicInterfacePair;
        this.interleavingShapes = interleavingShapes == null ? new ArrayList<>() : new ArrayList<>(interleavingShapes);
        this.symptomCandidates = symptomCandidates == null ? new ArrayList<>() : new ArrayList<>(symptomCandidates);
        this.synchronizedPruned = synchronizedPruned;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    public String getVariableId() {
        return variableId;
    }

    public List<String> getEnclosingFunctions() {
        return new ArrayList<>(enclosingFunctions);
    }

    public List<AccessSite> getAccessSites() {
        return new ArrayList<>(accessSites);
    }

    public ViolationType getType() {
        return type;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRpcPairId() {
        return rpcPairId;
    }

    public SystemSide getSide() {
        return side;
    }

    public PublicInterfacePair getPublicInterfacePair() {
        return publicInterfacePair;
    }

    public List<InterleavingShape> getInterleavingShapes() {
        return new ArrayList<>(interleavingShapes);
    }

    public List<SymptomCandidate> getSymptomCandidates() {
        return new ArrayList<>(symptomCandidates);
    }

    public boolean isSynchronizedPruned() {
        return synchronizedPruned;
    }

    public Map<String, String> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public ViolationTuple withSymptoms(List<SymptomCandidate> updated) {
        return new ViolationTuple(id, variableId, enclosingFunctions, accessSites, type, ruleId, rpcPairId, side, publicInterfacePair, interleavingShapes, updated, synchronizedPruned, metadata);
    }

    public ViolationTuple withPublicInterfacePair(PublicInterfacePair pair) {
        return new ViolationTuple(id, variableId, enclosingFunctions, accessSites, type, ruleId, rpcPairId, side, pair, interleavingShapes, symptomCandidates, synchronizedPruned, metadata);
    }

    public ViolationTuple withSynchronizedPruned(boolean value) {
        return new ViolationTuple(id, variableId, enclosingFunctions, accessSites, type, ruleId, rpcPairId, side, publicInterfacePair, interleavingShapes, symptomCandidates, value, metadata);
    }

    public boolean isTriggerable() {
        return publicInterfacePair != null && publicInterfacePair.isComplete() && !synchronizedPruned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ViolationTuple)) {
            return false;
        }
        ViolationTuple that = (ViolationTuple) o;
        return synchronizedPruned == that.synchronizedPruned
            && Objects.equals(id, that.id)
            && Objects.equals(variableId, that.variableId)
            && Objects.equals(enclosingFunctions, that.enclosingFunctions)
            && Objects.equals(accessSites, that.accessSites)
            && type == that.type
            && Objects.equals(ruleId, that.ruleId)
            && Objects.equals(rpcPairId, that.rpcPairId)
            && side == that.side
            && Objects.equals(publicInterfacePair, that.publicInterfacePair)
            && Objects.equals(interleavingShapes, that.interleavingShapes)
            && Objects.equals(symptomCandidates, that.symptomCandidates)
            && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, variableId, enclosingFunctions, accessSites, type, ruleId, rpcPairId, side, publicInterfacePair, interleavingShapes, symptomCandidates, synchronizedPruned, metadata);
    }
}
