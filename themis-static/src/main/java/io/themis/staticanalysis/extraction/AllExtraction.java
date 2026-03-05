package indi.dc.extraction;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.analysis.CallGraphBuilder;
import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.entity.Adapter;
import cn.ac.ios.bridge.entity.HadoopAdapter;
import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.address.AddressExtraction;
import indi.dc.extraction.address.FileAddressExtraction;
import indi.dc.extraction.sameaccess.SameAccessSite;
import indi.dc.extraction.readwrite.RWSameAccessSite;
import indi.dc.extraction.impact.ImpactAnalysis;
import indi.dc.extraction.impact.ImpactRWSameAccessSite;
import indi.dc.extraction.utils.MethodOverrideChecker;
import indi.dc.extraction.utils.RPCSignatures;
import indi.dc.extraction.utils.Tuple;
import indi.dc.extraction.utils.Pair;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import static indi.dc.extraction.address.AddressExtraction.findAddressPortProperties;
import static indi.dc.extraction.address.FileAddressExtraction.*;
import static indi.dc.extraction.address.FileAddressPropertyChecker.checkIfFilePathProperty;
import static indi.dc.extraction.address.PropertyChecker.isIPAddress;
import static indi.dc.extraction.readwrite.ReadWriteAnalysis.getReadWriteWholeStatic;
import static indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis.findSameClsVarAccessesWhole;
import static indi.dc.extraction.readwrite.ReadWriteAnalysis.getReadWriteWhole;
import static indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis.findSameStaticVarAccessesWhole;
import static indi.dc.extraction.utils.Utils.getJars;
import static indi.dc.extraction.utils.Utils.isSubclassOrDescendant;


public class AllExtraction {
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

        String systemName = args.length > 0 ? args[0] : "all";
        String systemNM = "";
        if (systemName.equals("mapreduce") || systemName.equals("yarn") || systemName.equals("hdfs") || systemName.equals("hadoop") || systemName.equals("all")) {
            systemNM = systemName;
            if (systemName.equals("all")) systemNM = "all";
            systemName = "hadoop";
        }
        else {
            systemNM = systemName;
        }

        if (systemName.equals("motivating_example")) {
            systemName = "hadoopSmall_3.4.1";
            systemNM = "hadoop";
        }
        // Start Soot
//        String systemName = "Hadoop_3.4.1";

        String systemPath = "E:\\DCAnalyzer\\src\\main\\resources\\"+systemName+"\\sys_jars";



        start(systemName, systemPath, systemNM);


        // RPCBridge Analysis
        if (!args[0].equals("motivating_example")) {
            // analyzer.start();
            // loadRPCBridgeResults(systemName);
        }


        // for (String impl : proxy2CallSite.keySet()) {
        //     System.out.println("RPC Server: " + impl);
        //     SootClass sootClass = Scene.v().getSootClassUnsafe(impl);
        //     int rpcNum = 0;
        //     if (sootClass != null) {
        //         rpcNum = sootClass.getMethods().size();
        //     }

        //     Set<SootMethod> rpcCalled = new HashSet<>();
        //     for (Analyzer.CallSite callSite : proxy2CallSite.get(impl)) {
        //         System.out.println("CallSite: " + callSite.getCallStmt().getInvokeExpr().getMethod());
        //         rpcCalled.add(callSite.getCallStmt().getInvokeExpr().getMethod());
        //     }
        //     System.out.printf("%d, %d\n", rpcNum, rpcCalled.size());
        //     System.out.println("===================================================================");
        // }

//        for (String impl : impls) {
//            SootClass sootClass = Scene.v().getSootClassUnsafe(impl);
//            if (sootClass != null) {
//                System.out.println("RPC Server: " + sootClass);
//                Set<Analyzer.CallSite> callSites = proxy2CallSite.get(impl);
//                if (callSites != null) {
//                    for (Analyzer.CallSite callSite : callSites) {
//                        System.out.println("CallSite: " + callSite.getCallStmt().getInvokeExpr().getMethod());
//                    }
//                }
//            }
//            System.out.println("===================================================================");
//        }

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

