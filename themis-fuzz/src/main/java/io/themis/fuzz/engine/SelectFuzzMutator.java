package io.themis.fuzz.engine;

import io.themis.core.model.ParameterSeed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SelectFuzzMutator {
    public List<ParameterSeed> mutateBatch(List<ParameterSeed> seeds,
                                           int batchSize,
                                           List<String> rawArgs,
                                           List<String> targets) {
        List<ParameterSeed> out = new ArrayList<>();
        MutationContext context = MutationContext.from(rawArgs, targets);
        int idx = 0;
        for (ParameterSeed seed : seeds) {
            if (idx >= batchSize) {
                break;
            }
            Map<String, String> mutatedValues = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : seed.getValues().entrySet()) {
                mutatedValues.put(entry.getKey(), mutate(entry.getKey(), entry.getValue(), idx, context));
            }
            out.add(new ParameterSeed(seed.getId() + "-m" + idx, mutatedValues, seed.getScore(), context.sourceTag()));
            idx++;
        }
        return out;
    }

    private String mutate(String key, String value, int delta, MutationContext context) {
        if (value == null) {
            value = "";
        }
        if ("directed".equals(context.policy) && !context.isTargetedKey(key)) {
            return value;
        }
        int bias = Math.max(1, context.bias + delta + 1);
        try {
            int numeric = Integer.parseInt(value.trim());
            if ("directed".equals(context.policy)) {
                int direction = context.directionFor(key);
                return Integer.toString(numeric + (direction * bias));
            }
            return Integer.toString(numeric + bias);
        } catch (NumberFormatException ignored) {
            if (value == null) {
                return Integer.toString(bias);
            }
            if ("directed".equals(context.policy)) {
                return value + "_" + context.targetHash + "_" + bias;
            }
            return value + "_" + bias;
        }
    }

    private static class MutationContext {
        private final String policy;
        private final int bias;
        private final int targetHash;
        private final Set<String> focusKeys;
        private final Set<String> targetTokens;

        private MutationContext(String policy, int bias, int targetHash, Set<String> focusKeys, Set<String> targetTokens) {
            this.policy = policy;
            this.bias = bias;
            this.targetHash = targetHash;
            this.focusKeys = focusKeys;
            this.targetTokens = targetTokens;
        }

        private static MutationContext from(List<String> rawArgs, List<String> targets) {
            String policy = "directed";
            int bias = 1;
            Set<String> focusKeys = new LinkedHashSet<String>();
            if (rawArgs != null) {
                for (int i = 0; i < rawArgs.size(); i++) {
                    String arg = rawArgs.get(i);
                    if ("--policy".equals(arg) && i + 1 < rawArgs.size()) {
                        policy = rawArgs.get(i + 1);
                        i++;
                    } else if (arg.startsWith("--policy=")) {
                        policy = arg.substring("--policy=".length());
                    } else if ("--energy".equals(arg) && i + 1 < rawArgs.size()) {
                        try {
                            bias = Math.max(1, Integer.parseInt(rawArgs.get(i + 1)));
                        } catch (NumberFormatException ignored) {
                            bias = 1;
                        }
                        i++;
                    } else if ("--focus".equals(arg) && i + 1 < rawArgs.size()) {
                        addFocusKeys(focusKeys, rawArgs.get(i + 1));
                        i++;
                    } else if (arg.startsWith("--focus=")) {
                        addFocusKeys(focusKeys, arg.substring("--focus=".length()));
                    }
                }
            }
            int targetHash = 0;
            Set<String> targetTokens = new LinkedHashSet<String>();
            if (targets != null) {
                for (String target : targets) {
                    targetHash = 31 * targetHash + (target == null ? 0 : target.hashCode());
                    if (target == null) {
                        continue;
                    }
                    String cleaned = target.replaceAll("[^A-Za-z0-9_]", " ").toLowerCase();
                    for (String token : cleaned.split("\\s+")) {
                        if (token.length() >= 3) {
                            targetTokens.add(token);
                        }
                    }
                }
            }
            return new MutationContext(policy, bias, Math.abs(targetHash), focusKeys, targetTokens);
        }

        private String sourceTag() {
            return "selectfuzz:" + policy;
        }

        private static void addFocusKeys(Set<String> focusKeys, String csv) {
            if (csv == null) {
                return;
            }
            for (String token : csv.split(",")) {
                String key = token.trim().toLowerCase();
                if (!key.isEmpty()) {
                    focusKeys.add(key);
                }
            }
        }

        private boolean isTargetedKey(String key) {
            if (key == null || key.trim().isEmpty()) {
                return false;
            }
            String lower = key.toLowerCase();
            if (!focusKeys.isEmpty()) {
                return focusKeys.contains(lower);
            }
            for (String token : targetTokens) {
                if (lower.contains(token)) {
                    return true;
                }
            }
            return true;
        }

        private int directionFor(String key) {
            if (key == null) {
                return 1;
            }
            int hash = Math.abs((key + "#" + targetHash).hashCode());
            return (hash % 2 == 0) ? 1 : -1;
        }
    }
}
