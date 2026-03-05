package io.themis.staticanalysis.access;

import io.themis.core.model.AccessScope;
import io.themis.core.model.AccessSite;
import io.themis.core.model.SharedVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccessSiteSetBuilder {
    public Map<String, List<AccessSite>> build(List<SharedVariable> variables, List<AccessSite> allSites) {
        Map<String, List<AccessSite>> result = new LinkedHashMap<>();
        Map<String, List<AccessSite>> byVar = new HashMap<>();
        for (AccessSite site : allSites) {
            byVar.computeIfAbsent(site.getVariable(), k -> new ArrayList<>()).add(site);
        }
        for (SharedVariable variable : variables) {
            List<AccessSite> matches = new ArrayList<>();
            for (AccessSite site : byVar.getOrDefault(variable.getName(), new ArrayList<AccessSite>())) {
                if (withinScope(variable, site)) {
                    matches.add(site);
                }
            }
            result.put(variable.getId(), matches);
        }
        return result;
    }

    private boolean withinScope(SharedVariable variable, AccessSite site) {
        AccessScope scope = variable.getScope();
        if (scope == AccessScope.STATIC) {
            return true;
        }
        if (scope == AccessScope.IO_OBJECT || scope == AccessScope.NIO_OBJECT) {
            boolean sameClass = variable.getDeclaringClass() != null && variable.getDeclaringClass().equals(site.getClassName());
            boolean sameMethod = variable.getDeclaringMethod() != null && variable.getDeclaringMethod().equals(site.getMethodSignature());
            return sameClass || sameMethod;
        }
        if (scope == AccessScope.INSTANCE) {
            return variable.getDeclaringClass().equals(site.getClassName());
        }
        if (scope == AccessScope.COLLECTION || scope == AccessScope.ARRAY) {
            return variable.getDeclaringClass().equals(site.getClassName()) && methodsCompatible(variable, site);
        }
        if (scope == AccessScope.LOCAL || scope == AccessScope.PARAMETER) {
            return variable.getDeclaringMethod() != null && variable.getDeclaringMethod().equals(site.getMethodSignature());
        }
        return variable.getDeclaringClass().equals(site.getClassName());
    }

    private boolean methodsCompatible(SharedVariable variable, AccessSite site) {
        if (variable.getDeclaringMethod() == null || variable.getDeclaringMethod().isEmpty()) {
            return sameReceiverIdentity(variable, site);
        }
        if (variable.getDeclaringMethod().equals(site.getMethodSignature())) {
            return true;
        }
        return sameReceiverIdentity(variable, site);
    }

    private boolean sameReceiverIdentity(SharedVariable variable, AccessSite site) {
        if (!variable.getName().equals(site.getVariable())) {
            return false;
        }
        if (variable.getDeclaringClass() == null || !variable.getDeclaringClass().equals(site.getClassName())) {
            return false;
        }
        String raw = site.getAttributes().get("rawUnit");
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (variable.getName().startsWith("param:")) {
            return false;
        }
        if (variable.getName().contains(".")) {
            return raw.contains(variable.getName().substring(variable.getName().indexOf('.') + 1));
        }
        return raw.contains(variable.getName());
    }
}
