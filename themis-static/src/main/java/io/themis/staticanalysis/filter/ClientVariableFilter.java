package io.themis.staticanalysis.filter;

import io.themis.core.model.SharedVariable;

import java.util.ArrayList;
import java.util.List;

public class ClientVariableFilter {
    public List<SharedVariable> filter(List<SharedVariable> candidates) {
        return new ArrayList<>(candidates);
    }
}
