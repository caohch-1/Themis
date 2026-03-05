package io.themis.staticanalysis.framework;

import soot.SootMethod;
import soot.Unit;

public class ThriftAdapter implements RpcFrameworkAdapter {
    @Override
    public String protocol() {
        return "Thrift";
    }

    @Override
    public boolean isClientMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("TServiceClient") || sig.contains("TBinaryProtocol");
    }

    @Override
    public boolean isServerMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("TProcessor") || sig.contains("TServer");
    }

    @Override
    public String extractAddressKey(Unit unit) {
        String text = unit.toString();
        if (text.contains("open") || text.contains("socket")) {
            return "thrift.endpoint";
        }
        return "";
    }
}
