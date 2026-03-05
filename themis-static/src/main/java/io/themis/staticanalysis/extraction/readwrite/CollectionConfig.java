package indi.dc.extraction.readwrite;

import java.util.*;

public class CollectionConfig {
    public static Map<String, Set<String>> type2writeFunc = new HashMap<String, Set<String>>() {{
        put("java.util.List", new HashSet<>(Arrays.asList("add", "addAll", "clear", "remove", "removeAll", "removeIf", "removeRange", "replaceAll", "set", "retainAll", "sort")));
        put("java.util.Set", new HashSet<>(Arrays.asList("add", "addAll", "clear", "remove", "removeAll", "removeIf", "retainAll")));
        put("java.util.Map", new HashSet<>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));
        put("java.util.concurrent.ConcurrentMap", new HashSet<>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));
        put("java.util.concurrent.ConcurrentHashMap", new HashSet<>(Arrays.asList("clear", "put", "putAll", "putIfAbsent", "remove", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge")));

    }};
}
