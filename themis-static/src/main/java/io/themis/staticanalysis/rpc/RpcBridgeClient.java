package io.themis.staticanalysis.rpc;

import io.themis.core.model.RpcPair;

import java.io.IOException;
import java.util.List;

public interface RpcBridgeClient {
    List<RpcPair> extractRpcPairs(String codeRoot) throws IOException, InterruptedException;
}
