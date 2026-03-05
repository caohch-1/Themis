package io.themis.staticanalysis.framework;

import soot.SootMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FrameworkAdapterRegistry {
    private final List<RpcFrameworkAdapter> adapters;

    public FrameworkAdapterRegistry() {
        List<RpcFrameworkAdapter> loaded = new ArrayList<>();
        loaded.add(new GrpcAdapter());
        loaded.add(new HadoopIpcAdapter());
        loaded.add(new NettyAdapter());
        loaded.add(new ThriftAdapter());
        this.adapters = Collections.unmodifiableList(loaded);
    }

    public List<RpcFrameworkAdapter> all() {
        return adapters;
    }

    public RpcFrameworkAdapter byProtocol(String protocol) {
        for (RpcFrameworkAdapter adapter : adapters) {
            if (adapter.protocol().equalsIgnoreCase(protocol)) {
                return adapter;
            }
        }
        return null;
    }

    public RpcFrameworkAdapter byMethod(SootMethod method, boolean clientMethod) {
        for (RpcFrameworkAdapter adapter : adapters) {
            boolean matched = clientMethod ? adapter.isClientMethod(method) : adapter.isServerMethod(method);
            if (matched) {
                return adapter;
            }
        }
        return null;
    }
}
