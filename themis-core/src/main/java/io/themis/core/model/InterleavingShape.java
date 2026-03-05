package io.themis.core.model;

public enum InterleavingShape {
    RWR,
    WWR,
    WRW,
    RWW,
    ORDER_FORWARD,
    ORDER_REVERSE,
    ATOMICITY_BEFORE,
    ATOMICITY_MIDDLE,
    ATOMICITY_AFTER
}
