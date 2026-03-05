package io.themis.staticanalysis.rpc;

import io.themis.core.model.RpcPair;
import io.themis.staticanalysis.framework.FrameworkAdapterRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RpcPairExtractor {
    private final RpcBridgeClient rpcBridgeClient;
    private final ConfigurableAddressAnalyzer addressAnalyzer;

    public RpcPairExtractor(String rpcBridgeCommand) {
        this(rpcBridgeCommand, new ArrayList<String>());
    }

    public RpcPairExtractor(String rpcBridgeCommand, List<String> targetSourceRoots) {
        this.rpcBridgeClient = new RpcBridgeCommandClient(rpcBridgeCommand, targetSourceRoots);
        this.addressAnalyzer = new ConfigurableAddressAnalyzer(new FrameworkAdapterRegistry());
    }

    public RpcPairExtractor(RpcBridgeClient rpcBridgeClient, ConfigurableAddressAnalyzer addressAnalyzer) {
        this.rpcBridgeClient = rpcBridgeClient;
        this.addressAnalyzer = addressAnalyzer;
    }

    public List<RpcPair> extractAndPrune(String codeRoot) throws IOException, InterruptedException {
        addressAnalyzer.prepare(codeRoot);
        List<RpcPair> raw = rpcBridgeClient.extractRpcPairs(codeRoot);
        List<RpcPair> filtered = new ArrayList<RpcPair>();
        for (RpcPair pair : raw) {
            RpcPair enriched = addressAnalyzer.enrich(pair);
            if (addressAnalyzer.isAddressCompatible(enriched)) {
                filtered.add(enriched);
            }
        }
        return filtered;
    }
}
