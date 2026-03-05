package io.themis.staticanalysis.rpc;

import io.themis.core.model.RpcPair;
import io.themis.staticanalysis.framework.FrameworkAdapterRegistry;
import io.themis.staticanalysis.framework.RpcFrameworkAdapter;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigurableAddressAnalyzer {
    private final FrameworkAdapterRegistry registry;
    private final XmlPropertyExtractor xmlPropertyExtractor;
    private XmlPropertyExtractor.ExtractionResult xmlProperties;

    public ConfigurableAddressAnalyzer(FrameworkAdapterRegistry registry) {
        this.registry = registry;
        this.xmlPropertyExtractor = new XmlPropertyExtractor();
        this.xmlProperties = XmlPropertyExtractor.ExtractionResult.empty();
    }

    public void prepare(String codeRoot) {
        this.xmlProperties = xmlPropertyExtractor.extractFromProjectRoot(codeRoot);
    }

    public RpcPair enrich(RpcPair pair) {
        Set<String> clientKeys = new LinkedHashSet<String>(pair.getClientAddressKeys());
        Set<String> serverKeys = new LinkedHashSet<String>(pair.getServerAddressKeys());
        RpcFrameworkAdapter adapter = registry.byProtocol(pair.getProtocol());
        if (adapter != null) {
            clientKeys.addAll(scanMethod(pair.getClientClass(), pair.getClientMethod(), adapter));
            serverKeys.addAll(scanMethod(pair.getServerClass(), pair.getServerMethod(), adapter));
        } else {
            clientKeys.addAll(scanMethodWithAllAdapters(pair.getClientClass(), pair.getClientMethod()));
            serverKeys.addAll(scanMethodWithAllAdapters(pair.getServerClass(), pair.getServerMethod()));
        }
        Set<String> xmlNetworkKeys = new LinkedHashSet<String>();
        xmlNetworkKeys.addAll(xmlProperties.getAddressProperties());
        xmlNetworkKeys.addAll(xmlProperties.getPortProperties());
        Set<String> clientMentioned = collectMentionedConfigKeys(pair.getClientClass(), pair.getClientMethod(), xmlNetworkKeys);
        Set<String> serverMentioned = collectMentionedConfigKeys(pair.getServerClass(), pair.getServerMethod(), xmlNetworkKeys);
        clientKeys.addAll(clientMentioned);
        serverKeys.addAll(serverMentioned);
        Map<String, String> metadata = new LinkedHashMap<String, String>(pair.getMetadata());
        metadata.put("xml.file.path.keys", Integer.toString(xmlProperties.getFilePathProperties().size()));
        metadata.put("xml.client.mentioned.keys", Integer.toString(clientMentioned.size()));
        metadata.put("xml.server.mentioned.keys", Integer.toString(serverMentioned.size()));
        return new RpcPair(
            pair.getId(),
            pair.getProtocol(),
            pair.getClientClass(),
            pair.getClientMethod(),
            pair.getServerClass(),
            pair.getServerMethod(),
            new ArrayList<String>(clientKeys),
            new ArrayList<String>(serverKeys),
            metadata);
    }

    public boolean isAddressCompatible(RpcPair pair) {
        if (pair.getClientAddressKeys().isEmpty() || pair.getServerAddressKeys().isEmpty()) {
            return true;
        }
        Set<String> left = normalize(pair.getClientAddressKeys());
        Set<String> right = normalize(pair.getServerAddressKeys());
        for (String key : left) {
            if (right.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private List<String> scanMethod(String className, String methodName, RpcFrameworkAdapter adapter) {
        List<String> keys = new ArrayList<String>();
        if (!Scene.v().containsClass(className)) {
            return keys;
        }
        SootClass sootClass = Scene.v().getSootClass(className);
        for (SootMethod method : sootClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (!method.hasActiveBody()) {
                continue;
            }
            for (Unit unit : method.getActiveBody().getUnits()) {
                String key = adapter.extractAddressKey(unit);
                if (key != null && !key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private List<String> scanMethodWithAllAdapters(String className, String methodName) {
        Set<String> keys = new LinkedHashSet<String>();
        for (RpcFrameworkAdapter adapter : registry.all()) {
            keys.addAll(scanMethod(className, methodName, adapter));
        }
        return new ArrayList<String>(keys);
    }

    private Set<String> collectMentionedConfigKeys(String className, String methodName, Set<String> knownKeys) {
        Set<String> mentioned = new LinkedHashSet<String>();
        if (knownKeys.isEmpty()) {
            return mentioned;
        }
        if (!Scene.v().containsClass(className)) {
            return mentioned;
        }
        SootClass sootClass = Scene.v().getSootClass(className);
        for (SootMethod method : sootClass.getMethods()) {
            if (!method.getName().equals(methodName) || !method.hasActiveBody()) {
                continue;
            }
            for (Unit unit : method.getActiveBody().getUnits()) {
                String text = unit.toString().toLowerCase(Locale.ROOT);
                for (String key : knownKeys) {
                    String normalizedKey = key.toLowerCase(Locale.ROOT);
                    if (text.contains(normalizedKey)) {
                        mentioned.add(key);
                    }
                }
            }
        }
        return mentioned;
    }

    private Set<String> normalize(List<String> keys) {
        Set<String> normalized = new LinkedHashSet<String>();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String v = key.trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) {
                continue;
            }
            normalized.add(v);
            if (v.contains(":")) {
                normalized.add(v.substring(0, v.indexOf(':')));
            }
            if (v.contains("=")) {
                normalized.add(v.substring(0, v.indexOf('=')));
            }
        }
        return normalized;
    }
}