        // atomicity public caller
        try {
            callerAccessAtomicity();
            // here
            callerOneClassStatics();
            instancePairs();
//       StaticPairs();
        } catch (Exception e) {
        }

       if (args.length > 0) {
           printCacheResults(systemNM);
           return;
        }

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

        saveFilePathPropertyUsage(propertyUsage, "src/main/resources/logs/PropertyUsageMulti_" + systemName + ".txt");
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

        saveAddressPropertyUsage(addressPropertyUsage, "src/main/resources/logs/AddressPropertyUsage_" + systemName + ".txt");
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

        saveFilePathPropertyUsage(addressPropertyUsage, "src/main/resources/logs/FilePathPropertyUsage_" + systemName + ".txt");
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

        saveConfigPropertyUsage(configPropertyUsage, "src/main/resources/logs/ConfigPropertyUsage_" + systemName + ".txt");

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
//        Log.i("[Start] Sever: Static variable");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        for (String impl : impls) {
            SootClass severImplClass = Scene.v().getSootClass(impl);
            Set<Analyzer.CallSite> callSites = proxy2CallSite.get(impl2proto.get(impl));
            Set<SameAccessSite> sameClsVarAccesses = findSameStaticVarAccessesWhole(severImplClass, callSites);
            sameClsVarAccessSites.addAll(sameClsVarAccesses);
        }
