package io.themis.core.config;

import java.util.ArrayList;
import java.util.List;

public class ThemisConfig {
    private String systemName;
    private String codeRoot;
    private String classPath;
    private String outputDir;
    private StaticConfig staticConfig = new StaticConfig();
    private LlmConfig llmConfig = new LlmConfig();
    private FuzzConfig fuzzConfig = new FuzzConfig();

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getCodeRoot() {
        return codeRoot;
    }

    public void setCodeRoot(String codeRoot) {
        this.codeRoot = codeRoot;
    }

    public String getClassPath() {
        return classPath;
    }

    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public StaticConfig getStaticConfig() {
        return staticConfig;
    }

    public void setStaticConfig(StaticConfig staticConfig) {
        this.staticConfig = staticConfig;
    }

    public LlmConfig getLlmConfig() {
        return llmConfig;
    }

    public void setLlmConfig(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    public FuzzConfig getFuzzConfig() {
        return fuzzConfig;
    }

    public void setFuzzConfig(FuzzConfig fuzzConfig) {
        this.fuzzConfig = fuzzConfig;
    }

    public static class StaticConfig {
        private String rpcBridgeCommand = "rpcbridge";
        private int propagationHopBound = 3;
        private String threadUnsafeRegistryPath = "config/labels/thread_unsafe_objects.json";
        private List<String> targetSourceRoots = new ArrayList<>();

        public String getRpcBridgeCommand() {
            return rpcBridgeCommand;
        }

        public void setRpcBridgeCommand(String rpcBridgeCommand) {
            this.rpcBridgeCommand = rpcBridgeCommand;
        }

        public int getPropagationHopBound() {
            return propagationHopBound;
        }

        public void setPropagationHopBound(int propagationHopBound) {
            this.propagationHopBound = propagationHopBound;
        }

        public String getThreadUnsafeRegistryPath() {
            return threadUnsafeRegistryPath;
        }

        public void setThreadUnsafeRegistryPath(String threadUnsafeRegistryPath) {
            this.threadUnsafeRegistryPath = threadUnsafeRegistryPath;
        }

        public List<String> getTargetSourceRoots() {
            return targetSourceRoots;
        }

        public void setTargetSourceRoots(List<String> targetSourceRoots) {
            this.targetSourceRoots = targetSourceRoots == null ? new ArrayList<String>() : targetSourceRoots;
        }
    }

    public static class LlmConfig {
        private String model = "claude-4";
        private String apiKeyEnv = "ANTHROPIC_API_KEY";
        private int repairThreshold = 8;
        private int restartCycles = 5;
        private String compileClassPath = "";
        private String runtimeClassPath = "";
        private String buildOracleCommand = "";

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public int getRepairThreshold() {
            return repairThreshold;
        }

        public void setRepairThreshold(int repairThreshold) {
            this.repairThreshold = repairThreshold;
        }

        public int getRestartCycles() {
            return restartCycles;
        }

        public void setRestartCycles(int restartCycles) {
            this.restartCycles = restartCycles;
        }

        public String getCompileClassPath() {
            return compileClassPath;
        }

        public void setCompileClassPath(String compileClassPath) {
            this.compileClassPath = compileClassPath;
        }

        public String getRuntimeClassPath() {
            return runtimeClassPath;
        }

        public void setRuntimeClassPath(String runtimeClassPath) {
            this.runtimeClassPath = runtimeClassPath;
        }

        public String getBuildOracleCommand() {
            return buildOracleCommand;
        }

        public void setBuildOracleCommand(String buildOracleCommand) {
            this.buildOracleCommand = buildOracleCommand;
        }
    }

    public static class FuzzConfig {
        private int stageTimeBudgetMinutes = 30;
        private int mutationBatchSize = 5;
        private int instrumentationHopLimit = 7;
        private List<String> jqfArgs = new ArrayList<>();
        private List<String> selectFuzzArgs = new ArrayList<>();

        public int getStageTimeBudgetMinutes() {
            return stageTimeBudgetMinutes;
        }

        public void setStageTimeBudgetMinutes(int stageTimeBudgetMinutes) {
            this.stageTimeBudgetMinutes = stageTimeBudgetMinutes;
        }

        public int getMutationBatchSize() {
            return mutationBatchSize;
        }

        public void setMutationBatchSize(int mutationBatchSize) {
            this.mutationBatchSize = mutationBatchSize;
        }

        public int getInstrumentationHopLimit() {
            return instrumentationHopLimit;
        }

        public void setInstrumentationHopLimit(int instrumentationHopLimit) {
            this.instrumentationHopLimit = instrumentationHopLimit;
        }

        public List<String> getJqfArgs() {
            return jqfArgs;
        }

        public void setJqfArgs(List<String> jqfArgs) {
            this.jqfArgs = jqfArgs;
        }

        public List<String> getSelectFuzzArgs() {
            return selectFuzzArgs;
        }

        public void setSelectFuzzArgs(List<String> selectFuzzArgs) {
            this.selectFuzzArgs = selectFuzzArgs;
        }
    }
}
