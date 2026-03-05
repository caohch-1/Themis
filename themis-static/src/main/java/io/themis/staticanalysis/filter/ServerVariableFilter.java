package io.themis.staticanalysis.filter;

import io.themis.core.model.AccessScope;
import io.themis.core.model.SharedVariable;

import java.util.ArrayList;
import java.util.List;

public class ServerVariableFilter {
    public List<SharedVariable> filter(List<SharedVariable> candidates) {
        List<SharedVariable> selected = new ArrayList<>();
        for (SharedVariable variable : candidates) {
            if (variable.getScope() == AccessScope.INSTANCE
                || variable.getScope() == AccessScope.STATIC
                || variable.getScope() == AccessScope.IO_OBJECT
                || variable.getScope() == AccessScope.NIO_OBJECT) {
                selected.add(variable);
                continue;
            }
            if (variable.getScope() == AccessScope.COLLECTION || variable.getScope() == AccessScope.ARRAY) {
                selected.add(variable);
            }
        }
        return selected;
    }
}
