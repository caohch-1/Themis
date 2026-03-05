package io.themis.staticanalysis.framework;

import soot.SootMethod;
import soot.Unit;

public interface RpcFrameworkAdapter {
    String protocol();
    boolean isClientMethod(SootMethod method);
    boolean isServerMethod(SootMethod method);
    String extractAddressKey(Unit unit);
}
