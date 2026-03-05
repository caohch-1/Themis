package indi.dc.extraction.address;

import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.utils.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import soot.jimple.StringConstant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static indi.dc.extraction.address.FileAddressPropertyChecker.checkIfFilePathProperty;
import static indi.dc.extraction.utils.Utils.getJars;

public class FileAddressExtraction {
    public static Set<PropertyHelper> findFileProperties(String systemPath, String systemName, Set<StringConstant> configPropertiesInSystem) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        String outputPath = "E:\\DCAnalyzer\\src\\main\\resources\\logs\\FileAddressProperty_" + systemName + ".txt";

        if (Files.exists(Paths.get(outputPath))) {
            Set<PropertyHelper> cachedResult = readResultsFromFile(outputPath);
            if (!cachedResult.isEmpty()) {
                Log.i("[Res] Find file path properties in existing file.");
                return cachedResult;
            }
        }

        Map<String, Document> xmlFiles = findDefaultXMLFiles(systemPath);
        Map<String, Map<String, PropertyHelper>> xmlProperties = parseXMLProperties(xmlFiles);
        Set<PropertyHelper> filePathProperty = new HashSet<>();
        Set<String> propertyNamesInXML = new HashSet<>();
        for (String xmlFile : xmlProperties.keySet()) {
            for (String name : xmlProperties.get(xmlFile).keySet()) {
                propertyNamesInXML.add(name);
                PropertyHelper propertyHelper = xmlProperties.get(xmlFile).get(name);
                String value = propertyHelper.value;
                String description = propertyHelper.description;
                String propertyCategory = checkIfFilePathProperty(name, value, description);
                if (propertyCategory.equals("PATH")) {
                    filePathProperty.add(propertyHelper);
                    Log.i("FILE PATH: ", propertyHelper);
                }
            }
        }

        for (StringConstant configProperty : configPropertiesInSystem) {
            String configPropertyName = configProperty.value;
            if (!propertyNamesInXML.contains(configPropertyName)) {
                String propertyCategory = checkIfFilePathProperty(configPropertyName, "", "");
                if (propertyCategory.equals("PATH")) {
                    PropertyHelper propertyHelper = new PropertyHelper(configPropertyName, "", "", "System");
                    filePathProperty.add(propertyHelper);
                    Log.i("FILE PATH: ", propertyHelper);
                }
            }
        }

        writeResults(outputPath, filePathProperty);
        return filePathProperty;
    }


    public static Map<String, Document> findDefaultXMLFiles(String projectPath) throws ParserConfigurationException, SAXException, IOException {
        Map<String, Document> xmlFiles = new HashMap<>();
        for (String jarPath : getJars(projectPath)) {
            try (JarFile jarFile = new JarFile(jarPath)) {
                // 遍历 JAR 文件中的所有条目
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    // 检查条目名称是否以 "-default.xml" 结尾
                    if (entryName.endsWith("-default.xml") || entryName.endsWith("-site.xml")) {
                        try (InputStream inputStream = jarFile.getInputStream(entry)) {
                            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            Document document = builder.parse(inputStream);
                            // 将文件名和 Document 存储到 Map 中
                            xmlFiles.put(entryName, document);
                        }
                    }
                }
            }
        }

        return xmlFiles;
    }

    public static class PropertyHelper {
        public String name;
        public String value;
        public String description;
        public String xmlFile;
        public PropertyHelper(String name, String value, String description, String xmlFile) {
            this.name = name;
            this.value = value;
            this.description = description;
            this.xmlFile = xmlFile;
        }

        @Override
        public String toString() {
            return "File: " + xmlFile + ", Name: " + name + ", Value: " + value + ", Description: " + description;
        }
    }

    public static Map<String, Map<String, PropertyHelper>> parseXMLProperties(Map<String, Document> xmlFiles) throws XPathExpressionException {
        Map<String, Map<String, PropertyHelper>> allProperties = new HashMap<>();

        // 遍历每个 XML 文件
        for (Map.Entry<String, Document> entry : xmlFiles.entrySet()) {
            String fileName = entry.getKey();
            Document document = entry.getValue();

            Map<String, PropertyHelper> properties = new HashMap<>();

            // 使用 XPath 提取所有 <property> 元素
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList propertyNodes = (NodeList) xPath.evaluate("//property", document, XPathConstants.NODESET);

            for (int i = 0; i < propertyNodes.getLength(); i++) {
                Node propertyNode = propertyNodes.item(i);
                if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element propertyElement = (Element) propertyNode;

                    // 获取 name 元素内容
                    String name = getTextContent(propertyElement, "name");
                    // 获取 value 元素内容，可能为空
                    String value = getTextContent(propertyElement, "value");
                    // 获取 description 元素内容，可能为空
                    String description = getTextContent(propertyElement, "description");

                    // 将提取到的属性存储在 Map 中
                    properties.put(name, new PropertyHelper(name, value, description, fileName));
                }
            }
            // 将解析结果存储在最终的 Map 中
            allProperties.put(fileName, properties);
        }

        return allProperties;
    }

    // 获取元素的文本内容，如果元素不存在则返回空字符串
    private static String getTextContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim().replace("\n", " ").replace("\r", " ").replace("\t", " ");
        }
        return ""; // 如果没有找到该元素，则返回空字符串
    }

    // 结果写入方法
    private static void writeResults(String filePath, Set<PropertyHelper> paths) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            writer.write("=== File Path Properties ===\n");
            writeProperties(writer, paths);

        }
    }

    // 辅助写入方法
    private static void writeProperties(BufferedWriter writer, Set<PropertyHelper> properties) throws IOException {
        for (PropertyHelper prop : properties) {
            String line = String.format("File: %s, Name: %s , Value: %s , Description: %s", prop.xmlFile, prop.name, prop.value, prop.description);
            writer.write(line);
            writer.newLine();
        }
    }

    private static Set<PropertyHelper> readResultsFromFile(String filePath) throws IOException {
        Set<PropertyHelper> paths = new LinkedHashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            Set<PropertyHelper> currentSet = null;
            Pattern linePattern = Pattern.compile("^File: (.*?), Name: (.*?) , Value: (.*?) , Description: (.*)$");

            String line;
            while ((line = reader.readLine()) != null) {
                // 匹配段落标题
                if (line.startsWith("=== File Path Properties ===")) {
                    currentSet = paths;
                    continue;
                }

                // 跳过空行和注释
                if (line.trim().isEmpty() || currentSet == null) {
                    continue;
                }

                // 解析属性行
                Matcher matcher = linePattern.matcher(line);
                if (matcher.matches()) {
                    PropertyHelper prop = new PropertyHelper(
                            matcher.group(2).trim(),  // name
                            matcher.group(3).trim(),  // value
                            matcher.group(4).trim(),  // description
                            matcher.group(1).trim()   // xmlFile
                    );
                    currentSet.add(prop);
                }
            }
        }
        return paths;
    }

}
