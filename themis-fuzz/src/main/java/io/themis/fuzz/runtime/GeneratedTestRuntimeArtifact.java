package io.themis.fuzz.runtime;

import java.nio.file.Path;

public class GeneratedTestRuntimeArtifact {
    private final boolean compiled;
    private final String qualifiedClassName;
    private final Path classesDir;
    private final String instrumentedCode;
    private final String error;

    public GeneratedTestRuntimeArtifact(boolean compiled,
                                        String qualifiedClassName,
                                        Path classesDir,
                                        String instrumentedCode,
                                        String error) {
        this.compiled = compiled;
        this.qualifiedClassName = qualifiedClassName;
        this.classesDir = classesDir;
        this.instrumentedCode = instrumentedCode;
        this.error = error;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public String getQualifiedClassName() {
        return qualifiedClassName;
    }

    public Path getClassesDir() {
        return classesDir;
    }

    public String getInstrumentedCode() {
        return instrumentedCode;
    }

    public String getError() {
        return error;
    }
}
