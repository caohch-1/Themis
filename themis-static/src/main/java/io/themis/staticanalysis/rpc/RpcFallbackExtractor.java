package io.themis.staticanalysis.rpc;

import io.themis.core.model.RpcPair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RpcFallbackExtractor {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?m)\\b(public|protected)\\s+[A-Za-z0-9_<>,\\[\\]\\s]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("([A-Za-z0-9_.-]*address[A-Za-z0-9_.-]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRAMEWORK_PATTERN = Pattern.compile("(org\\.apache\\.hadoop\\.ipc|io\\.grpc|thrift|netty|protobuf|rpc)", Pattern.CASE_INSENSITIVE);

    public List<RpcPair> extract(String codeRoot, String reason) {
        return extract(codeRoot, reason, Collections.<String>emptyList());
    }

    public List<RpcPair> extract(String codeRoot, String reason, List<String> targetSourceRoots) {
        Path root = Paths.get(codeRoot).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IllegalStateException("RPC fallback failed: missing code root " + root + " reason=" + reason);
        }
        if (targetSourceRoots == null || targetSourceRoots.isEmpty()) {
            throw new IllegalStateException("RPC fallback requires staticConfig.targetSourceRoots to be configured.");
        }
        List<Path> scanRoots = resolveScanRoots(root, targetSourceRoots);
        if (scanRoots.isEmpty()) {
            throw new IllegalStateException("RPC fallback misconfigured: no valid scan roots under " + root + ". Set staticConfig.targetSourceRoots to target-system source roots.");
        }
        List<MethodCandidate> methods = new ArrayList<MethodCandidate>();
        Map<String, List<String>> addressKeysByClass = new LinkedHashMap<String, List<String>>();
        Set<String> packages = new LinkedHashSet<String>();
        String protocol = "unknown";
        try {
            for (Path scanRoot : scanRoots) {
                if (!Files.exists(scanRoot)) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(scanRoot)) {
                    stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .filter(this::isCandidateJavaSource)
                        .forEach(path -> scan(path, methods, addressKeysByClass, packages));
                }
            }
            protocol = inferProtocol(root, scanRoots);
        } catch (IOException e) {
            throw new IllegalStateException("RPC fallback failed while scanning roots: " + e.getMessage(), e);
        }
        List<MethodCandidate> clientCandidates = new ArrayList<MethodCandidate>();
        List<MethodCandidate> serverCandidates = new ArrayList<MethodCandidate>();
        for (MethodCandidate method : methods) {
            if (method.clientScore > 0) {
                clientCandidates.add(method);
            }
            if (method.serverScore > 0) {
                serverCandidates.add(method);
            }
        }
        List<MethodCandidate> filteredClients = restrictToTargetPackages(clientCandidates, packages);
        List<MethodCandidate> filteredServers = restrictToTargetPackages(serverCandidates, packages);
        if (filteredClients.isEmpty() || filteredServers.isEmpty()) {
            throw new IllegalStateException("RPC fallback found no ranked client/server candidates in scan roots. Set staticConfig.targetSourceRoots to target-system source roots.");
        }
        MethodCandidate client = best(filteredClients, true, null);
        MethodCandidate server = best(filteredServers, false, client);
        if (client == null || server == null) {
            throw new IllegalStateException("RPC fallback found incomplete endpoint candidates.");
        }
        if (!isInTargetPackages(client.fullClassName, packages) || !isInTargetPackages(server.fullClassName, packages)) {
            throw new IllegalStateException("RPC fallback rejected endpoints outside configured target packages.");
        }
        if (client.fullClassName.startsWith("io.themis.") || server.fullClassName.startsWith("io.themis.")) {
            throw new IllegalStateException("RPC fallback rejected framework/self endpoint classes: " + client.fullClassName + " -> " + server.fullClassName);
        }
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("fallback", "true");
        metadata.put("reason", reason);
        metadata.put("method_count", Integer.toString(methods.size()));
        metadata.put("anchored", "true");
        metadata.put("scan_roots", Integer.toString(scanRoots.size()));
        return Collections.singletonList(
            new RpcPair(
                "fallback-rpc-0",
                protocol,
                client.fullClassName,
                client.methodName,
                server.fullClassName,
                server.methodName,
                dedup(addressKeysByClass.get(client.fullClassName)),
                dedup(addressKeysByClass.get(server.fullClassName)),
                metadata));
    }

    private List<Path> resolveScanRoots(Path codeRoot, List<String> targetSourceRoots) {
        Set<Path> roots = new LinkedHashSet<Path>();
        for (String configuredRoot : targetSourceRoots) {
            if (configuredRoot == null || configuredRoot.trim().isEmpty()) {
                continue;
            }
            Path resolved = Paths.get(configuredRoot);
            if (!resolved.isAbsolute()) {
                resolved = codeRoot.resolve(configuredRoot);
            }
            resolved = resolved.normalize();
            if (Files.exists(resolved) && Files.isDirectory(resolved) && !isExcludedRoot(resolved)) {
                roots.add(resolved);
            }
        }
        return new ArrayList<Path>(roots);
    }

    private boolean isExcludedRoot(Path root) {
        String lower = root.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.contains("/third_party/")
            || lower.contains("/output/")
            || lower.contains("/config/")
            || lower.contains("/scripts/")
            || lower.contains("/.m2/")
            || lower.contains("/dcanalyzer/")
            || lower.contains("/themis-core/")
            || lower.contains("/themis-static/")
            || lower.contains("/themis-fuzz/")
            || lower.contains("/themis-cli/")
            || lower.contains("/themis-llm/")
            || lower.contains("/src/test/")
            || lower.endsWith("/target")
            || lower.contains("/target/");
    }

    private boolean isCandidateJavaSource(Path file) {
        String lower = file.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (lower.contains("/src/test/")) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return !name.endsWith("test.java") && !name.contains("itcase");
    }

    private void scan(Path file,
                      List<MethodCandidate> methods,
                      Map<String, List<String>> addressKeysByClass,
                      Set<String> packages) {
        try {
            String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String lower = code.toLowerCase(Locale.ROOT);
            if (!isFrameworkAnchored(file, lower)) {
                return;
            }
            String packageName = packageName(code);
            if (!packageName.isEmpty()) {
                packages.add(packageName);
            }
            if (packageName.startsWith("io.themis.")) {
                return;
            }
            String className = className(code, file);
            String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
            for (String methodName : topLevelMethodNames(code, className)) {
                int clientScore = score(fullClassName, methodName, true, lower);
                int serverScore = score(fullClassName, methodName, false, lower);
                methods.add(new MethodCandidate(fullClassName, methodName, clientScore, serverScore));
            }
            Matcher addressMatcher = ADDRESS_PATTERN.matcher(code);
            while (addressMatcher.find()) {
                String key = addressMatcher.group(1);
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                addressKeysByClass.computeIfAbsent(fullClassName, k -> new ArrayList<String>()).add(key.trim());
            }
        } catch (IOException ignored) {
        }
    }

    private List<MethodCandidate> restrictToTargetPackages(List<MethodCandidate> candidates, Set<String> packages) {
        List<MethodCandidate> kept = new ArrayList<MethodCandidate>();
        for (MethodCandidate candidate : candidates) {
            if (isInTargetPackages(candidate.fullClassName, packages)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private boolean isInTargetPackages(String className, Set<String> packages) {
        if (className == null || className.trim().isEmpty()) {
            return false;
        }
        if (packages == null || packages.isEmpty()) {
            return false;
        }
        for (String pkg : packages) {
            if (pkg == null || pkg.trim().isEmpty()) {
                continue;
            }
            if (className.equals(pkg) || className.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<String> topLevelMethodNames(String code, String className) {
        Set<String> names = new LinkedHashSet<String>();
        String[] lines = code.split("\\n", -1);
        int braceDepth = 0;
        for (String line : lines) {
            if (braceDepth == 1) {
                Matcher matcher = METHOD_PATTERN.matcher(line);
                while (matcher.find()) {
                    String methodName = matcher.group(2);
                    if (methodName.equals(className)) {
                        continue;
                    }
                    if (isControlSignature(methodName, line)) {
                        continue;
                    }
                    names.add(methodName);
                }
            }
            braceDepth = Math.max(0, braceDepth + braceDelta(line));
        }
        return new ArrayList<String>(names);
    }

    private boolean isControlSignature(String methodName, String line) {
        if (methodName == null || line == null) {
            return true;
        }
        String lowerMethod = methodName.toLowerCase(Locale.ROOT);
        if ("if".equals(lowerMethod) || "for".equals(lowerMethod) || "while".equals(lowerMethod)
            || "switch".equals(lowerMethod) || "catch".equals(lowerMethod) || "new".equals(lowerMethod)) {
            return true;
        }
        String trimmed = line.trim();
        return trimmed.startsWith("if ") || trimmed.startsWith("if(")
            || trimmed.startsWith("for ") || trimmed.startsWith("for(")
            || trimmed.startsWith("while ") || trimmed.startsWith("while(")
            || trimmed.startsWith("switch ") || trimmed.startsWith("switch(")
            || trimmed.startsWith("catch ") || trimmed.startsWith("catch(");
    }

    private int braceDelta(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                delta++;
            } else if (c == '}') {
                delta--;
            }
        }
        return delta;
    }

    private boolean isFrameworkAnchored(Path file, String lowerCode) {
        String path = file.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (FRAMEWORK_PATTERN.matcher(lowerCode).find()) {
            return true;
        }
        return path.contains("/rpc/") || path.contains("/ipc/") || path.contains("/service/") || path.contains("/protocol/");
    }

    private String inferProtocol(Path codeRoot, List<Path> scanRoots) throws IOException {
        List<Path> pomCandidates = new ArrayList<Path>();
        pomCandidates.add(codeRoot.resolve("pom.xml"));
        for (Path scanRoot : scanRoots) {
            Path current = scanRoot;
            int depth = 0;
            while (current != null && depth < 6) {
                pomCandidates.add(current.resolve("pom.xml"));
                if (current.equals(codeRoot)) {
                    break;
                }
                current = current.getParent();
                depth++;
            }
        }
        for (Path pom : dedupPaths(pomCandidates)) {
            if (!Files.exists(pom)) {
                continue;
            }
            String text = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (text.contains("grpc")) {
                return "grpc";
            }
            if (text.contains("thrift")) {
                return "thrift";
            }
            if (text.contains("netty")) {
                return "netty";
            }
            if (text.contains("hadoop")) {
                return "hadoopipc";
            }
        }
        return "unknown";
    }

    private List<Path> dedupPaths(List<Path> paths) {
        Set<Path> unique = new LinkedHashSet<Path>();
        for (Path path : paths) {
            if (path != null) {
                unique.add(path.normalize());
            }
        }
        return new ArrayList<Path>(unique);
    }

    private MethodCandidate best(List<MethodCandidate> methods, boolean client, MethodCandidate avoid) {
        MethodCandidate best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MethodCandidate method : methods) {
            if (avoid != null && method.fullClassName.equals(avoid.fullClassName) && method.methodName.equals(avoid.methodName)) {
                continue;
            }
            int score = client ? method.clientScore : method.serverScore;
            if (score > bestScore) {
                bestScore = score;
                best = method;
            }
        }
        return best;
    }

    private List<String> dedup(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        Set<String> set = new LinkedHashSet<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                set.add(value.trim());
            }
        }
        return new ArrayList<String>(set);
    }

    private String packageName(String code) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String className(String code, Path file) {
        Matcher matcher = CLASS_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) {
            return name.substring(0, name.length() - 5);
        }
        return "UnknownClass";
    }

    private int score(String className, String methodName, boolean client, String lowerCode) {
        String lowerClass = className.toLowerCase(Locale.ROOT);
        String lowerMethod = methodName.toLowerCase(Locale.ROOT);
        int score = FRAMEWORK_PATTERN.matcher(lowerCode).find() ? 4 : 0;
        if (client) {
            if ("main".equals(lowerMethod)) {
                score += 8;
            }
            if (containsAny(lowerMethod, "request", "invoke", "call", "submit", "send", "start", "connect")) {
                score += 8;
            }
            if (containsAny(lowerClass, "client", "caller", "proxy", "stub")) {
                score += 8;
            }
        } else {
            if (containsAny(lowerMethod, "handle", "process", "service", "receive", "listen", "dispatch", "gettask")) {
                score += 10;
            }
            if (containsAny(lowerClass, "server", "service", "handler", "listener", "processor", "impl")) {
                score += 8;
            }
        }
        if (containsAny(lowerMethod, "run", "execute")) {
            score += 2;
        }
        return score;
    }

    private boolean containsAny(String value, String... keys) {
        for (String key : keys) {
            if (value.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private List<RpcPair> defaultPair(String reason) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("fallback", "true");
        metadata.put("reason", reason);
        RpcPair pair = new RpcPair(
            "fallback-rpc-0",
            "unknown",
            "UnknownClient",
            "main",
            "UnknownServer",
            "handle",
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            metadata);
        return Collections.singletonList(pair);
    }

    private static class MethodCandidate {
        private final String fullClassName;
        private final String methodName;
        private final int clientScore;
        private final int serverScore;

        private MethodCandidate(String fullClassName, String methodName, int clientScore, int serverScore) {
            this.fullClassName = fullClassName;
            this.methodName = methodName;
            this.clientScore = clientScore;
            this.serverScore = serverScore;
        }
    }
}
