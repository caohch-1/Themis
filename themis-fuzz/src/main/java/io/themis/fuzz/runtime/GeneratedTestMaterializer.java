package io.themis.fuzz.runtime;

import io.themis.core.model.AccessSite;
import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.GeneratedTestArtifact;
import io.themis.fuzz.instrumentation.ProbeCatalog;
import io.themis.fuzz.instrumentation.SymptomConditionInstrumenter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneratedTestMaterializer {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern CLASS_OPEN_PATTERN = Pattern.compile("\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*[^\\{]*\\{");
    private static final Pattern DECL_PATTERN = Pattern.compile("^([ \\t]*)(final\\s+)?(int|long|double|float|boolean|String)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+);\\s*$");
    private static final Pattern MAIN_PATTERN = Pattern.compile(".*\\bmain\\s*\\(\\s*String\\s*\\[\\]\\s+args\\s*\\).*");
    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile("^[ \\t]*(public|protected|private|static|final|native|synchronized|abstract|strictfp|\\s)+[A-Za-z0-9_<>,\\[\\]\\s]+\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\([^;]*\\)\\s*(throws\\s+[A-Za-z0-9_.,\\s]+)?\\s*\\{?[ \\t]*$");
    private final ProbeCatalog probeCatalog = new ProbeCatalog();
    private final SymptomConditionInstrumenter symptomConditionInstrumenter = new SymptomConditionInstrumenter();

    public GeneratedTestRuntimeArtifact materialize(GeneratedTestArtifact artifact,
                                                    ExecutionPathBundle bundle,
                                                    String baseClassPath,
                                                    String codeRoot) {
        if (artifact == null || artifact.getCode() == null || artifact.getCode().trim().isEmpty()) {
            return new GeneratedTestRuntimeArtifact(false, "", null, "", "empty generated test code");
        }
        try {
            String instrumentedTestCode = instrumentTestSource(artifact.getCode(), bundle);
            String packageName = packageName(instrumentedTestCode);
            String className = className(instrumentedTestCode, artifact.getTestClassName());
            String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            Path root = Paths.get(System.getProperty("java.io.tmpdir"), "themis-generated-tests", artifact.getViolationId(), Long.toString(System.nanoTime()));
            Path srcDir = root.resolve("src");
            Path appSrcDir = root.resolve("appsrc");
            Path classesDir = root.resolve("classes");
            Path javaFile = sourcePath(srcDir, packageName, className);
            Files.createDirectories(javaFile.getParent());
            Files.createDirectories(classesDir);
            Files.createDirectories(appSrcDir);
            Files.write(javaFile, instrumentedTestCode.getBytes(StandardCharsets.UTF_8));

            String compileClassPath = mergeClassPath(baseClassPath, System.getProperty("java.class.path", ""));
            CommandResult testCompile = compile(javaFile, classesDir, compileClassPath);
            if (testCompile.exitCode != 0) {
                return new GeneratedTestRuntimeArtifact(false, qualifiedName, classesDir, instrumentedTestCode, testCompile.output.isEmpty() ? "javac failed" : testCompile.output);
            }

            List<String> symptomTargets = symptomTargets(bundle);
            String instrumentationErrors = compileTargetClasses(
                bundle,
                symptomTargets,
                codeRoot,
                appSrcDir,
                classesDir,
                mergeClassPath(compileClassPath, classesDir.toString()));
            if (!instrumentationErrors.isEmpty()) {
                return new GeneratedTestRuntimeArtifact(false, qualifiedName, classesDir, instrumentedTestCode, instrumentationErrors);
            }
            return new GeneratedTestRuntimeArtifact(true, qualifiedName, classesDir, instrumentedTestCode, "");
        } catch (IOException e) {
            return new GeneratedTestRuntimeArtifact(false, "", null, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GeneratedTestRuntimeArtifact(false, "", null, "", "interrupted");
        }
    }

    public String mergeClassPath(String... values) {
        Set<String> entries = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String[] parts = value.split(Pattern.quote(java.io.File.pathSeparator));
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    entries.add(trimmed);
                }
            }
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private String instrumentTestSource(String code, ExecutionPathBundle bundle) {
        String updated = ensureImports(code, true);
        updated = injectRuntimeMembers(updated);
        updated = rewriteSeedBindings(updated);
        updated = injectMainArgsBinding(updated);
        updated = injectTargetProbesInTest(updated, bundle);
        return updated;
    }

    private String ensureImports(String code, boolean withRuntimeContext) {
        String updated = code;
        String importHooks = "import io.themis.fuzz.runtime.InterleavingRuntimeHooks;";
        if (!updated.contains(importHooks)) {
            updated = insertImport(updated, importHooks);
        }
        if (withRuntimeContext) {
            String importRuntime = "import io.themis.fuzz.runtime.ThemisRuntimeContext;";
            if (!updated.contains(importRuntime)) {
                updated = insertImport(updated, importRuntime);
            }
        }
        return updated;
    }

    private String insertImport(String code, String importLine) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        if (matcher.find()) {
            int insert = matcher.end();
            return code.substring(0, insert) + "\n" + importLine + code.substring(insert);
        }
        return importLine + "\n" + code;
    }

    private String injectRuntimeMembers(String code) {
        if (code.contains("THEMIS_RUNTIME_CONTEXT") && code.contains("themisSeedRaw(")) {
            return code;
        }
        String members = runtimeMembers();
        Matcher matcher = CLASS_OPEN_PATTERN.matcher(code);
        if (matcher.find()) {
            int insert = matcher.end();
            return code.substring(0, insert) + members + code.substring(insert);
        }
        return code + members;
    }

    private String runtimeMembers() {
        StringBuilder builder = new StringBuilder();
        builder.append("\nprivate static final ThemisRuntimeContext THEMIS_RUNTIME_CONTEXT = new ThemisRuntimeContext();\n");
        builder.append("private static final java.util.Map<String, String> THEMIS_SEED_VALUES = THEMIS_RUNTIME_CONTEXT.seedValues();\n");
        builder.append("private static final java.util.List<String> THEMIS_SELECTFUZZ_ARGS = THEMIS_RUNTIME_CONTEXT.selectFuzzArgs();\n");
        builder.append("static {\n");
        builder.append("InterleavingRuntimeHooks.configureFromSystemProperties();\n");
        builder.append("}\n");
        builder.append("private static String themisSeedRaw(String key, String fallback) {\n");
        builder.append("String value = THEMIS_SEED_VALUES.get(key);\n");
        builder.append("if (value == null || value.trim().isEmpty()) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("return value;\n");
        builder.append("}\n");
        builder.append("private static int themisInt(String key, int fallback) {\n");
        builder.append("try {\n");
        builder.append("return Integer.parseInt(themisSeedRaw(key, Integer.toString(fallback)));\n");
        builder.append("} catch (Exception e) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("private static long themisLong(String key, long fallback) {\n");
        builder.append("try {\n");
        builder.append("return Long.parseLong(themisSeedRaw(key, Long.toString(fallback)));\n");
        builder.append("} catch (Exception e) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("private static double themisDouble(String key, double fallback) {\n");
        builder.append("try {\n");
        builder.append("return Double.parseDouble(themisSeedRaw(key, Double.toString(fallback)));\n");
        builder.append("} catch (Exception e) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("private static boolean themisBoolean(String key, boolean fallback) {\n");
        builder.append("String value = themisSeedRaw(key, Boolean.toString(fallback));\n");
        builder.append("if (\"true\".equalsIgnoreCase(value) || \"false\".equalsIgnoreCase(value)) {\n");
        builder.append("return Boolean.parseBoolean(value);\n");
        builder.append("}\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("private static String themisString(String key, String fallback) {\n");
        builder.append("return themisSeedRaw(key, fallback);\n");
        builder.append("}\n");
        builder.append("private static boolean themisDirected() {\n");
        builder.append("for (int i = 0; i < THEMIS_SELECTFUZZ_ARGS.size(); i++) {\n");
        builder.append("String arg = THEMIS_SELECTFUZZ_ARGS.get(i);\n");
        builder.append("if (\"--policy\".equals(arg) && i + 1 < THEMIS_SELECTFUZZ_ARGS.size()) {\n");
        builder.append("return \"directed\".equalsIgnoreCase(THEMIS_SELECTFUZZ_ARGS.get(i + 1));\n");
        builder.append("}\n");
        builder.append("if (arg.startsWith(\"--policy=\")) {\n");
        builder.append("return \"directed\".equalsIgnoreCase(arg.substring(\"--policy=\".length()));\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("return false;\n");
        builder.append("}\n");
        builder.append("private static int themisEnergy(int fallback) {\n");
        builder.append("for (int i = 0; i < THEMIS_SELECTFUZZ_ARGS.size(); i++) {\n");
        builder.append("String arg = THEMIS_SELECTFUZZ_ARGS.get(i);\n");
        builder.append("if (\"--energy\".equals(arg) && i + 1 < THEMIS_SELECTFUZZ_ARGS.size()) {\n");
        builder.append("try {\n");
        builder.append("return Math.max(1, Integer.parseInt(THEMIS_SELECTFUZZ_ARGS.get(i + 1)));\n");
        builder.append("} catch (Exception e) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("private static int themisScaleInt(String key, int fallback) {\n");
        builder.append("int value = themisInt(key, fallback);\n");
        builder.append("if (!themisDirected()) {\n");
        builder.append("return value;\n");
        builder.append("}\n");
        builder.append("int energy = Math.max(1, themisEnergy(1));\n");
        builder.append("long scaled = (long) value * (long) energy;\n");
        builder.append("if (scaled > Integer.MAX_VALUE) {\n");
        builder.append("return Integer.MAX_VALUE;\n");
        builder.append("}\n");
        builder.append("if (scaled < Integer.MIN_VALUE) {\n");
        builder.append("return Integer.MIN_VALUE;\n");
        builder.append("}\n");
        builder.append("return (int) scaled;\n");
        builder.append("}\n");
        builder.append("private static long themisScaleLong(String key, long fallback) {\n");
        builder.append("long value = themisLong(key, fallback);\n");
        builder.append("if (!themisDirected()) {\n");
        builder.append("return value;\n");
        builder.append("}\n");
        builder.append("long energy = Math.max(1, themisEnergy(1));\n");
        builder.append("if (value > 0 && energy > Long.MAX_VALUE / value) {\n");
        builder.append("return Long.MAX_VALUE;\n");
        builder.append("}\n");
        builder.append("if (value < 0 && energy > Long.MIN_VALUE / value) {\n");
        builder.append("return Long.MIN_VALUE;\n");
        builder.append("}\n");
        builder.append("return value * energy;\n");
        builder.append("}\n");
        builder.append("private static String[] themisArgs(String[] fallback) {\n");
        builder.append("if (fallback != null && fallback.length > 0) {\n");
        builder.append("return fallback;\n");
        builder.append("}\n");
        builder.append("java.util.List<java.util.Map.Entry<String, String>> entries = new java.util.ArrayList<java.util.Map.Entry<String, String>>();\n");
        builder.append("for (java.util.Map.Entry<String, String> entry : THEMIS_SEED_VALUES.entrySet()) {\n");
        builder.append("String key = entry.getKey();\n");
        builder.append("if (key != null && key.matches(\"arg[0-9]+\")) {\n");
        builder.append("entries.add(entry);\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("entries.sort(new java.util.Comparator<java.util.Map.Entry<String, String>>() {\n");
        builder.append("public int compare(java.util.Map.Entry<String, String> a, java.util.Map.Entry<String, String> b) {\n");
        builder.append("int ai = Integer.parseInt(a.getKey().substring(3));\n");
        builder.append("int bi = Integer.parseInt(b.getKey().substring(3));\n");
        builder.append("return Integer.compare(ai, bi);\n");
        builder.append("}\n");
        builder.append("});\n");
        builder.append("if (entries.isEmpty()) {\n");
        builder.append("return fallback == null ? new String[0] : fallback;\n");
        builder.append("}\n");
        builder.append("String[] values = new String[entries.size()];\n");
        builder.append("for (int i = 0; i < entries.size(); i++) {\n");
        builder.append("values[i] = entries.get(i).getValue();\n");
        builder.append("}\n");
        builder.append("return values;\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String rewriteSeedBindings(String code) {
        String[] lines = code.split("\\n", -1);
        List<String> out = new ArrayList<String>(lines.length + 8);
        for (String line : lines) {
            String rewritten = rewriteSeedBindingLine(line);
            out.add(rewritten);
        }
        return String.join("\n", out);
    }

    private String rewriteSeedBindingLine(String line) {
        if (line.contains("THEMIS_") || line.contains("themisSeedRaw(") || line.contains("InterleavingRuntimeHooks")) {
            return line;
        }
        Matcher matcher = DECL_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return line;
        }
        String indent = matcher.group(1);
        String finals = matcher.group(2) == null ? "" : matcher.group(2);
        String type = matcher.group(3);
        String var = matcher.group(4);
        String expr = matcher.group(5).trim();
        if (var.toLowerCase(Locale.ROOT).startsWith("themis")) {
            return line;
        }
        String rhs = bindExpression(type, var, expr);
        return indent + finals + type + " " + var + " = " + rhs + ";";
    }

    private String bindExpression(String type, String var, String expr) {
        String lowerType = type.toLowerCase(Locale.ROOT);
        if ("int".equals(lowerType)) {
            String bound = "themisInt(\"" + escape(var) + "\", " + expr + ")";
            if (isScaleCandidate(var)) {
                return "themisScaleInt(\"" + escape(var) + "\", " + bound + ")";
            }
            return bound;
        }
        if ("long".equals(lowerType)) {
            String bound = "themisLong(\"" + escape(var) + "\", " + expr + ")";
            if (isScaleCandidate(var)) {
                return "themisScaleLong(\"" + escape(var) + "\", " + bound + ")";
            }
            return bound;
        }
        if ("double".equals(lowerType)) {
            return "themisDouble(\"" + escape(var) + "\", " + expr + ")";
        }
        if ("float".equals(lowerType)) {
            return "(float) themisDouble(\"" + escape(var) + "\", " + expr + ")";
        }
        if ("boolean".equals(lowerType)) {
            return "themisBoolean(\"" + escape(var) + "\", " + expr + ")";
        }
        if ("string".equals(lowerType)) {
            return "themisString(\"" + escape(var) + "\", " + expr + ")";
        }
        return expr;
    }

    private boolean isScaleCandidate(String var) {
        String lower = var.toLowerCase(Locale.ROOT);
        return lower.contains("count") || lower.contains("iter") || lower.contains("thread") || lower.contains("size") || lower.contains("limit") || lower.contains("num");
    }

    private String injectMainArgsBinding(String code) {
        String[] lines = code.split("\\n", -1);
        List<String> out = new ArrayList<String>(lines.length + 4);
        boolean injected = false;
        for (String line : lines) {
            out.add(line);
            if (!injected && MAIN_PATTERN.matcher(line).matches()) {
                out.add(indentOf(line) + "    args = themisArgs(args);");
                injected = true;
            }
        }
        return String.join("\n", out);
    }

    private String injectTargetProbesInTest(String code, ExecutionPathBundle bundle) {
        Map<String, AccessSite> byId = new LinkedHashMap<String, AccessSite>();
        for (AccessSite site : bundle.getViolationTuple().getAccessSites()) {
            byId.put(site.getId(), site);
        }
        String[] lines = code.split("\\n", -1);
        List<String> out = new ArrayList<String>(lines.length + 16);
        int braceDepth = 0;
        for (String line : lines) {
            List<String> probeIds = matchingProbeIds(line, new ArrayList<AccessSite>(byId.values()), false);
            if (isMethodBodyLine(braceDepth, line) && !probeIds.isEmpty()) {
                String indent = indentOf(line);
                for (String probeId : probeIds) {
                    if (!line.contains(probeId)) {
                        out.add(indent + "InterleavingRuntimeHooks.probeId(\"" + probeId + "\");");
                    }
                }
            }
            out.add(line);
            braceDepth = updatedBraceDepth(braceDepth, line);
        }
        return String.join("\n", out);
    }

    private String compileTargetClasses(ExecutionPathBundle bundle,
                                        List<String> symptomTargets,
                                        String codeRoot,
                                        Path appSrcDir,
                                        Path classesDir,
                                        String classPath) throws IOException, InterruptedException {
        if (codeRoot == null || codeRoot.trim().isEmpty()) {
            return "";
        }
        Map<String, List<AccessSite>> byClass = new LinkedHashMap<String, List<AccessSite>>();
        for (AccessSite site : bundle.getViolationTuple().getAccessSites()) {
            byClass.computeIfAbsent(site.getClassName(), k -> new ArrayList<AccessSite>()).add(site);
        }
        Set<String> optionalClasses = enclosingFunctionClasses(bundle);
        for (String className : optionalClasses) {
            byClass.putIfAbsent(className, new ArrayList<AccessSite>());
        }
        StringBuilder errors = new StringBuilder();
        for (Map.Entry<String, List<AccessSite>> entry : byClass.entrySet()) {
            Path source = resolveSourcePath(codeRoot, entry.getKey());
            if (source == null || !Files.exists(source)) {
                if (!entry.getValue().isEmpty()) {
                    if (errors.length() > 0) {
                        errors.append('\n');
                    }
                    errors.append("missing_source:").append(entry.getKey());
                }
                continue;
            }
            String raw = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            String instrumented = instrumentApplicationSource(raw, entry.getValue(), symptomTargets);
            String packageName = packageName(instrumented);
            String className = className(instrumented, source.getFileName().toString().replace(".java", ""));
            Path outputSource = sourcePath(appSrcDir, packageName, className);
            Files.createDirectories(outputSource.getParent());
            Files.write(outputSource, instrumented.getBytes(StandardCharsets.UTF_8));
            CommandResult compile = compile(outputSource, classesDir, classPath);
            if (compile.exitCode != 0) {
                if (errors.length() > 0) {
                    errors.append('\n');
                }
                errors.append("compile_failed:").append(entry.getKey()).append(":").append(compile.output);
            }
        }
        return errors.toString();
    }

    private String instrumentApplicationSource(String sourceCode, List<AccessSite> sites, List<String> symptomTargets) {
        String source = ensureImports(sourceCode, false);
        String[] lines = source.split("\\n", -1);
        Map<Integer, List<AccessSite>> byLine = new HashMap<Integer, List<AccessSite>>();
        List<AccessSite> unresolved = new ArrayList<AccessSite>();
        for (AccessSite site : sites) {
            if (site.getLineNumber() > 0) {
                byLine.computeIfAbsent(site.getLineNumber(), k -> new ArrayList<AccessSite>()).add(site);
            } else {
                unresolved.add(site);
            }
        }
        Set<String> unresolvedEmitted = new LinkedHashSet<String>();
        Set<String> symptomEmitted = new LinkedHashSet<String>();
        List<String> out = new ArrayList<String>(lines.length + sites.size() * 2);
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i];
            if (isMethodBodyLine(braceDepth, line)) {
                List<AccessSite> fixed = byLine.get(lineNo);
                if (fixed != null) {
                    for (AccessSite site : fixed) {
                        out.add(indentOf(line) + "InterleavingRuntimeHooks.probeId(\"" + probeCatalog.probeIdForTarget(site.getId()) + "\");");
                    }
                }
                List<String> dynamic = matchingProbeIds(line, unresolved, true);
                for (String probeId : dynamic) {
                    if (unresolvedEmitted.add(probeId)) {
                        out.add(indentOf(line) + "InterleavingRuntimeHooks.probeId(\"" + probeId + "\");");
                    }
                }
                List<String> symptomMatches = matchingSymptomProbeIds(line, symptomTargets);
                for (String probeId : symptomMatches) {
                    if (symptomEmitted.add(probeId)) {
                        out.add(indentOf(line) + "InterleavingRuntimeHooks.probeId(\"" + probeId + "\");");
                    }
                }
            }
            out.add(line);
            braceDepth = updatedBraceDepth(braceDepth, line);
        }
        return String.join("\n", out);
    }

    private List<String> matchingProbeIds(String line, List<AccessSite> sites, boolean allowMethodMatch) {
        List<String> probeIds = new ArrayList<String>();
        if (line == null || line.trim().isEmpty()) {
            return probeIds;
        }
        String normalizedLine = normalize(line);
        for (AccessSite site : sites) {
            String statement = site.getStatement() == null ? "" : site.getStatement();
            String normalizedStatement = normalize(statement);
            boolean statementMatch = !normalizedStatement.isEmpty() && normalizedLine.contains(normalizedStatement);
            boolean methodMatch = false;
            if (allowMethodMatch) {
                String methodName = methodNameOf(site.getMethodSignature());
                if (!methodName.isEmpty() && line.contains(methodName + "(")) {
                    methodMatch = true;
                }
            }
            if (statementMatch || methodMatch || line.contains(site.getId())) {
                String probeId = probeCatalog.probeIdForTarget(site.getId());
                if (!probeIds.contains(probeId)) {
                    probeIds.add(probeId);
                }
            }
        }
        return probeIds;
    }

    private List<String> matchingSymptomProbeIds(String line, List<String> symptomTargets) {
        List<String> probeIds = new ArrayList<String>();
        if (line == null || line.trim().isEmpty() || symptomTargets == null || symptomTargets.isEmpty()) {
            return probeIds;
        }
        String normalizedLine = normalize(line);
        for (String target : symptomTargets) {
            if (target == null || target.trim().isEmpty()) {
                continue;
            }
            String normalizedTarget = normalize(target);
            if (normalizedTarget.isEmpty()) {
                continue;
            }
            boolean matched = normalizedLine.contains(normalizedTarget);
            if (!matched && normalizedTarget.startsWith("if(") && normalizedTarget.endsWith(")")) {
                String inner = normalizedTarget.substring(3, normalizedTarget.length() - 1);
                matched = !inner.isEmpty() && normalizedLine.contains(inner);
            }
            if (matched) {
                String probeId = probeCatalog.probeIdForTarget(target);
                if (!probeIds.contains(probeId)) {
                    probeIds.add(probeId);
                }
            }
        }
        return probeIds;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String methodNameOf(String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            return "";
        }
        int paren = signature.indexOf('(');
        if (paren < 0) {
            return "";
        }
        int colon = signature.indexOf(':');
        if (colon >= 0 && colon < paren) {
            String between = signature.substring(colon + 1, paren).trim();
            String[] tokens = between.split("\\s+");
            if (tokens.length > 0) {
                return tokens[tokens.length - 1];
            }
        }
        int dot = signature.lastIndexOf('.', paren);
        if (dot >= 0) {
            return signature.substring(dot + 1, paren).trim();
        }
        return "";
    }

    private Set<String> enclosingFunctionClasses(ExecutionPathBundle bundle) {
        Set<String> classes = new LinkedHashSet<String>();
        bundle.getViolationTuple().getEnclosingFunctions().forEach(function -> {
            String className = classNameOfFunction(function);
            if (!className.isEmpty()) {
                classes.add(className);
            }
        });
        return classes;
    }

    private String classNameOfFunction(String function) {
        if (function == null || function.trim().isEmpty()) {
            return "";
        }
        String trimmed = function.trim();
        if (trimmed.startsWith("<")) {
            int colon = trimmed.indexOf(':');
            if (colon > 1) {
                return trimmed.substring(1, colon).trim();
            }
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String left = trimmed.substring(0, colon).trim();
            if (left.contains(".")) {
                return left;
            }
        }
        int paren = trimmed.indexOf('(');
        int dot = trimmed.lastIndexOf('.', paren >= 0 ? paren : trimmed.length());
        if (dot > 0) {
            return trimmed.substring(0, dot).trim();
        }
        return "";
    }

    private List<String> symptomTargets(ExecutionPathBundle bundle) {
        Set<String> targets = new LinkedHashSet<String>();
        targets.addAll(symptomConditionInstrumenter.synthesizeTargets(bundle.getViolationTuple().getSymptomCandidates()));
        bundle.getViolationTuple().getSymptomCandidates().forEach(candidate -> {
            String statement = candidate.getStatement();
            if (statement != null && !statement.trim().isEmpty()) {
                targets.add(statement.trim());
            }
        });
        return new ArrayList<String>(targets);
    }

    private boolean isExecutableLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
            return false;
        }
        if (trimmed.startsWith("class ") || trimmed.startsWith("public class") || trimmed.startsWith("interface ") || trimmed.startsWith("enum ")) {
            return false;
        }
        if ("{".equals(trimmed) || "}".equals(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("@")) {
            return false;
        }
        if (isMethodDeclarationLine(trimmed)) {
            return false;
        }
        return true;
    }

    private boolean isMethodBodyLine(int braceDepth, String line) {
        return braceDepth >= 2 && isExecutableLine(line);
    }

    private int updatedBraceDepth(int current, String line) {
        int updated = current + braceDelta(line);
        return Math.max(updated, 0);
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

    private boolean isMethodDeclarationLine(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty()) {
            return false;
        }
        if (trimmedLine.startsWith("if ") || trimmedLine.startsWith("if(")
            || trimmedLine.startsWith("for ") || trimmedLine.startsWith("for(")
            || trimmedLine.startsWith("while ") || trimmedLine.startsWith("while(")
            || trimmedLine.startsWith("switch ") || trimmedLine.startsWith("switch(")
            || trimmedLine.startsWith("catch ") || trimmedLine.startsWith("catch(")
            || trimmedLine.startsWith("return ") || trimmedLine.startsWith("new ")) {
            return false;
        }
        if (!trimmedLine.contains("(") || !trimmedLine.contains(")")) {
            return false;
        }
        if (trimmedLine.endsWith(";")) {
            return false;
        }
        return METHOD_DECL_PATTERN.matcher(trimmedLine).matches();
    }

    private String indentOf(String line) {
        int idx = 0;
        while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
            idx++;
        }
        return line.substring(0, idx);
    }

    private Path resolveSourcePath(String codeRoot, String fullClassName) throws IOException {
        Path root = Paths.get(codeRoot);
        String normalizedClassName = outerClassName(fullClassName);
        String relative = normalizedClassName.replace('.', java.io.File.separatorChar) + ".java";
        List<Path> candidates = new ArrayList<Path>();
        candidates.add(root.resolve(relative));
        candidates.add(root.resolve("src/main/java").resolve(relative));
        candidates.add(root.resolve("src/test/java").resolve(relative));
        if (!normalizedClassName.equals(fullClassName)) {
            String nestedRelative = fullClassName.replace('.', java.io.File.separatorChar) + ".java";
            candidates.add(root.resolve(nestedRelative));
            candidates.add(root.resolve("src/main/java").resolve(nestedRelative));
            candidates.add(root.resolve("src/test/java").resolve(nestedRelative));
        }
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        String simpleName = simpleJavaFileName(normalizedClassName);
        String nestedSimpleName = simpleJavaFileName(fullClassName);
        String expectedPackage = "";
        int dot = normalizedClassName.lastIndexOf('.');
        if (dot > 0) {
            expectedPackage = normalizedClassName.substring(0, dot);
        }
        List<Path> matches = new ArrayList<Path>();
        Files.walk(root).filter(p -> Files.isRegularFile(p) && (
            p.getFileName().toString().equals(simpleName) || p.getFileName().toString().equals(nestedSimpleName)
        )).forEach(matches::add);
        matches.sort(Comparator.comparingInt(p -> p.toString().length()));
        for (Path match : matches) {
            String code = new String(Files.readAllBytes(match), StandardCharsets.UTF_8);
            if (expectedPackage.isEmpty() || expectedPackage.equals(packageName(code))) {
                return match;
            }
        }
        return null;
    }

    private String outerClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return "";
        }
        int nestedIndex = fullClassName.indexOf('$');
        if (nestedIndex < 0) {
            return fullClassName;
        }
        return fullClassName.substring(0, nestedIndex);
    }

    private String simpleJavaFileName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return "Unknown.java";
        }
        String outer = outerClassName(fullClassName);
        int dot = outer.lastIndexOf('.');
        String simple = dot >= 0 ? outer.substring(dot + 1) : outer;
        return simple + ".java";
    }

    private CommandResult compile(Path sourceFile, Path outputDir, String classPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add(javacCommand());
        if (classPath != null && !classPath.trim().isEmpty()) {
            command.add("-cp");
            command.add(classPath);
        }
        command.add("-d");
        command.add(outputDir.toString());
        command.add(sourceFile.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = readAll(process);
        int exit = process.waitFor();
        return new CommandResult(exit, output);
    }

    private String packageName(String code) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String className(String code, String fallback) {
        Matcher matcher = CLASS_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            String name = fallback.trim();
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        }
        return "GeneratedThemisTest";
    }

    private Path sourcePath(Path srcDir, String packageName, String className) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return srcDir.resolve(className + ".java");
        }
        String[] segments = packageName.split("\\.");
        Path path = srcDir;
        for (String segment : segments) {
            path = path.resolve(segment);
        }
        return path.resolve(className + ".java");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String readAll(Process process) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String javacCommand() {
        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isEmpty()) {
            Path candidate = Paths.get(javaHome, "bin", "javac");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
            Path parent = Paths.get(javaHome).getParent();
            if (parent != null) {
                Path sibling = parent.resolve("bin").resolve("javac");
                if (Files.exists(sibling)) {
                    return sibling.toString();
                }
            }
        }
        return "javac";
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}