//        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

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
//        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

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
//        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
        sameClsVarAccessSites = null;

        // 4. Impact Analysis
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
//        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
        rwSameClsVarAccessSites = null;

        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
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
        }
        impactRWSameAccessSites = null;
    }

    /** 收集：本类中访问字段 f 的方法 -> 访问类型(读/写/两者)；过滤名字含 init/start 的方法 */
    private static Map<SootMethod, AccessKind> findAccessorsOfFieldInClass(SootClass clazz, SootField f) {
        Map<SootMethod, AccessKind> res = new HashMap<>();
        for (SootMethod m : clazz.getMethods()) {
            if (!isAnalyzable(m) || !isNameAllowed(m)) continue;
            Body b; try { b = m.retrieveActiveBody(); } catch (Exception ex) { continue; }

            AccessKind kind = null;
            for (Unit u : b.getUnits()) {
                if (!(u instanceof Stmt)) continue;
                Stmt s = (Stmt) u;

                boolean reads = false, writes = false;

                // 写：赋值到字段  e.g., x.f = rhs;
                if (s instanceof AssignStmt) {
                    Value l = ((AssignStmt) s).getLeftOp();
                    if (l instanceof FieldRef && ((FieldRef) l).getField().equals(f)) {
                        writes = true;
                    }
                }
                // 读：字段出现在 use boxes 或者右值中
                for (ValueBox vb : s.getUseBoxes()) {
                    Value v = vb.getValue();
                    if (v instanceof FieldRef && ((FieldRef) v).getField().equals(f)) {
                        reads = true;
                    }
                }
                // 有些情况下 def/use 汇总更稳妥（比如复杂表达式）
                for (ValueBox vb : s.getUseAndDefBoxes()) {
                    Value v = vb.getValue();
                    if (v instanceof FieldRef && ((FieldRef) v).getField().equals(f)) {
                        // 如果已经标为写，保持；否则视为读
                        if (!writes) reads = true;
                    }
                }

                if (reads || writes) {
                    AccessKind cur = writes ? (reads ? AccessKind.BOTH : AccessKind.WRITE) : AccessKind.READ;
                    kind = AccessKind.merge(kind, cur);
                }
            }
            if (kind != null) res.put(m, kind);
        }
        return res;
    }

    public static void callerOneClassStatics() {
        Set<SootClass> sootClasses = new HashSet<>();

        for (String impl : impl2handlerSite.keySet()) {
            sootClasses.add(Scene.v().getSootClass(impl));
            for (Analyzer.CallSite callSite : impl2handlerSite.get(impl)) {
                sootClasses.add(callSite.getCallMethod().getDeclaringClass());
            }
        }

        int all_num = 0;
        for (SootClass sootClass : sootClasses) {
            Map<SootMethod, Set<SootMethod>> res = buildIntraClassCallerMap(sootClass, cg, true, true);
            if (res.isEmpty()) continue;
//            Log.i(sootClass);
//            System.out.println("Methods with at least one intra-class caller: " + res.keySet().size());
            all_num += res.keySet().size();
            for (Map.Entry<SootMethod, Set<SootMethod>> e : res.entrySet()) {
                if (!e.getValue().isEmpty()) {
//                    System.out.println(e.getKey().getName());
                    for (SootMethod c : e.getValue()) {
//                        System.out.println("  <- " + c.getName());
                    }
                }
            }
        }
//        Log.i("All class caller: " + all_num);
    }

    public static Map<SootMethod, Set<SootMethod>> buildIntraClassCallerMap(
            SootClass clazz,
            CallGraph cg,
            boolean excludeSelfRecursion,
            boolean ignoreInitStart) {

        Map<SootMethod, Set<SootMethod>> result = new LinkedHashMap<>();
        int withIntraCaller = 0;

        for (SootMethod callee : clazz.getMethods()) {
            // 过滤 callee：仅分析具体且非库方法；可选忽略 init/start 名字
            if (!callee.hasActiveBody()) continue;
            if (ignoreInitStart) {
                String n = callee.getName().toLowerCase(Locale.ROOT);
                if (n.contains("init") || n.contains("start") || n.contains("create") || n.contains("access")) continue;
            }

            Set<SootMethod> callers = new LinkedHashSet<>();
            Iterator<Edge> it = cg.edgesInto(callee);
            while (it.hasNext()) {
                Edge e = it.next();

                // 合并“真实代码调用”判定（STATIC/SPECIAL/VIRTUAL/INTERFACE）
                Kind k = e.kind();
                if (!(k == Kind.STATIC || k == Kind.SPECIAL || k == Kind.VIRTUAL || k == Kind.INTERFACE)) {
                    continue;
                }

                SootMethod caller = e.src();
                if (caller == null || !caller.hasActiveBody()) continue;
                if (!caller.getDeclaringClass().equals(clazz)) continue; // 仅同类内 caller

                if (ignoreInitStart) {
                    String cn = caller.getName().toLowerCase(Locale.ROOT);
                    if (cn.contains("init") || cn.contains("start")) continue;
                }

                if (excludeSelfRecursion && caller.equals(callee)) continue;

                callers.add(caller);
            }

            result.put(callee, callers);
        }

        // 打印统计

        return result;
    }

    /** 获取方法 m 的“同类内”直接调用者（已过滤非法边 & 名字过滤） */
    private static Set<SootMethod> getIntraClassCallers(CallGraph cg, SootMethod m, SootClass clazz) {
        Set<SootMethod> callers = new HashSet<>();
        Iterator<Edge> it = cg.edgesInto(m);
        while (it.hasNext()) {
            Edge e = it.next();
            SootMethod src = e.src();
            if (src != null
                    && src.hasActiveBody()
                    && src.getDeclaringClass().equals(clazz)
                    && isNameAllowed(src)
                    && isRealCodeSite(e)) {
                callers.add(src);
            }
        }
        return callers;
    }

    private static boolean isRealCodeSite(Edge e) {
        Kind k = e.kind();
        return k == Kind.STATIC || k == Kind.SPECIAL || k == Kind.VIRTUAL || k == Kind.INTERFACE;
    }

    private static boolean isAnalyzable(SootMethod m) {
        return m.isConcrete() && !m.isJavaLibraryMethod();
    }

    /** 名字过滤：忽略含有 init / start 的方法（大小写不敏感） */
    private static boolean isNameAllowed(SootMethod m) {
        String n = m.getName().toLowerCase(Locale.ROOT);
        return !(n.contains("init") || n.contains("start"));
    }

    private static List<Tuple> dedup(List<Tuple> tuples) {
        Set<String> seen = new HashSet<>();
        List<Tuple> out = new ArrayList<>();
        for (Tuple t : tuples) {
            String key = t.func1.getSignature()+"|"+t.func4.getSignature()+"|"+t.func2.getSignature()+
                    "|"+t.func3.getSignature()+"|"+t.classVar.getSignature();
            if (seen.add(key)) out.add(t);
        }
        return out;
    }

    private static boolean shouldIgnoreField(SootField field) {
        if (field.getSignature().contains("org.slf4j")) return true;
        if (field.isFinal()) return true;
        if (isAllUpperCase(field.getName())) return true;
        return false;
    }

    private static boolean isAllUpperCase(String s) {
        boolean hasLetter = false;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter; // 至少有一个字母，并且所有字母都是大写
    }


    public enum AccessKind { READ, WRITE, BOTH;
        boolean isReadOnly() { return this == READ; }
        static AccessKind merge(AccessKind a, AccessKind b){
            if (a == null) return b;
            if (b == null) return a;
            if (a == b) return a;
            return BOTH;
        }
    }

    public static List<Tuple> findTuples(SootClass clazz, CallGraph cg) {
        // 1) 对每个字段：收集“本类中访问它的方法 及 访问类型(读/写/两者)”
        Map<SootField, Map<SootMethod, AccessKind>> field2Access = new HashMap<>();
        for (SootField f : clazz.getFields()) {
            if (shouldIgnoreField(f)) continue;
            Map<SootMethod, AccessKind> acc = findAccessorsOfFieldInClass(clazz, f);
            // 至少 3 个不同方法访问同一字段，才可能形成三元组
            if (acc.keySet().size() >= 3) field2Access.put(f, acc);
        }
        if (field2Access.isEmpty()) return Collections.emptyList();

        // 2) 预先计算每个方法的“本类内 callers”（同样过滤掉名字含 init/start 的方法）
        Map<SootMethod, Set<SootMethod>> method2Callers = new HashMap<>();
        for (SootMethod m : clazz.getMethods()) {
            if (!isAnalyzable(m)) continue;
            method2Callers.put(m, getIntraClassCallers(cg, m, clazz));
        }

        // 3) 枚举满足条件的组合
        List<Tuple> results = new ArrayList<>();
        for (Map.Entry<SootField, Map<SootMethod, AccessKind>> e : field2Access.entrySet()) {
            SootField var = e.getKey();
            List<SootMethod> acc = new ArrayList<>(e.getValue().keySet());

            for (int i = 0; i < acc.size(); i++) {
                SootMethod func2 = acc.get(i);
                for (int j = i + 1; j < acc.size(); j++) {
                    SootMethod func3 = acc.get(j);

                    Set<SootMethod> callers2 = method2Callers.getOrDefault(func2, Collections.emptySet());
                    Set<SootMethod> callers3 = method2Callers.getOrDefault(func3, Collections.emptySet());
                    if (callers2.isEmpty() || callers3.isEmpty()) continue;

                    Set<SootMethod> common = new HashSet<>(callers2);
                    common.retainAll(callers3);
                    if (common.isEmpty()) continue;

                    for (SootMethod func4 : common) {
                        // 过滤 func4 名字含 init/start
                        if (!isNameAllowed(func4)) continue;

                        for (SootMethod func1 : acc) {
                            if (func1.equals(func2) || func1.equals(func3) || func1.equals(func4)) continue;

                            // —— 规则2：丢弃三个全是“只读”的组合 ——
                            AccessKind k1 = e.getValue().get(func1);
                            AccessKind k2 = e.getValue().get(func2);
                            AccessKind k3 = e.getValue().get(func3);
                            boolean allReadOnly = k1.isReadOnly() && k2.isReadOnly() && k3.isReadOnly();
                            if (allReadOnly) continue;

                            results.add(new Tuple(func1, func4, func2, func3, var));
                        }
                    }
                }
            }
        }
        return dedup(results);
    }

    public static void callerAccessAtomicity() {
        Set<SootClass> sootClasses = new HashSet<>();

        for (String impl : impl2handlerSite.keySet()) {
            sootClasses.add(Scene.v().getSootClass(impl));
            for (Analyzer.CallSite callSite : impl2handlerSite.get(impl)) {
                sootClasses.add(callSite.getCallMethod().getDeclaringClass());
            }
        }

        for (SootClass sootClass : sootClasses) {
            for (Tuple tuple : findTuples(sootClass, cg)) {
//                Log.i(tuple);
            }
        }



    }

    public static void instancePairs() {
        /* A. Instance Variable*/
        // Todo: 同一个RPC， 调用两次 client-client-server with same RPC
//        Log.i("[Start] Sever: Instance variable");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();

        for (String impl : impls) {
            SootClass severImplClass = Scene.v().getSootClass(impl);
            Set<Analyzer.CallSite> callSites = proxy2CallSite.get(impl2proto.get(impl));
            Set<SameAccessSite> sameClsVarAccesses = findSameClsVarAccessesWhole(severImplClass, callSites);
            sameClsVarAccessSites.addAll(sameClsVarAccesses);
        }


//        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});
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
//        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

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
//        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
        sameClsVarAccessSites = null;

        Map<String, Set<RWSameAccessSite>> moduleClasses = new HashMap<>();
        moduleClasses.put("yarn", new HashSet<>());
        moduleClasses.put("mapreduce", new HashSet<>());
        moduleClasses.put("hdfs", new HashSet<>());
        moduleClasses.put("common", new HashSet<>());

        for (RWSameAccessSite rwSameAccessSite : rwSameClsVarAccessSites) {
            String sootField = rwSameAccessSite.sameAccessClsVar.getDeclaringClass().toString();
            if (sootField.contains("yarn")) moduleClasses.get("yarn").add(rwSameAccessSite);
            else if (sootField.contains("mapreduce") || sootField.contains("mapred")) moduleClasses.get("mapreduce").add(rwSameAccessSite);
            else if (sootField.contains("hdfs")) moduleClasses.get("hdfs").add(rwSameAccessSite);
            else moduleClasses.get("common").add(rwSameAccessSite);
        }

        for (Map.Entry<String, Set<RWSameAccessSite>> entry : moduleClasses.entrySet()) {
//            Log.i(entry.getKey()+"|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
//            Log.i(entry.getValue().size()+"||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            for (RWSameAccessSite rw : entry.getValue()) {
//                Log.i("============================================================================================");
//                Log.i("==VAR==", rw.sameAccessClsVar);
//                Log.i("==Imp==", rw.sameAccessClsVar.getDeclaringClass().toString());
//                Log.i("====IHS====", impl2handlerSite.get(rw.sameAccessClsVar.getDeclaringClass().toString()));
//                Log.i("==MET==", rw.accessSite1.sootMethod);
//                Log.i("==RCS======", rw.accessSite1.callerSites);
//                Log.i("==ACS======",rw.accessSite1.accessSites.size());
//                Log.i("==MET==", rw.accessSite2.sootMethod);
//                Log.i("===RCS=====", rw.accessSite2.callerSites);
//                Log.i("==ACS======", rw.accessSite2.accessSites.size());
            }
        }
        System.exit(1);

        // 4. Impact Analysis
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
//        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
        rwSameClsVarAccessSites = null;

        Map<String, Set<ImpactRWSameAccessSite>> moduleClassesImpact = new HashMap<>();
        moduleClassesImpact.put("yarn", new HashSet<>());
        moduleClassesImpact.put("mapreduce", new HashSet<>());
        moduleClassesImpact.put("hdfs", new HashSet<>());
        moduleClassesImpact.put("common", new HashSet<>());

        for (ImpactRWSameAccessSite impactRWSameAccessSite : impactRWSameAccessSites) {
            String sootField = impactRWSameAccessSite.sameAccessClsVar.getDeclaringClass().toString();
            if (sootField.contains("yarn")) moduleClassesImpact.get("yarn").add(impactRWSameAccessSite);
            else if (sootField.contains("mapreduce") || sootField.contains("mapred")) moduleClassesImpact.get("mapreduce").add(impactRWSameAccessSite);
            else if (sootField.contains("hdfs")) moduleClassesImpact.get("hdfs").add(impactRWSameAccessSite);
            else moduleClassesImpact.get("common").add(impactRWSameAccessSite);
        }

        for (Map.Entry<String, Set<ImpactRWSameAccessSite>> entry : moduleClassesImpact.entrySet()) {
//            Log.i(entry.getKey()+"|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
//            Log.i(entry.getValue().size()+"||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            for (ImpactRWSameAccessSite impactRW : entry.getValue()) {
//                Log.i("============================================================================================");
//                Log.i("==VAR==", impactRW.sameAccessClsVar);
//                Log.i("==Imp==", impactRW.sameAccessClsVar.getDeclaringClass().toString());
                // 有bug
//                Log.i("====IHS====", impl2handlerSite.get(impactRW.sameAccessClsVar.getDeclaringClass().toString()));
//                Log.i("==SYM==", impactRW.symptomSite.symptomType);
//                Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
//                Log.i("==RPC======", impactRW.symptomSite.RPCCallSites);
//                Log.i("==MET==", impactRW.accessSite1.sootMethod);
//                Log.i("==RCS======", impactRW.accessSite1.callerSites);
//                Log.i("==ACS======",impactRW.accessSite1.accessSites.size());
//                Log.i("==MET==", impactRW.accessSite2.sootMethod);
//                Log.i("===RCS=====", impactRW.accessSite2.callerSites);
//                Log.i("==ACS======", impactRW.accessSite2.accessSites.size());
            }
        }

        impactRWSameAccessSites = null;
    }

    public static void localPairs() {
        Log.i("[Start] Sever: Local variable");
        return;
    }

    public static void start(String systemName, String systemPath, String name) {
        Set<String> jars = new HashSet<>(getJars(systemPath));
        Log.i("[Start] target system = ", name);
//        Log.i("[Res] jars.size() = ", jars.size());
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
//        Log.i(new Object[]{"[Res] protos.size() = ", protos.size()});

        impls = new HashSet<>();
        for (String protocol : analyzer.impls.keySet()) {
            impls.addAll(analyzer.impls.get(protocol));
        }
//        Log.i(new Object[]{"[Res] impls.size() = ", impls.size()});

        protocol2impls = analyzer.impls;
//        Log.i(new Object[]{"[Res] protocol2impls.size() = ", protocol2impls.size()});

        impl2proto = new HashMap<>();
        for (String protocol : analyzer.impls.keySet()) {
            for (String impl : analyzer.impls.get(protocol)) {
                impl2proto.put(impl, protocol);
            }
        }
//        Log.i(new Object[]{"[Res] impl2proto.size() = ", impl2proto.size()});

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
//        Log.i(new Object[]{"[Res] impl2handlerSite.size() = ", impl2handlerSite.size()});


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
//        Log.i(new Object[]{"[Res] proxyCallSite.size() = ", proxy2CreateCallSite.size()});

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
//        Log.i(new Object[]{"[Res] proxy2CallSite.size() = ", proxy2CallSite.size()});

        logRPCBridgeResults("src/main/resources/logs/RPCBridge_"+systemName+".txt");
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
        String filePath = "src/main/resources/logs/RPCCallSites_"+systemName+".txt";

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


    public static void printCacheResults(String systemName) throws IOException {
        String jsonFilePath;
        boolean isKnownFile = "known".equalsIgnoreCase(systemName);
        
        if (isKnownFile) {
            jsonFilePath = "E:\\DCAnalyzer\\src\\main\\resources\\cache_known.json";
        } else {
            jsonFilePath = "E:\\DCAnalyzer\\src\\main\\resources\\cache_result.json";
        }
        
        File jsonFile = new File(jsonFilePath);

        if (!jsonFile.exists()) {
            return;
        }

        Gson gson = new Gson();
        JsonObject jsonObject;
        try (FileReader reader = new FileReader(jsonFile)) {
            jsonObject = gson.fromJson(reader, JsonObject.class);
        }

        Set<String> targetSysTags = new HashSet<>();
        if (systemName != null && !systemName.equalsIgnoreCase("all") && !systemName.isEmpty()) {
            String lowerSystemName = systemName.toLowerCase();
            if (lowerSystemName.contains("hadoop")) {
                targetSysTags.add("MapReduce");
                targetSysTags.add("Yarn");
                targetSysTags.add("HDFS");
                targetSysTags.add("hadoop");
            } else {
                targetSysTags.add(systemName);
                if (systemName.length() > 0) {
                    String capitalized = systemName.substring(0, 1).toUpperCase() + 
                                       (systemName.length() > 1 ? systemName.substring(1) : "");
                    targetSysTags.add(capitalized);
                }
            }
        }

        Map<String, Map<String, Integer>> tableData = new LinkedHashMap<>();
        Set<String> allViolationTypes = new TreeSet<>();
        Set<String> allSysTags = new TreeSet<>();
        
        List<Pair<String, JsonObject>> violationsToPrint = new ArrayList<>();

        for (String key : jsonObject.keySet()) {
            JsonObject violation = jsonObject.getAsJsonObject(key);
            
            String sysTag;
            if (isKnownFile) {
                int dashIndex = key.indexOf('-');
                if (dashIndex > 0) {
                    sysTag = key.substring(0, dashIndex);
                } else {
                    sysTag = "Unknown";
                }
            } else {
                sysTag = violation.has("sys_tag") ? violation.get("sys_tag").getAsString() : "Unknown";
                if (sysTag.isEmpty()) {
                    sysTag = "Unknown";
                }
            }
            
            String violationType = violation.has("violation_type") ? violation.get("violation_type").getAsString() : "Unknown";
            if (violationType.isEmpty()) {
                violationType = "Unknown";
            }
            
            boolean shouldInclude;
            if (isKnownFile) {
                shouldInclude = true;
            } else if (systemName == null || systemName.isEmpty() || systemName.equalsIgnoreCase("all")) {
                shouldInclude = true; 
            } else {
                String lowerSysTag = sysTag.toLowerCase();
                String lowerSystemName = systemName.toLowerCase();
                
                if (lowerSystemName.contains("hadoop")) {
                    shouldInclude = lowerSysTag.contains("mapreduce") || 
                                   lowerSysTag.contains("yarn") || 
                                   lowerSysTag.contains("hdfs") ||
                                   lowerSysTag.contains("hadoop");
                } else {
                    shouldInclude = lowerSysTag.contains(lowerSystemName) || 
                                   targetSysTags.contains(sysTag);
                }
            }
            
            if (shouldInclude) {
                allSysTags.add(sysTag);
                allViolationTypes.add(violationType);
                
                tableData.putIfAbsent(sysTag, new HashMap<>());
                Map<String, Integer> row = tableData.get(sysTag);
                row.put(violationType, row.getOrDefault(violationType, 0) + 1);
                
                violationsToPrint.add(new Pair<>(key, violation));
            }
        }

        printViolationDetails(violationsToPrint, systemName);
        
        printTable(tableData, allSysTags, allViolationTypes, systemName);
    }


    private static void printViolationDetails(List<Pair<String, JsonObject>> violations, String systemName) {
        if (violations.isEmpty()) {
            return;
        }

        boolean needRenumber = systemName != null && !systemName.isEmpty() && 
                               !systemName.equalsIgnoreCase("all") && 
                               !systemName.equalsIgnoreCase("known");
        
        System.out.println("\n========================================");
        System.out.println("Violation Details:");
        System.out.println("========================================");
        
        int currentId = 1;
        for (Pair<String, JsonObject> violationPair : violations) {
            String originalKey = violationPair.getKey();
            JsonObject violation = violationPair.getValue();
            
            if (needRenumber) {
                System.out.println("violation" + currentId + ":");
                currentId++;
            } else {
                System.out.println(originalKey + ":");
            }
            
            if (violation.has("common_access_variable")) {
                System.out.println("  common_access_variable: " + violation.get("common_access_variable").getAsString());
            }
            
            if (violation.has("root_cause_function")) {
                JsonArray functions = violation.getAsJsonArray("root_cause_function");
                System.out.print("  root_cause_function: [");
                for (int i = 0; i < functions.size(); i++) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(functions.get(i).getAsString());
                }
                System.out.println("]");
            }
            
            if (violation.has("symptom")) {
                JsonObject symptom = violation.getAsJsonObject("symptom");
                String symptomType = symptom.has("type") ? symptom.get("type").getAsString() : "unknown";
                System.out.println("  symptom: " + symptomType);
            }
            
            if (violation.has("violation_type")) {
                System.out.println("  violation_type: " + violation.get("violation_type").getAsString());
            }
            
            String sysTagToPrint;
            if (violation.has("sys_tag")) {
                sysTagToPrint = violation.get("sys_tag").getAsString();
            } else {
                int dashIndex = originalKey.indexOf('-');
                if (dashIndex > 0) {
                    sysTagToPrint = originalKey.substring(0, dashIndex);
                } else {
                    sysTagToPrint = "Unknown";
                }
            }
            System.out.println("  sys_tag: " + sysTagToPrint);
            
            System.out.println();
        }
        System.out.println("========================================");
    }

    private static void printTable(Map<String, Map<String, Integer>> tableData, 
                                   Set<String> allSysTags, 
                                   Set<String> allViolationTypes,
                                   String systemName) {

        int systemNameWidth = 15;
        for (String sysTag : allSysTags) {
            systemNameWidth = Math.max(systemNameWidth, sysTag.length() + 2);
        }
        systemNameWidth = Math.max(systemNameWidth, "System".length() + 2);

        int columnWidth = 8;
        for (String vType : allViolationTypes) {
            columnWidth = Math.max(columnWidth, vType.length() + 2);
        }
        columnWidth = Math.max(columnWidth, "Total".length() + 2);

        System.out.println("\n========================================");
        System.out.println("Final result by static analysis for " + (systemName != null ? systemName : "all") + ":");
        System.out.println("========================================");
        
        System.out.printf("%-" + systemNameWidth + "s", "System");
        for (String vType : allViolationTypes) {
            System.out.printf("%" + columnWidth + "s", vType);
        }
        System.out.printf("%" + columnWidth + "s", "Total");
        System.out.println();

        for (int i = 0; i < systemNameWidth; i++) System.out.print("-");
        for (String vType : allViolationTypes) {
            for (int i = 0; i < columnWidth; i++) System.out.print("-");
        }
        for (int i = 0; i < columnWidth; i++) System.out.print("-");
        System.out.println();

        Map<String, Integer> columnTotals = new HashMap<>();
        for (String vType : allViolationTypes) {
            columnTotals.put(vType, 0);
        }

        for (String sysTag : allSysTags) {
            Map<String, Integer> row = tableData.get(sysTag);
            int rowTotal = 0;
            
            System.out.printf("%-" + systemNameWidth + "s", sysTag);
            
            for (String vType : allViolationTypes) {
                int count = row.getOrDefault(vType, 0);
                rowTotal += count;
                columnTotals.put(vType, columnTotals.get(vType) + count);
                System.out.printf("%" + columnWidth + "d", count);
            }
            
            System.out.printf("%" + columnWidth + "d", rowTotal);
            System.out.println();
        }

        for (int i = 0; i < systemNameWidth; i++) System.out.print("-");
        for (String vType : allViolationTypes) {
            for (int i = 0; i < columnWidth; i++) System.out.print("-");
        }
        for (int i = 0; i < columnWidth; i++) System.out.print("-");
        System.out.println();

        System.out.printf("%-" + systemNameWidth + "s", "Total");
        int grandTotal = 0;
        for (String vType : allViolationTypes) {
            int colTotal = columnTotals.get(vType);
            grandTotal += colTotal;
            System.out.printf("%" + columnWidth + "d", colTotal);
        }
        System.out.printf("%" + columnWidth + "d", grandTotal);
        System.out.println();
        System.out.println("========================================");
    }
}
