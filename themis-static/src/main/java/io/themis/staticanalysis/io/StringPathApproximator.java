package io.themis.staticanalysis.io;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringPathApproximator {
    private static final Pattern PATH_PATTERN = Pattern.compile("([A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+)");

    public List<String> approximatePaths(String expression) {
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = PATH_PATTERN.matcher(expression == null ? "" : expression);
        while (matcher.find()) {
            paths.add(normalize(matcher.group(1)));
        }
        if (paths.isEmpty() && expression != null && !expression.isEmpty()) {
            paths.add(normalize(expression));
        }
        return new ArrayList<>(paths);
    }

    private String normalize(String path) {
        return path.replace('"', ' ').trim().replace("//", "/");
    }
}
