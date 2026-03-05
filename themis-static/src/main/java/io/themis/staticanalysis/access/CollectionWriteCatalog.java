package io.themis.staticanalysis.access;

import soot.SootClass;
import soot.SootMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CollectionWriteCatalog {
    private final Map<String, Set<String>> typeToWriteMethods = new HashMap<String, Set<String>>();

    public CollectionWriteCatalog() {
        typeToWriteMethods.put("java.util.List", new HashSet<String>(Arrays.asList("add", "addAll", "clear", "remove", "removeAll", "removeIf", "removeRange", "replaceAll", "set", "retainAll", "sort")));
        typeToWriteMethods.put("java.util.Set", new HashSet<String>(Arrays.asList("add", "addAll", "clear", "remove", "removeAll", "removeIf", "retainAll")));
        typeToWriteMethods.put("java.util.Map", new HashSet<String>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));
        typeToWriteMethods.put("java.util.concurrent.ConcurrentMap", new HashSet<String>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));
        typeToWriteMethods.put("java.util.concurrent.ConcurrentHashMap", new HashSet<String>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));
    }

    public boolean isWriteLikeInvocation(SootMethod method) {
        if (method == null) {
            return false;
        }
        String methodName = method.getName();
        SootClass declaringClass = method.getDeclaringClass();
        for (Map.Entry<String, Set<String>> entry : typeToWriteMethods.entrySet()) {
            if (!entry.getValue().contains(methodName)) {
                continue;
            }
            if (matchesType(declaringClass, entry.getKey(), new HashSet<String>())) {
                return true;
            }
        }
        return false;
    }

    public boolean isCollectionType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String lower = typeName.toLowerCase(Locale.ROOT);
        return lower.contains("collection") || lower.contains("list") || lower.contains("map") || lower.contains("set") || lower.contains("queue");
    }

    private boolean matchesType(SootClass sootClass, String targetType, Set<String> visited) {
        if (sootClass == null) {
            return false;
        }
        String name = sootClass.getName();
        if (visited.contains(name)) {
            return false;
        }
        visited.add(name);
        if (name.equals(targetType)) {
            return true;
        }
        if (sootClass.implementsInterface(targetType)) {
            return true;
        }
        for (SootClass iface : sootClass.getInterfaces()) {
            if (matchesType(iface, targetType, visited)) {
                return true;
            }
        }
        if (sootClass.hasSuperclass()) {
            return matchesType(sootClass.getSuperclass(), targetType, visited);
        }
        return false;
    }
}
