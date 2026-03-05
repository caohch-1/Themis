package io.themis.fuzz.seed;

import io.themis.core.model.ExecutionPathBundle;
import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstantSeedSource {
    private static final Pattern INT_PATTERN = Pattern.compile("\\b-?\\d+\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");

    public List<ParameterSeed> fromExecutionPath(ExecutionPathBundle bundle, List<String> parameterNames) {
        Set<String> constants = new LinkedHashSet<>();
        for (String code : bundle.getSourceCodeSnippets()) {
            collect(constants, code);
        }
        collect(constants, bundle.getRpcServerClassCode());
        List<ParameterSeed> seeds = new ArrayList<>();
        int i = 0;
        for (String constant : constants) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String param : parameterNames) {
                values.put(param, constant);
            }
            seeds.add(new ParameterSeed(bundle.getViolationId() + "-const-" + i, values, 0.0, "constant"));
            i++;
        }
        return seeds;
    }

    private void collect(Set<String> constants, String text) {
        if (text == null) {
            return;
        }
        Matcher ints = INT_PATTERN.matcher(text);
        while (ints.find()) {
            constants.add(ints.group());
        }
        Matcher strings = STRING_PATTERN.matcher(text);
        while (strings.find()) {
            constants.add(strings.group(1));
        }
    }
}
