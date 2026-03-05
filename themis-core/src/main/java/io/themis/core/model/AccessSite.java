package io.themis.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AccessSite {
    private final String id;
    private final String className;
    private final String methodSignature;
    private final String statement;
    private final int lineNumber;
    private final String variable;
    private final AccessScope scope;
    private final AccessOperation operation;
    private final SystemSide side;
    private final boolean reachableFromRpc;
    private final int publicCallerDepth;
    private final Map<String, String> attributes;

    public AccessSite(String id,
                      String className,
                      String methodSignature,
                      String statement,
                      int lineNumber,
                      String variable,
                      AccessScope scope,
                      AccessOperation operation,
                      SystemSide side,
                      boolean reachableFromRpc,
                      int publicCallerDepth,
                      Map<String, String> attributes) {
        this.id = id;
        this.className = className;
        this.methodSignature = methodSignature;
        this.statement = statement;
        this.lineNumber = lineNumber;
        this.variable = variable;
        this.scope = scope;
        this.operation = operation;
        this.side = side;
        this.reachableFromRpc = reachableFromRpc;
        this.publicCallerDepth = publicCallerDepth;
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getStatement() {
        return statement;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getVariable() {
        return variable;
    }

    public AccessScope getScope() {
        return scope;
    }

    public AccessOperation getOperation() {
        return operation;
    }

    public SystemSide getSide() {
        return side;
    }

    public boolean isReachableFromRpc() {
        return reachableFromRpc;
    }

    public int getPublicCallerDepth() {
        return publicCallerDepth;
    }

    public Map<String, String> getAttributes() {
        return new LinkedHashMap<>(attributes);
    }

    public boolean isWriteLike() {
        return operation == AccessOperation.WRITE || operation == AccessOperation.READ_WRITE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessSite)) {
            return false;
        }
        AccessSite accessSite = (AccessSite) o;
        return lineNumber == accessSite.lineNumber
            && reachableFromRpc == accessSite.reachableFromRpc
            && publicCallerDepth == accessSite.publicCallerDepth
            && Objects.equals(id, accessSite.id)
            && Objects.equals(className, accessSite.className)
            && Objects.equals(methodSignature, accessSite.methodSignature)
            && Objects.equals(statement, accessSite.statement)
            && Objects.equals(variable, accessSite.variable)
            && scope == accessSite.scope
            && operation == accessSite.operation
            && side == accessSite.side
            && Objects.equals(attributes, accessSite.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, className, methodSignature, statement, lineNumber, variable, scope, operation, side, reachableFromRpc, publicCallerDepth, attributes);
    }
}
