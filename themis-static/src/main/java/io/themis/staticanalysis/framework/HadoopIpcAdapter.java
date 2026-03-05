package io.themis.staticanalysis.framework;

import soot.SootMethod;
import soot.Unit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HadoopIpcAdapter implements RpcFrameworkAdapter {
    private static final Pattern KEY_PATTERN = Pattern.compile("\"([A-Za-z0-9_.-]*(?:address|port|host|endpoint|bind|ipc|rpc)[A-Za-z0-9_.-]*)\"", Pattern.CASE_INSENSITIVE);

    @Override
    public String protocol() {
        return "HadoopIPC";
    }

    @Override
    public boolean isClientMethod(SootMethod method) {
        String sig = method.getSignature();
        if (RpcSignatureCatalog.isHadoopClientSignature(sig)) {
            return true;
        }
        String lower = sig.toLowerCase(Locale.ROOT);
        return lower.contains("org.apache.hadoop.ipc") &&
            (lower.contains("getproxy") || lower.contains("getprotocolproxy") || lower.contains("waitforprotocolproxy"));
    }

    @Override
    public boolean isServerMethod(SootMethod method) {
        String sig = method.getSignature();
        if (RpcSignatureCatalog.isHadoopServerSignature(sig)) {
            return true;
        }
        String lower = sig.toLowerCase(Locale.ROOT);
        return lower.contains("org.apache.hadoop.ipc") && (lower.contains("getserver") || lower.contains("build()"));
    }

    @Override
    public String extractAddressKey(Unit unit) {
        String text = unit == null ? "" : unit.toString();
        String matched = extractExplicitKey(text);
        if (!matched.isEmpty()) {
            return matched;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("getsocketaddr") || lower.contains("inetsocketaddress") || lower.contains("bind") || lower.contains("setsocketaddr")) {
            return "hadoop.rpc.address";
        }
        if (lower.contains("port") && lower.contains("conf")) {
            return "hadoop.rpc.port";
        }
        if (lower.contains("host") && lower.contains("conf")) {
            return "hadoop.rpc.host";
        }
        return "";
    }

    private String extractExplicitKey(String text) {
        Matcher matcher = KEY_PATTERN.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
}
