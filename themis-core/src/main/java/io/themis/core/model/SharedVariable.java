package io.themis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SharedVariable {
    private final String id;
    private final String name;
    private final String declaringClass;
    private final String declaringMethod;
    private final AccessScope scope;
    private final SystemSide side;
    private final String type;
    private final List<String> ioPathCandidates;
    private final List<String> accessSiteIds;

    public SharedVariable(String id,
                          String name,
                          String declaringClass,
                          String declaringMethod,
                          AccessScope scope,
                          SystemSide side,
                          String type,
                          List<String> ioPathCandidates,
                          List<String> accessSiteIds) {
        this.id = id;
        this.name = name;
        this.declaringClass = declaringClass;
        this.declaringMethod = declaringMethod;
        this.scope = scope;
        this.side = side;
        this.type = type;
        this.ioPathCandidates = ioPathCandidates == null ? new ArrayList<>() : new ArrayList<>(ioPathCandidates);
        this.accessSiteIds = accessSiteIds == null ? new ArrayList<>() : new ArrayList<>(accessSiteIds);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getDeclaringMethod() {
        return declaringMethod;
    }

    public AccessScope getScope() {
        return scope;
    }

    public SystemSide getSide() {
        return side;
    }

    public String getType() {
        return type;
    }

    public List<String> getIoPathCandidates() {
        return new ArrayList<>(ioPathCandidates);
    }

    public List<String> getAccessSiteIds() {
        return new ArrayList<>(accessSiteIds);
    }

    public SharedVariable withAccessSite(String accessSiteId) {
        List<String> updated = new ArrayList<>(accessSiteIds);
        updated.add(accessSiteId);
        return new SharedVariable(id, name, declaringClass, declaringMethod, scope, side, type, ioPathCandidates, updated);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SharedVariable)) {
            return false;
        }
        SharedVariable that = (SharedVariable) o;
        return Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && Objects.equals(declaringClass, that.declaringClass)
            && Objects.equals(declaringMethod, that.declaringMethod)
            && scope == that.scope
            && side == that.side
            && Objects.equals(type, that.type)
            && Objects.equals(ioPathCandidates, that.ioPathCandidates)
            && Objects.equals(accessSiteIds, that.accessSiteIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, declaringClass, declaringMethod, scope, side, type, ioPathCandidates, accessSiteIds);
    }
}
