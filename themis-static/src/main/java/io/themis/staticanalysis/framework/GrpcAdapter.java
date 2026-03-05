package io.themis.staticanalysis.framework;

import soot.SootMethod;
import soot.Unit;

public class GrpcAdapter implements RpcFrameworkAdapter {
    @Override
    public String protocol() {
        return "gRPC";
    }

    @Override
    public boolean isClientMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("Stub") || sig.contains("ManagedChannel");
    }

    @Override
    public boolean isServerMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("ImplBase") || sig.contains("ServerCall");
    }

    @Override
    public String extractAddressKey(Unit unit) {
        String text = unit.toString();
        if (text.contains("forAddress")) {
            return "grpc.address";
        }
        return "";
    }
}
