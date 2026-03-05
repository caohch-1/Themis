package io.themis.fuzz.stage;

import io.themis.core.model.FuzzOutcome;

public class StageThreeResult {
    private final boolean exposed;
    private final FuzzOutcome outcome;

    public StageThreeResult(boolean exposed, FuzzOutcome outcome) {
        this.exposed = exposed;
        this.outcome = outcome;
    }

    public boolean isExposed() {
        return exposed;
    }

    public FuzzOutcome getOutcome() {
        return outcome;
    }
}
