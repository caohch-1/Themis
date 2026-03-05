package io.themis.staticanalysis.rpc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class XmlPropertyExtractor {
    private static final Pattern NETWORK_HINT = Pattern.compile("\\b(ip|address|addresses|port|host|endpoint|bind|rpc|ipc)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_HINT = Pattern.compile("\\b(path|paths|file|dir|dirs|directory|location|root)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile("^(localhost|(?:(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9]))$");
    private static final Pattern IPV6 = Pattern.compile("^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4})?::(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4}))$");

    public ExtractionResult extractFromProjectRoot(String projectRoot) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            return ExtractionResult.empty();
        }
        Path root = Paths.get(projectRoot);
        if (!Files.exists(root)) {
            return ExtractionResult.empty();
        }
        Set<String> address = new LinkedHashSet<String>();
        Set<String> port = new LinkedHashSet<String>();
        Set<String> file = new LinkedHashSet<String>();
        for (Path jar : discoverJars(root)) {
            extractFromJar(jar, address, port, file);
        }
        return new ExtractionResult(address, port, file);
    }

    private List<Path> discoverJars(Path root) {
        List<Path> jars = new ArrayList<Path>();
        try {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .forEach(jars::add);
        } catch (IOException e) {
            return jars;
        }
        return jars;
    }

    private void extractFromJar(Path jarPath,
                                Set<String> address,
                                Set<String> port,
                                Set<String> file) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!(name.endsWith("-default.xml") || name.endsWith("-site.xml"))) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    parseProperties(in, address, port, file);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void parseProperties(InputStream in,
                                 Set<String> address,
                                 Set<String> port,
                                 Set<String> file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(in);
        NodeList properties = document.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            Node node = properties.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String propName = normalize(textContent(element, "name"));
            String value = normalize(textContent(element, "value"));
            String desc = normalize(textContent(element, "description"));
            if (propName.isEmpty()) {
                continue;
            }
            String networkClass = classifyNetworkProperty(propName, value, desc);
            if ("ADDRESS".equals(networkClass)) {
                address.add(propName);
            } else if ("PORT".equals(networkClass)) {
                port.add(propName);
            }
            if (isFileProperty(propName, value, desc)) {
                file.add(propName);
            }
        }
    }

    private String classifyNetworkProperty(String name, String value, String description) {
        String searchText = (name + " " + description).toLowerCase(Locale.ROOT);
        if (isIpAddress(value)) {
            return "ADDRESS";
        }
        if (isPortValue(value) && containsAny(name.toLowerCase(Locale.ROOT), "port", "rpc.port", "ipc.port")) {
            return "PORT";
        }
        if (!NETWORK_HINT.matcher(searchText).find()) {
            return "NONE";
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (containsAny(lowerName, "port", "rpc.port", "ipc.port") || containsAny(searchText, "listen port", "socket port")) {
            return "PORT";
        }
        if (containsAny(lowerName, "host", "address", "bind", "endpoint", "ip", "rpc.address", "ipc.address")) {
            return "ADDRESS";
        }
        if (containsAny(searchText, "hostname", "socket address", "ip address", "network endpoint")) {
            return "ADDRESS";
        }
        return "NONE";
    }

    private boolean isFileProperty(String name, String value, String description) {
        String merged = (name + " " + description).toLowerCase(Locale.ROOT);
        if (!FILE_HINT.matcher(merged).find()) {
            return isLikelyPath(value);
        }
        if (containsAny(merged, "classpath", "classloader")) {
            return false;
        }
        return true;
    }

    private boolean isLikelyPath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("/") || lower.contains("\\\\") || lower.startsWith("${") || lower.endsWith(".xml") || lower.endsWith(".properties");
    }

    private boolean isIpAddress(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return IPV4.matcher(value).matches() || IPV6.matcher(value).matches() || "::1".equals(value);
    }

    private boolean isPortValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            int v = Integer.parseInt(value);
            return v > 0 && v <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean containsAny(String text, String... tokens) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String textContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    public static class ExtractionResult {
        private final Set<String> addressProperties;
        private final Set<String> portProperties;
        private final Set<String> filePathProperties;

        public ExtractionResult(Set<String> addressProperties,
                                Set<String> portProperties,
                                Set<String> filePathProperties) {
            this.addressProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(addressProperties));
            this.portProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(portProperties));
            this.filePathProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(filePathProperties));
        }

        public Set<String> getAddressProperties() {
            return addressProperties;
        }

        public Set<String> getPortProperties() {
            return portProperties;
        }

        public Set<String> getFilePathProperties() {
            return filePathProperties;
        }

        public static ExtractionResult empty() {
            return new ExtractionResult(Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet());
        }
    }
}
