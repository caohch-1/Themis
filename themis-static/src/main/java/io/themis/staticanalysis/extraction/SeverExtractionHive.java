package indi.dc.extraction;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.analysis.CallGraphBuilder;
import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.entity.Adapter;
import cn.ac.ios.bridge.entity.HadoopAdapter;
import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.address.AddressExtraction;
import indi.dc.extraction.impact.ImpactAnalysis;
import indi.dc.extraction.impact.ImpactRWSameAccessSite;
import indi.dc.extraction.readwrite.RWSameAccessSite;
import indi.dc.extraction.sameaccess.SameAccessSite;
import indi.dc.extraction.utils.MethodOverrideChecker;
import indi.dc.extraction.utils.RPCSignatures;
import indi.dc.extraction.utils.Pair;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static indi.dc.extraction.address.FileAddressExtraction.*;
import static indi.dc.extraction.address.PropertyChecker.isIPAddress;
import static indi.dc.extraction.readwrite.ReadWriteAnalysis.getReadWriteWhole;
import static indi.dc.extraction.readwrite.ReadWriteAnalysis.getReadWriteWholeStatic;
import static indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis.findSameClsVarAccessesWhole;
import static indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis.findSameStaticVarAccessesWhole;
import static indi.dc.extraction.utils.Utils.getJars;
import static indi.dc.extraction.utils.Utils.isSubclassOrDescendant;

public class SeverExtractionHive {
    public static CallGraph cg;

    public static final Adapter adapter = (Adapter)new HadoopAdapter();

    public static final Analyzer analyzer = new Analyzer(adapter);
    public static Set<String> protos;
    public static Set<String> impls;
    public static Map<String, Set<String>> protocol2impls;
    public static Map<String, String> impl2proto;
    public static HashMap<String, Set<Analyzer.CallSite>> impl2handlerSite;
    public static HashMap<String, Set<Analyzer.CallSite>> proxy2CallSite;
    public static Map<String, Set<Analyzer.CallSite>> proxy2CreateCallSite;

