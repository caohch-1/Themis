package io.themis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExecutionPathBundle {
    private final String violationId;
    private final RpcPair rpcPair;
    private final ViolationTuple violationTuple;
    private final List<String> callChain;
    private final List<String> sourceCodeSnippets;
    private final String rpcServerClassCode;
    private final String targetSymptomStatement;

    public ExecutionPathBundle(String violationId,
                               RpcPair rpcPair,
                               ViolationTuple violationTuple,
                               List<String> callChain,
                               List<String> sourceCodeSnippets,
                               String rpcServerClassCode,
                               String targetSymptomStatement) {
        this.violationId = violationId;
        this.rpcPair = rpcPair;
        this.violationTuple = violationTuple;
        this.callChain = callChain == null ? new ArrayList<>() : new ArrayList<>(callChain);
        this.sourceCodeSnippets = sourceCodeSnippets == null ? new ArrayList<>() : new ArrayList<>(sourceCodeSnippets);
        this.rpcServerClassCode = rpcServerClassCode;
        this.targetSymptomStatement = targetSymptomStatement;
    }

    public String getViolationId() {
        return violationId;
    }

    public RpcPair getRpcPair() {
        return rpcPair;
    }

    public ViolationTuple getViolationTuple() {
        return violationTuple;
    }

    public List<String> getCallChain() {
        return new ArrayList<>(callChain);
    }

    public List<String> getSourceCodeSnippets() {
        return new ArrayList<>(sourceCodeSnippets);
    }

    public String getRpcServerClassCode() {
        return rpcServerClassCode;
    }

    public String getTargetSymptomStatement() {
        return targetSymptomStatement;
    }

    public List<String> getPublicApis() {
        List<String> apis = new ArrayList<>();
        if (violationTuple != null && violationTuple.getPublicInterfacePair() != null) {
            apis.add(violationTuple.getPublicInterfacePair().getLeftPublicMethod());
            apis.add(violationTuple.getPublicInterfacePair().getRightPublicMethod());
        }
        return apis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionPathBundle)) {
            return false;
        }
        ExecutionPathBundle that = (ExecutionPathBundle) o;
        return Objects.equals(violationId, that.violationId)
            && Objects.equals(rpcPair, that.rpcPair)
            && Objects.equals(violationTuple, that.violationTuple)
            && Objects.equals(callChain, that.callChain)
            && Objects.equals(sourceCodeSnippets, that.sourceCodeSnippets)
            && Objects.equals(rpcServerClassCode, that.rpcServerClassCode)
            && Objects.equals(targetSymptomStatement, that.targetSymptomStatement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(violationId, rpcPair, violationTuple, callChain, sourceCodeSnippets, rpcServerClassCode, targetSymptomStatement);
    }
}
