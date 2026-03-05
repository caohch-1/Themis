package io.themis.staticanalysis.framework;

import soot.SootMethod;
import soot.Unit;

public class NettyAdapter implements RpcFrameworkAdapter {
    @Override
    public String protocol() {
        return "Netty";
    }

    @Override
    public boolean isClientMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("Bootstrap") || sig.contains("ChannelFuture");
    }

    @Override
    public boolean isServerMethod(SootMethod method) {
        String sig = method.getSignature();
        return sig.contains("ServerBootstrap") || sig.contains("ChannelInboundHandler");
    }

    @Override
    public String extractAddressKey(Unit unit) {
        String text = unit.toString();
        if (text.contains("bind") || text.contains("connect")) {
            return "netty.endpoint";
        }
        return "";
    }
}