    public static void main(String[] args) throws IOException, XPathExpressionException, ParserConfigurationException, SAXException {
        // Start Soot
        String systemName = "hive_4.0.1";
        String systemPath = "E:\\DCAnalyzer\\src\\main\\resources\\"+systemName+"\\sys_jars";
        start(systemName, systemPath);

        // RPCBridge Analysis
        analyzer.start();
        loadRPCBridgeResults(systemName);

        // thread-unsafe analysis
//        Map<String, Set<String>> hashUsesSever = new HashMap<>();
//        Map<String, Set<String>> hashUsesClient = new HashMap<>();
//
//        for (String impl : impls) {
//            Set<Analyzer.CallSite> serverSites = new HashSet<>();
//
//            Set<Analyzer.CallSite> tempSite = impl2handlerSite.get(impl);
//            if (tempSite == null) {
//                continue;
//            }
//            serverSites.addAll(tempSite);
//
//            Set<Analyzer.CallSite> clientSites = new HashSet<>();
//
//            String temp = impl2proto.get(impl);
//            if (temp == null) {
//                continue;
//            }
//            tempSite = proxy2CallSite.get(temp);
//            if (tempSite == null) {
//                continue;
//            }
//            clientSites.addAll(tempSite);
//
//
//            for (Analyzer.CallSite callSite : serverSites) {
//                SootClass sootClass = callSite.getCallMethod().getDeclaringClass();
//                for (SootMethod sootMethod : sootClass.getMethods()) {
//                    if (sootMethod.hasActiveBody()) {
//                        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
//                            if (unit instanceof AssignStmt) {
//                                if (((AssignStmt) unit).getRightOp() instanceof NewExpr && (unit.toString().contains("HashMap") || unit.toString().contains("HashSet")
//                                        || unit.toString().contains("LinkedHashMap") || unit.toString().contains("LinkedHashSet")
//                                        || unit.toString().contains("TreeMap") || unit.toString().contains("TreeSet") ||
//                                        unit.toString().contains("ArrayList"))) {
//                                    if (!hashUsesSever.containsKey(sootClass.toString())) hashUsesSever.put(sootClass.toString(), new HashSet<>());
//                                    hashUsesSever.get(sootClass.toString()).add(sootMethod.toString() + "   " + unit.toString());
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            for (Analyzer.CallSite callSite : clientSites) {
//                SootClass sootClass = callSite.getCallMethod().getDeclaringClass();
//                for (SootMethod sootMethod : sootClass.getMethods()) {
//                    if (sootMethod.hasActiveBody()) {
//                        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
//                            if (unit instanceof AssignStmt) {
//                                if (((AssignStmt) unit).getRightOp() instanceof NewExpr && (unit.toString().contains("HashMap") || unit.toString().contains("HashSet")
//                                        || unit.toString().contains("LinkedHashMap") || unit.toString().contains("LinkedHashSet")
//                                        || unit.toString().contains("TreeMap") || unit.toString().contains("TreeSet") ||
//                                        unit.toString().contains("ArrayList"))) {
//                                    if (!hashUsesClient.containsKey(sootClass.toString())) hashUsesClient.put(sootClass.toString(), new HashSet<>());
//                                    hashUsesClient.get(sootClass.toString()).add(sootMethod.toString() + " ||| " + unit.toString());
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        for (String cls : hashUsesSever.keySet()) {
//            Log.i(cls);
//            for (String strxxx : hashUsesSever.get(cls)) Log.i(strxxx);
//            Log.i("====================");
//        }
//        System.out.println("||||||||||||||||||||||||||||||||||||||");
//        System.out.println("||||||||||||||||||||||||||||||||||||||");
//        System.out.println("||||||||||||||||||||||||||||||||||||||");
//        for (String cls : hashUsesClient.keySet()) {
//            Log.i(cls);
//            for (String strxxx : hashUsesClient.get(cls)) Log.i(strxxx);
//            Log.i("====================");
//        }



        // File Analysis
//        Map<String, Set<String>> fileOperations = new HashMap<>();
//        fileOperations.put("java.nio.file.Files", new HashSet<>(Arrays.asList(new String[]{"readAllBytes", "readAllLines", "write", "newBufferedReader", "newBufferedWriter", "createFile", "<init>", "<clinit>"})));
//        fileOperations.put("java.io.File", new HashSet<>(Arrays.asList(new String[]{"<init>", "<clinit>"})));
//        fileOperations.put("java.io.FileInputStream", new HashSet<>(Arrays.asList(new String[]{"read", "<init>", "<clinit>"})));
//        fileOperations.put("java.io.FileOutputStream", new HashSet<>(Arrays.asList(new String[]{"write", "<init>", "<clinit>"})));
//        fileOperations.put("java.io.FileReader", new HashSet<>(Arrays.asList(new String[]{"read", "<init>", "<clinit>"})));
//        fileOperations.put("java.util.zip.ZipFile", new HashSet<>(Arrays.asList(new String[]{"<init>", "<clinit>"})));
//        fileOperations.put("java.util.zip.GZIPInputStream", new HashSet<>(Arrays.asList(new String[]{"read", "<init>", "<clinit>"})));
//        fileOperations.put("java.util.jar.JarFile", new HashSet<>(Arrays.asList(new String[]{"<init>", "<clinit>"})));
//
//
//
//        int fileCount = 0;
//        int constantCount = 0;
//        int configCount = 0;
//        for (SootClass sootClass : Scene.v().getClasses()) {
//            if (sootClass.isApplicationClass()) {
//                for (SootMethod sootMethod : sootClass.getMethods()) {
//                    if (!sootMethod.hasActiveBody()) continue;
//                    Body body = sootMethod.retrieveActiveBody();
//                    SimpleLocalDefs defAnalysis = new SimpleLocalDefs(new CompleteUnitGraph(body));
//                    for (Unit unit : body.getUnits()) {
//                        Stmt stmt = (Stmt)unit;
//                        if (stmt.containsInvokeExpr()) {
//                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
//                            SootMethod invokedMethod = invokeExpr.getMethod();
//                            SootClass invokedClass = invokedMethod.getDeclaringClass();
//                            if (fileOperations.getOrDefault(invokedClass.getName(), new HashSet<>()).contains(invokedMethod.getName())) {
//                                fileCount++;
//                                Log.i("======================");
//                                Log.i("Invoked method: "+invokedMethod);
//                                Log.i("Caller method: "+sootMethod);
//                                Log.i("Invoking stmt: "+stmt);
//                                for (Value arg : invokeExpr.getArgs()) {
//                                    if (arg instanceof StringConstant) {
//                                        constantCount++;
//                                        Log.i("StringConstant Arg: "+arg);
//                                    }
//                                    if (arg instanceof Local) {
//                                        if (arg.getType().toString().equals("java.lang.String")) {
//                                            for (Unit defUnit : new HashSet<>(defAnalysis.getDefsOf((Local) arg))) {
//                                                if (defUnit.toString().toLowerCase().contains("configuration") || defUnit.toString().toLowerCase().contains("config")) {
//                                                    if (sootMethod.toString().toLowerCase().contains("configuration")) {continue;}
//                                                    configCount++;
//                                                    Log.i("SystemConfiguration: "+defUnit);
//                                                }
//                                            }
//                                        }
//                                    }
//
//                                }
//
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        Log.i("=====================");
//        Log.i("FileCount: "+fileCount);
//        Log.i("ConstantCount: "+constantCount);
//        Log.i("ConfigCount: "+configCount);
//        System.exit(0);

        // FilePath-property Analysis
//        Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage = findConfigPropertyUsage(systemName);
//        Set<FileAddressExtraction.PropertyHelper> filePaths = findFileProperties(systemPath, systemName, configPropertyUsage.keySet());
//        Map<FileAddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage = findFilePathInfo(filePaths, configPropertyUsage, systemName);


        // Address-property Analysis
//        Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage = findConfigPropertyUsage(systemName);
//        Pair<Set<AddressExtraction.PropertyHelper>, Set<AddressExtraction.PropertyHelper>> addressPorts = findAddressPortProperties(systemPath, systemName, configPropertyUsage.keySet());
//        Map<AddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage = findAddressPortInfo(addressPorts, configPropertyUsage, systemName);
//        Set<String> addressPropertyNames = new HashSet<>();
//        for (AddressExtraction.PropertyHelper propertyHelper : addressPropertyUsage.keySet()) {
//            addressPropertyNames.add(propertyHelper.name);
//        }
//
//        Set<SootClass> configurationClasses = new HashSet<>();
//        for (SootClass sc : Scene.v().getApplicationClasses()) {
//            if (sc.getName().equals("org.apache.hadoop.yarn.service.api.records.Configuration") || sc.getName().equals("org.apache.hadoop.conf.Configuration")) {
//                configurationClasses.add(sc);
//            }
//        }
//        Iterator<SootClass> iterator = Scene.v().getApplicationClasses().iterator();
//        while (iterator.hasNext()) {
//            SootClass sc = iterator.next();
//            Set<SootClass> toAdd = new HashSet<>();
//            for (SootClass configurationClass : configurationClasses) {
//                if (isSubclassOrDescendant(sc, configurationClass)) {
//                    toAdd.add(sc);
//                }
//            }
//            configurationClasses.addAll(toAdd);
//        }
//
//        for (Set<Analyzer.CallSite> callSites : impl2handlerSite.values()) {
//            for (Analyzer.CallSite callSite : callSites) {
//                SootMethod newHandlerMethod = callSite.getCallMethod();
//                SootClass implClass = Scene.v().getSootClassUnsafe(callSite.getImplType().toString());
//
//                Log.i("============");
//                Log.i(implClass);
//                Log.i(newHandlerMethod);
//
//                Set<String> relatedAddressPropertiesNames = new HashSet<>();
//                for (SootMethod relatedMethod : implClass.getMethods()) {
//                    checkMethodForAddressProperties(relatedMethod, relatedAddressPropertiesNames, configurationClasses, addressPropertyNames);
//                }
//
//                if (relatedAddressPropertiesNames.isEmpty()) {
//                    checkMethodForAddressProperties(newHandlerMethod, relatedAddressPropertiesNames, configurationClasses, addressPropertyNames);
//                }
//
//                if (relatedAddressPropertiesNames.isEmpty()) {
//                    Queue<Pair<SootMethod, Integer>> queue = new LinkedList<>();
//                    Set<SootMethod> visited = new HashSet<>();
//                    queue.add(new Pair<>(newHandlerMethod, 0));
//                    visited.add(newHandlerMethod);
//
//                    while (!queue.isEmpty()) {
//                        Pair<SootMethod, Integer> pair = queue.poll();
//                        SootMethod currentMethod = pair.getKey();
//                        int currentDepth = pair.getValue();
//
//                        // 检查当前方法体
//                        checkMethodForAddressProperties(currentMethod, relatedAddressPropertiesNames, configurationClasses, addressPropertyNames);
//                        if (!relatedAddressPropertiesNames.isEmpty()) {break;}
//
//                        // 限制调用链深度
//                        if (currentDepth >= 3) continue;
//
//                        // 获取相邻方法（调用者和被调用者）
//                        Set<SootMethod> adjacentMethods = new HashSet<>();
//
//                        // 获取调用者（callers）
//                        for (SootClass sc : Scene.v().getApplicationClasses()) {
//                            for (SootMethod m : sc.getMethods()) {
//                                if (!m.hasActiveBody()) continue;
//                                for (Unit u : m.getActiveBody().getUnits()) {
//                                    if (u instanceof Stmt) {
//                                        Stmt stmt = (Stmt) u;
//                                        if (stmt.containsInvokeExpr() && stmt.getInvokeExpr().getMethod().equals(currentMethod)) {
//                                            adjacentMethods.add(m);
//                                        }
//                                    }
//                                }
//                            }
//                        }
//
//                        // 获取被调用者（callees）
//                        if (currentMethod.hasActiveBody()) {
//                            for (Unit u : currentMethod.getActiveBody().getUnits()) {
//                                if (u instanceof Stmt) {
//                                    Stmt stmt = (Stmt) u;
//                                    if (stmt.containsInvokeExpr()) {
//                                        adjacentMethods.add(stmt.getInvokeExpr().getMethod());
//                                    }
//                                }
//                            }
//                        }
//
//                        // 添加未访问的相邻方法到队列
//                        for (SootMethod m : adjacentMethods) {
//                            if (!visited.contains(m)) {
//                                visited.add(m);
//                                queue.add(new Pair<>(m, currentDepth + 1));
//                            }
//                        }
//                    }
//                }
//
//
//                Log.i(relatedAddressPropertiesNames);
//            }
//        }


        /* A. Instance Variable*/
        instancePairs();

        /* B. Static variable*/
//        StaticPairs();

        /* C. Local variable*/
//        localPairs();

    }

    private static void findAllPropertiesMultiUsage(String systemName, String systemPath) throws XPathExpressionException, IOException, ParserConfigurationException, SAXException {
        Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsagePre = findConfigPropertyUsage(systemName);
        Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage = new HashMap<>();
        for (StringConstant key : configPropertyUsagePre.keySet()) {
            Set<Pair<SootMethod, Stmt>> usages = configPropertyUsagePre.get(key);
            if (usages == null) continue;
            if (usages.size() >= 2) {
                configPropertyUsage.put(key, usages);
            } else {
                SootMethod useMethod = usages.iterator().next().getKey();
                Set<String> callerPackage = new HashSet<>();
                for (Iterator<Edge> it = cg.edgesInto(useMethod); it.hasNext(); ) {
                    Edge edge = it.next();
                    callerPackage.add(edge.getSrc().method().getDeclaringClass().getName());
                }
                if (callerPackage.size() >= 2) {
                    configPropertyUsage.put(key, usages);
                }
            }
        }
        System.out.println(configPropertyUsage.size());

        Map<String, Document> xmlFiles = findDefaultXMLFiles(systemPath);
        Map<String, Map<String, PropertyHelper>> xmlProperties = parseXMLProperties(xmlFiles);
        Set<PropertyHelper> xmlConfigurationProperties = new HashSet<>();
        for (String xmlFile : xmlProperties.keySet()) {
            for (String name : xmlProperties.get(xmlFile).keySet()) {
                PropertyHelper propertyHelper = xmlProperties.get(xmlFile).get(name);
                xmlConfigurationProperties.add(propertyHelper);
            }
        }
        System.out.println(xmlConfigurationProperties.size());

        Map<PropertyHelper, Set<Pair<SootMethod, Stmt>>> propertyUsage = new HashMap<>();
        for (StringConstant property : configPropertyUsage.keySet()) {
            String propertyName = property.value;
            for (PropertyHelper propertyHelper : xmlConfigurationProperties) {
                if (propertyName.equals(propertyHelper.name)) {
                    propertyUsage.put(propertyHelper, configPropertyUsage.get(property));
                }
            }
        }

        saveFilePathPropertyUsage(propertyUsage, "E:\\DCAnalyzer\\src\\main\\resources\\logs\\PropertyUsageMulti_" + systemName + ".txt");
        System.exit(1);
    }

    private static void checkMethodForAddressProperties(SootMethod method, Set<String> addressPropertiesNames, Set<SootClass> configurationClasses, Set<String> addressPropertyNames) {
        if (!method.hasActiveBody()) return;

        for (Unit u : method.getActiveBody().getUnits()) {
            if (((Stmt) u).containsInvokeExpr()) {
                InvokeExpr invokeExpr = ((Stmt) u).getInvokeExpr();
                if (configurationClasses.contains(invokeExpr.getMethod().getDeclaringClass())) {
                    for (Value v : invokeExpr.getArgs()) {
                        if (v instanceof StringConstant && addressPropertyNames.contains(((StringConstant) v).value)) {
                            addressPropertiesNames.add(((StringConstant) v).value);
                        } else if (v instanceof StringConstant && isIPAddress(((StringConstant) v).value)) {
                            addressPropertiesNames.add(((StringConstant) v).value);
                        }
                    }
                }
            }
        }
    }

    private static Set<SootMethod> collectMethodsWithinDistance(SootMethod startMethod, int maxDistance) {
        CallGraph cg = Scene.v().getCallGraph();
        Set<SootMethod> visited = new HashSet<>();
        Queue<SootMethod> queue = new LinkedList<>();
        Map<SootMethod, Integer> distances = new HashMap<>();

        queue.add(startMethod);
        distances.put(startMethod, 0);
        visited.add(startMethod);

        while (!queue.isEmpty()) {
            SootMethod current = queue.poll();
            int currentDistance = distances.get(current);

            if (currentDistance >= maxDistance) {
                continue;
            }

            // 处理调用者（callers）
            Iterator<Edge> incomingEdges = cg.edgesInto(current);
            while (incomingEdges.hasNext()) {
                Edge edge = incomingEdges.next();
                MethodOrMethodContext src = edge.src();
                if (src instanceof SootMethod) {
                    SootMethod caller = (SootMethod) src;
                    if (!visited.contains(caller)) {
                        visited.add(caller);
                        distances.put(caller, currentDistance + 1);
                        queue.add(caller);
                    }
                }
            }

            // 处理被调用者（callees）
            Iterator<Edge> outgoingEdges = cg.edgesOutOf(current);
            while (outgoingEdges.hasNext()) {
                Edge edge = outgoingEdges.next();
                MethodOrMethodContext tgt = edge.tgt();
                SootMethod callee = tgt.method();
                if (!visited.contains(callee)) {
                    visited.add(callee);
                    distances.put(callee, currentDistance + 1);
                    queue.add(callee);
                }
            }
        }

        return visited;
    }

    public static Map<AddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> findAddressPortInfo(Pair<Set<AddressExtraction.PropertyHelper>, Set<AddressExtraction.PropertyHelper>> addressPorts, Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage, String systemName) throws IOException {
        Set<AddressExtraction.PropertyHelper> addressPortInfo = new HashSet<>();
        addressPortInfo.addAll(addressPorts.getKey());
        addressPortInfo.addAll(addressPorts.getValue());
        Map<AddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage = new HashMap<>();
        for (StringConstant property : configPropertyUsage.keySet()) {
            String propertyName = property.value;
            for (AddressExtraction.PropertyHelper propertyHelper : addressPortInfo) {
                if (propertyName.equals(propertyHelper.name)) {
                    addressPropertyUsage.put(propertyHelper, configPropertyUsage.get(property));
                }
            }
        }

        saveAddressPropertyUsage(addressPropertyUsage, "E:\\DCAnalyzer\\src\\main\\resources\\logs\\AddressPropertyUsage_" + systemName + ".txt");
        return addressPropertyUsage;
    }

    public static Map<PropertyHelper, Set<Pair<SootMethod, Stmt>>> findFilePathInfo(Set<PropertyHelper> filePaths, Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage, String systemName) throws IOException {
        Map<PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage = new HashMap<>();
        for (StringConstant property : configPropertyUsage.keySet()) {
            String propertyName = property.value;
            for (PropertyHelper propertyHelper : filePaths) {
                if (propertyName.equals(propertyHelper.name)) {
                    addressPropertyUsage.put(propertyHelper, configPropertyUsage.get(property));
                }
            }
        }

        saveFilePathPropertyUsage(addressPropertyUsage, "E:\\DCAnalyzer\\src\\main\\resources\\logs\\FilePathPropertyUsage_" + systemName + ".txt");
        return addressPropertyUsage;
    }

    public static Map<StringConstant, Set<Pair<SootMethod, Stmt>>> findConfigPropertyUsage(String systemName) throws IOException {
        Set<SootClass> configurationClasses = new HashSet<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getName().equals("org.apache.hadoop.yarn.service.api.records.Configuration") || sc.getName().equals("org.apache.hadoop.conf.Configuration")) {
                configurationClasses.add(sc);
            }
        }
        Iterator<SootClass> iterator = Scene.v().getApplicationClasses().iterator();
        while (iterator.hasNext()) {
            SootClass sc = iterator.next();
            Set<SootClass> toAdd = new HashSet<>();
            for (SootClass configurationClass : configurationClasses) {
                if (isSubclassOrDescendant(sc, configurationClass)) {
                    toAdd.add(sc);
                }
            }
            configurationClasses.addAll(toAdd);
        }

        Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage = new HashMap<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod scm : sc.getMethods()) {
                if (!scm.hasActiveBody()) continue;
                Body body = scm.retrieveActiveBody();
                for (Unit u : body.getUnits()) {
                    if (((Stmt) u).containsInvokeExpr()) {
                        InvokeExpr invokeExpr = ((Stmt) u).getInvokeExpr();
                        SootMethod invokeMethod = invokeExpr.getMethod();
                        if (configurationClasses.contains(invokeMethod.getDeclaringClass())) {
                            for (Value v : invokeExpr.getArgs()) {
                                if (v instanceof StringConstant && !((StringConstant) v).value.isEmpty()) {
                                    if (configPropertyUsage.containsKey((StringConstant) v)) {
                                        configPropertyUsage.get((StringConstant) v).add(new Pair<>(scm, (Stmt) u));
                                    } else {
                                        configPropertyUsage.put((StringConstant) v, new HashSet<Pair<SootMethod, Stmt>>(){{add(new Pair<>(scm, (Stmt) u));}});
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        saveConfigPropertyUsage(configPropertyUsage, "E:\\DCAnalyzer\\src\\main\\resources\\logs\\ConfigPropertyUsage_" + systemName + ".txt");

        return configPropertyUsage;
    }

    public static Pair<Set<String>, Set<String>> findServerClientCallSites(String systemName) throws IOException {
        Set<String> serverCreateSignatures = new TreeSet<>(Arrays.asList(RPCSignatures.SERVERS));
        Set<String> clientCreateSignatures = new TreeSet<>(Arrays.asList(RPCSignatures.CLIENTS));
        ArrayList<Pair<SootMethod, Stmt>> serverCreateCallSites = new ArrayList<>();
        ArrayList<Pair<SootMethod, Stmt>> clientCreateCallSites = new ArrayList<>();
        ArrayList<Pair<SootMethod, Stmt>> clientCallRPCSites = new ArrayList<>();
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            Set<SootField> instanceProxies = new HashSet<>();
            for (SootMethod sootMethod : sootClass.getMethods()) {
                Set<Local> localProxies = new HashSet<>();
                Map<Local, SootField> local2Fields = new HashMap<>();
                if (!sootMethod.hasActiveBody() || serverCreateSignatures.contains(sootMethod.getSignature()) || clientCreateSignatures.contains(sootMethod.getSignature())) continue;
                Body body = sootMethod.retrieveActiveBody();
                for (Unit unit : body.getUnits()) {
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof AssignStmt) {
                        AssignStmt assignStmt = (AssignStmt) stmt;
                        Value valueRight = assignStmt.getRightOp();
                        Value valueLeft = assignStmt.getLeftOp();
                        if (valueLeft instanceof Local && valueRight instanceof FieldRef) {
                            local2Fields.put(((Local) valueLeft), ((FieldRef) valueRight).getField());
                        }
                    }


                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        SootMethod invokeMethod = invokeExpr.getMethod();

                        // 抓取 proxy 对应的Local和Field，以及创建proxy或server的sites
                        if (serverCreateSignatures.contains(invokeMethod.getSignature())) {
                            serverCreateCallSites.add(new Pair<>(sootMethod, stmt));
                        } else if (clientCreateSignatures.contains(invokeMethod.getSignature())) {
                            clientCreateCallSites.add(new Pair<>(sootMethod, stmt));
                            if (stmt instanceof AssignStmt) {
                                AssignStmt assignStmt = (AssignStmt) stmt;
                                Value left = assignStmt.getLeftOp();
                                if (left instanceof Local) {
                                    localProxies.add((Local) left);
                                    if (local2Fields.containsKey((Local) left)) {
                                        instanceProxies.add(local2Fields.get((Local) left));
                                    }
                                } else if (left instanceof FieldRef) {
                                    instanceProxies.add(((FieldRef) left).getField());
                                }
                            }
                        }

                        // 抓取RPC call
                        if (invokeExpr instanceof InterfaceInvokeExpr) {
                            InterfaceInvokeExpr interfaceInvokeExpr = (InterfaceInvokeExpr) invokeExpr;
                            Value base = interfaceInvokeExpr.getBase();
                            if (base instanceof Local) {
                                if (localProxies.contains((Local) base)) {
                                    clientCallRPCSites.add(new Pair<>(sootMethod, stmt));
                                }
                            } else if (base instanceof FieldRef) {
                                SootField sootField = ((FieldRef) base).getField();
                                if (instanceProxies.contains(sootField)) {
                                    clientCallRPCSites.add(new Pair<>(sootMethod, stmt));
                                }
                            }
                        }
                    }

                }
            }
        }
        saveServerClientCallSite(serverCreateCallSites, clientCreateCallSites, clientCallRPCSites, systemName);
        return new Pair<>(serverCreateSignatures, clientCreateSignatures);
    }

    public static void StaticPairs() {
        /* A. Static Variable*/
        Log.i("[Start] Sever: Static variable");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        for (String impl : impls) {
            SootClass severImplClass = Scene.v().getSootClass(impl);
            Set<Analyzer.CallSite> callSites = proxy2CallSite.get(impl2proto.get(impl));
            Set<SameAccessSite> sameClsVarAccesses = findSameStaticVarAccessesWhole(severImplClass, callSites);
            sameClsVarAccessSites.addAll(sameClsVarAccesses);
        }
        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

        // 2. May-happen-in-parallel analysis
        Iterator<SameAccessSite> iterator = sameClsVarAccessSites.iterator();
        while (iterator.hasNext()) {
            SameAccessSite sameAccessSite = iterator.next();

            SootMethod sootMethod1 = sameAccessSite.accessSite1.sootMethod;
            if (!MethodOverrideChecker.isOverrideMethod(sootMethod1)) {
                iterator.remove();
            }

//            Set<Analyzer.CallSite> rpcCallSites1 = sameAccessSite.accessSite1.callerSites;
//            Set<Analyzer.CallSite> rpcCallSites2 = sameAccessSite.accessSite2.callerSites;
//
//            if (!((rpcCallSites1 != null && !rpcCallSites1.isEmpty()) || (rpcCallSites2 != null && !rpcCallSites2.isEmpty()))) {
//                iterator.remove();
//            }
        }
        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

        // 3. Read and write analysis
        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWholeStatic(sameClsVarAccessSites);
        Iterator<RWSameAccessSite> iterator2 = rwSameClsVarAccessSites.iterator();
        while (iterator2.hasNext()) {
            RWSameAccessSite rwSameAccessSite = iterator2.next();
            RWSameAccessSite.RWType rwType1 = rwSameAccessSite.rwType1;
            RWSameAccessSite.RWType rwType2 = rwSameAccessSite.rwType2;
            if (rwType1.equals(RWSameAccessSite.RWType.Read) && rwType2.equals(RWSameAccessSite.RWType.Read)) {
                iterator2.remove();
            } else if (rwType1.equals(RWSameAccessSite.RWType.Write) && rwType2.equals(RWSameAccessSite.RWType.Write)) {
                iterator2.remove();
            }
        }
        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
        sameClsVarAccessSites = null;

        // 4. Impact Analysis
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
        rwSameClsVarAccessSites = null;

        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
            Log.i("============================================================================================");
            Log.i("==VAR==", impactRW.sameAccessClsVar);
            Log.i("==Imp==", impactRW.sameAccessClsVar.getDeclaringClass().toString());
            // 有bug
            Log.i("====IHS====", impl2handlerSite.get(impactRW.sameAccessClsVar.getDeclaringClass().toString()));
            Log.i("==SYM==", impactRW.symptomSite.symptomType);
            Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
            Log.i("==RPC======", impactRW.symptomSite.RPCCallSites);
            Log.i("==MET==", impactRW.accessSite1.sootMethod);
            Log.i("==RCS======", impactRW.accessSite1.callerSites);
            Log.i("==MET==", impactRW.accessSite2.sootMethod);
            Log.i("===RCS=====", impactRW.accessSite2.callerSites);
        }
        impactRWSameAccessSites = null;
    }

    public static void instancePairs() {
        /* A. Instance Variable*/
        // Todo: 同一个RPC， 调用两次 client-client-server with same RPC
        Log.i("[Start] Sever: Instance variable");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();

        for (String impl : impls) {
            SootClass severImplClass = Scene.v().getSootClass(impl);
            Set<Analyzer.CallSite> callSites = proxy2CallSite.get(impl2proto.get(impl));
            Set<SameAccessSite> sameClsVarAccesses = findSameClsVarAccessesWhole(severImplClass, callSites);
            sameClsVarAccessSites.addAll(sameClsVarAccesses);
        }


        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});
        Set<String> funcNames = new HashSet<>();
        for (SameAccessSite sameAccessSite : sameClsVarAccessSites) {
            SootMethod sootMethod1 = sameAccessSite.accessSite1.sootMethod;
            SootMethod sootMethod2 = sameAccessSite.accessSite2.sootMethod;
            if (sootMethod1.hasActiveBody()) {
                for (Unit unit : sootMethod1.retrieveActiveBody().getUnits()) {
                    if (unit instanceof MonitorStmt) funcNames.add(sootMethod1.getSignature());
                }
            }
            if (sootMethod2.hasActiveBody()) {
                for (Unit unit : sootMethod2.retrieveActiveBody().getUnits()) {
                    if (unit instanceof MonitorStmt) funcNames.add(sootMethod2.getSignature());
                }
            }
        }

        // 2. May-happen-in-parallel analysis
        Iterator<SameAccessSite> iterator = sameClsVarAccessSites.iterator();
        while (iterator.hasNext()) {
            SameAccessSite sameAccessSite = iterator.next();

            SootMethod sootMethod1 = sameAccessSite.accessSite1.sootMethod;
            SootMethod sootMethod2 = sameAccessSite.accessSite2.sootMethod;
            if (!MethodOverrideChecker.isOverrideMethod(sootMethod1) && !MethodOverrideChecker.isOverrideMethod(sootMethod2)) {
                iterator.remove();
                continue;
            }

            Set<Analyzer.CallSite> rpcCallSites1 = sameAccessSite.accessSite1.callerSites;
            Set<Analyzer.CallSite> rpcCallSites2 = sameAccessSite.accessSite2.callerSites;
            if (!((rpcCallSites1 != null && !rpcCallSites1.isEmpty()) || (rpcCallSites2 != null && !rpcCallSites2.isEmpty()))) {
                iterator.remove();
            }
        }
        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

        // 3. Read and write analysis
        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWhole(sameClsVarAccessSites);
        Iterator<RWSameAccessSite> iterator2 = rwSameClsVarAccessSites.iterator();
        while (iterator2.hasNext()) {
            RWSameAccessSite rwSameAccessSite = iterator2.next();
            RWSameAccessSite.RWType rwType1 = rwSameAccessSite.rwType1;
            RWSameAccessSite.RWType rwType2 = rwSameAccessSite.rwType2;
            if (rwType1.equals(RWSameAccessSite.RWType.Read) && rwType2.equals(RWSameAccessSite.RWType.Read)) {
                iterator2.remove();
            } else if (rwType1.equals(RWSameAccessSite.RWType.Write) && rwType2.equals(RWSameAccessSite.RWType.Write)) {
                iterator2.remove();
            }
        }
        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
        sameClsVarAccessSites = null;

        Set<SootField> sameClsFields = new HashSet<>();
        for (RWSameAccessSite rwSameAccessSite : rwSameClsVarAccessSites) {
            Log.i("============================================================================================");
            Log.i("==VAR==", rwSameAccessSite.sameAccessClsVar);
            sameClsFields.add(rwSameAccessSite.sameAccessClsVar);

            Log.i("==Imp==", rwSameAccessSite.sameAccessClsVar.getDeclaringClass().toString());

            Log.i("==MET==", rwSameAccessSite.accessSite1.sootMethod);
            Log.i("==RCS======", rwSameAccessSite.accessSite1.callerSites);
            Log.i("==MET==", rwSameAccessSite.accessSite2.sootMethod);
            Log.i("===RCS=====", rwSameAccessSite.accessSite2.callerSites);
        }
        Log.i(sameClsFields.size());

//        // 4. Impact Analysis
//        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
//        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
//        rwSameClsVarAccessSites = null;
//
//        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
//            Log.i("============================================================================================");
//            Log.i("==VAR==", impactRW.sameAccessClsVar);
//            Log.i("==Imp==", impactRW.sameAccessClsVar.getDeclaringClass().toString());
//            // 有bug
//            Log.i("====IHS====", impl2handlerSite.get(impactRW.sameAccessClsVar.getDeclaringClass().toString()));
//            Log.i("==SYM==", impactRW.symptomSite.symptomType);
//            Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
//            Log.i("==RPC======", impactRW.symptomSite.RPCCallSites);
//            Log.i("==MET==", impactRW.accessSite1.sootMethod);
//            Log.i("==RCS======", impactRW.accessSite1.callerSites);
//            Log.i("==MET==", impactRW.accessSite2.sootMethod);
//            Log.i("===RCS=====", impactRW.accessSite2.callerSites);
//        }
//        impactRWSameAccessSites = null;
    }

