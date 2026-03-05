package io.themis.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PublicInterfacePair {
    private final String leftPublicMethod;
    private final String rightPublicMethod;
    private final List<String> leftPath;
    private final List<String> rightPath;

    public PublicInterfacePair(String leftPublicMethod, String rightPublicMethod, List<String> leftPath, List<String> rightPath) {
        this.leftPublicMethod = leftPublicMethod;
        this.rightPublicMethod = rightPublicMethod;
        this.leftPath = leftPath == null ? new ArrayList<>() : new ArrayList<>(leftPath);
        this.rightPath = rightPath == null ? new ArrayList<>() : new ArrayList<>(rightPath);
    }

    public String getLeftPublicMethod() {
        return leftPublicMethod;
    }

    public String getRightPublicMethod() {
        return rightPublicMethod;
    }

    public List<String> getLeftPath() {
        return new ArrayList<>(leftPath);
    }

    public List<String> getRightPath() {
        return new ArrayList<>(rightPath);
    }

    public boolean isComplete() {
        return leftPublicMethod != null && !leftPublicMethod.isEmpty() && rightPublicMethod != null && !rightPublicMethod.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublicInterfacePair)) {
            return false;
        }
        PublicInterfacePair that = (PublicInterfacePair) o;
        return Objects.equals(leftPublicMethod, that.leftPublicMethod)
            && Objects.equals(rightPublicMethod, that.rightPublicMethod)
            && Objects.equals(leftPath, that.leftPath)
            && Objects.equals(rightPath, that.rightPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftPublicMethod, rightPublicMethod, leftPath, rightPath);
    }
}
