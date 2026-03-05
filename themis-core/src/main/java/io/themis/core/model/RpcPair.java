package io.themis.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RpcPair {
    private final String id;
    private final String protocol;
    private final String clientClass;
    private final String clientMethod;
    private final String serverClass;
    private final String serverMethod;
    private final List<String> clientAddressKeys;
    private final List<String> serverAddressKeys;
    private final Map<String, String> metadata;

    public RpcPair(String id,
                   String protocol,
                   String clientClass,
                   String clientMethod,
                   String serverClass,
                   String serverMethod,
                   List<String> clientAddressKeys,
                   List<String> serverAddressKeys,
                   Map<String, String> metadata) {
        this.id = id;
        this.protocol = protocol;
        this.clientClass = clientClass;
        this.clientMethod = clientMethod;
        this.serverClass = serverClass;
        this.serverMethod = serverMethod;
        this.clientAddressKeys = clientAddressKeys == null ? new ArrayList<>() : new ArrayList<>(clientAddressKeys);
        this.serverAddressKeys = serverAddressKeys == null ? new ArrayList<>() : new ArrayList<>(serverAddressKeys);
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getClientClass() {
        return clientClass;
    }

    public String getClientMethod() {
        return clientMethod;
    }

    public String getServerClass() {
        return serverClass;
    }

    public String getServerMethod() {
        return serverMethod;
    }

    public List<String> getClientAddressKeys() {
        return new ArrayList<>(clientAddressKeys);
    }

    public List<String> getServerAddressKeys() {
        return new ArrayList<>(serverAddressKeys);
    }

    public Map<String, String> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public boolean hasOverlappingAddressKeys() {
        for (String key : clientAddressKeys) {
            if (serverAddressKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RpcPair)) {
            return false;
        }
        RpcPair rpcPair = (RpcPair) o;
        return Objects.equals(id, rpcPair.id)
            && Objects.equals(protocol, rpcPair.protocol)
            && Objects.equals(clientClass, rpcPair.clientClass)
            && Objects.equals(clientMethod, rpcPair.clientMethod)
            && Objects.equals(serverClass, rpcPair.serverClass)
            && Objects.equals(serverMethod, rpcPair.serverMethod)
            && Objects.equals(clientAddressKeys, rpcPair.clientAddressKeys)
            && Objects.equals(serverAddressKeys, rpcPair.serverAddressKeys)
            && Objects.equals(metadata, rpcPair.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, protocol, clientClass, clientMethod, serverClass, serverMethod, clientAddressKeys, serverAddressKeys, metadata);
    }
}