    public static void localPairs() {
        Log.i("[Start] Sever: Local variable");
        return;
    }

    public static void start(String systemName, String systemPath) {
        Set<String> jars = new HashSet<>(getJars(systemPath));
        Log.i("[Start] target system = ", systemName);
        Log.i("[Res] jars.size() = ", jars.size());
        try {
            SootConfig.configrate(jars);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        CallGraphBuilder.build();
        cg = Scene.v().getCallGraph();

    }

    public static void loadRPCBridgeResults(String systemName) throws IOException {
        protos = analyzer.impls.keySet();
        Log.i(new Object[]{"[Res] protos.size() = ", protos.size()});

        impls = new HashSet<>();
        for (String protocol : analyzer.impls.keySet()) {
            impls.addAll(analyzer.impls.get(protocol));
        }
        Log.i(new Object[]{"[Res] impls.size() = ", impls.size()});

        protocol2impls = analyzer.impls;
        Log.i(new Object[]{"[Res] protocol2impls.size() = ", protocol2impls.size()});

        impl2proto = new HashMap<>();
        for (String protocol : analyzer.impls.keySet()) {
            for (String impl : analyzer.impls.get(protocol)) {
                impl2proto.put(impl, protocol);
            }
        }
        Log.i(new Object[]{"[Res] impl2proto.size() = ", impl2proto.size()});

        impl2handlerSite = new HashMap<>();
        for (String protocol : analyzer.newHandlerSite.keySet()) {
            for (Analyzer.CallSite callSite : analyzer.newHandlerSite.get(protocol)) {
                if (impl2handlerSite.containsKey(callSite.getImplType().toString())) {
                    impl2handlerSite.get(callSite.getImplType().toString()).add(callSite);
                } else {
                    impl2handlerSite.put(callSite.getImplType().toString(), new HashSet<Analyzer.CallSite>(){{add(callSite);}});
                }

            }
        }
        Log.i(new Object[]{"[Res] impl2handlerSite.size() = ", impl2handlerSite.size()});


        proxy2CreateCallSite = new HashMap<>();
        for (String protocol : analyzer.proxyConstructSite.keySet()) {
            for (Analyzer.CallSite callSite : analyzer.proxyConstructSite.get(protocol)) {
                if (proxy2CreateCallSite.containsKey(callSite.getImplType().toString())) {
                    proxy2CreateCallSite.get(callSite.getImplType().toString()).add(callSite);
                } else {
                    proxy2CreateCallSite.put(callSite.getImplType().toString(), new HashSet<Analyzer.CallSite>(){{add(callSite);}});
                }

            }
        }
        Log.i(new Object[]{"[Res] proxyCallSite.size() = ", proxy2CreateCallSite.size()});

        proxy2CallSite = new HashMap<>();
        for (String protocol : analyzer.proxyRPCSite.keySet()) {
            for (Analyzer.CallSite callSite : analyzer.proxyRPCSite.get(protocol)) {
                if (proxy2CallSite.containsKey(callSite.getImplType().toString())) {
                    proxy2CallSite.get(callSite.getImplType().toString()).add(callSite);
                } else {
                    proxy2CallSite.put(callSite.getImplType().toString(), new HashSet<Analyzer.CallSite>(){{add(callSite);}});
                }
            }
        }
        Log.i(new Object[]{"[Res] proxy2CallSite.size() = ", proxy2CallSite.size()});

        logRPCBridgeResults("E:\\DCAnalyzer\\src\\main\\resources\\logs\\RPCBridge_"+systemName+".txt");
    }

    public static void logRPCBridgeResults(String filePath) throws IOException {
        File file = new File(filePath);
        if (file.exists() && file.length() > 0) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            for (String impl : impl2handlerSite.keySet()) {
                writer.println("============================");

                String proto = impl2proto.get(impl);
                writer.println("Protocol: " + proto + "\nImplementation: " + impl);

                writer.println("Server handler site: -----------------------------");
                for (Analyzer.CallSite callSite : impl2handlerSite.get(impl)) {
                    writer.println(callSite.getCallMethod() + " " + callSite.getCallStmt());
                }

                writer.println("Client proxy site-----------------------------");
                for (Analyzer.CallSite callSite : proxy2CreateCallSite.get(proto)) {
                    writer.println(callSite.getCallMethod() + " " + callSite.getCallStmt());
                }

                writer.println("Client rpc site-----------------------------");
                for (Analyzer.CallSite callSite : proxy2CallSite.get(proto)) {
                    writer.println(callSite.getCallMethod() + " " + callSite.getCallStmt());
                }

                writer.println("============================");
                writer.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception appropriately for your application
        }
    }

    // 将 configPropertyUsage 保存到文件
    public static void saveConfigPropertyUsage(Map<StringConstant, Set<Pair<SootMethod, Stmt>>> configPropertyUsage, String filePath) throws IOException {
        if (Files.exists(Paths.get(filePath))) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<StringConstant, Set<Pair<SootMethod, Stmt>>> entry : configPropertyUsage.entrySet()) {
                StringConstant property = entry.getKey();
                Set<Pair<SootMethod, Stmt>> methodStmtPairs = entry.getValue();

                writer.write("Property: " + property.value + "\n");
                for (Pair<SootMethod, Stmt> pair : methodStmtPairs) {
                    SootMethod method = pair.getKey();
                    Stmt stmt = pair.getValue();
                    writer.write("  Method: " + method.getSignature() + ", Stmt: " + stmt.toString() + "\n");
                }
                writer.write("===============================================\n");
            }
        }
    }

    // 将 addressPropertyUsage 保存到文件
    public static void saveAddressPropertyUsage(Map<AddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage, String filePath) throws IOException {
        if (Files.exists(Paths.get(filePath))) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<AddressExtraction.PropertyHelper, Set<Pair<SootMethod, Stmt>>> entry : addressPropertyUsage.entrySet()) {
                AddressExtraction.PropertyHelper propertyHelper = entry.getKey();
                Set<Pair<SootMethod, Stmt>> methodStmtPairs = entry.getValue();

                writer.write("Address Property: " + propertyHelper.toString() + "\n");
                for (Pair<SootMethod, Stmt> pair : methodStmtPairs) {
                    SootMethod method = pair.getKey();
                    Stmt stmt = pair.getValue();
                    writer.write("  Method: " + method.getSignature() + ", Stmt: " + stmt.toString() + "\n");
                }
                writer.write("===============================================\n");
            }
        }
    }

    public static void saveFilePathPropertyUsage(Map<PropertyHelper, Set<Pair<SootMethod, Stmt>>> addressPropertyUsage, String filePath) throws IOException {
        if (Files.exists(Paths.get(filePath))) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<PropertyHelper, Set<Pair<SootMethod, Stmt>>> entry : addressPropertyUsage.entrySet()) {
                PropertyHelper propertyHelper = entry.getKey();
                Set<Pair<SootMethod, Stmt>> methodStmtPairs = entry.getValue();

                writer.write("File Path Property: " + propertyHelper.toString() + "\n");
                for (Pair<SootMethod, Stmt> pair : methodStmtPairs) {
                    SootMethod method = pair.getKey();
                    Stmt stmt = pair.getValue();
                    writer.write("  Method: " + method.getSignature() + ", Stmt: " + stmt.toString() + "\n");
                }
                writer.write("===============================================\n");
            }
        }
    }

    public static void saveServerClientCallSite(ArrayList<Pair<SootMethod, Stmt>> serverCalls, ArrayList<Pair<SootMethod, Stmt>> clientCalls, ArrayList<Pair<SootMethod, Stmt>> clientCallRPCSites, String systemName) throws IOException {
        String filePath = "E:\\DCAnalyzer\\src\\main\\resources\\logs\\RPCCallSites_"+systemName+".txt";

        if (Files.exists(Paths.get(filePath))) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

            // 写入 Server 调用点
            writer.write("=== RPC Server Creation Calls ===\n");
            for (Pair<SootMethod, Stmt> pair : serverCalls) {
                writer.write(formatEntry("Server", pair));
                writer.newLine();
            }

            // 写入 Client 调用点
            writer.write("\n=== RPC Client Creation Calls ===\n");
            for (Pair<SootMethod, Stmt> pair : clientCalls) {
                writer.write(formatEntry("Client", pair));
                writer.newLine();
            }

            // 写入 RPC 调用点
            writer.write("\n=== RPC Calls ===\n");
            for (Pair<SootMethod, Stmt> pair : clientCallRPCSites) {
                writer.write(formatEntry("RPC", pair));
                writer.newLine();
            }

        }
    }

    private static String formatEntry(String type, Pair<SootMethod, Stmt> pair) {
        SootMethod caller = pair.getKey();
        Stmt stmt = pair.getValue();

        return String.format(
                        "[Type] %s\n" +
                        "[Caller] %s.%s\n" +
                        "[Statement] %s\n" +
                        "----------------------------",
                type,
                caller.getDeclaringClass().getName(), caller.getSubSignature(),
                stmt
        );
    }
}
