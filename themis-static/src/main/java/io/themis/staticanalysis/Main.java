package indi.dc;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.analysis.CallGraphBuilder;
import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.entity.HadoopAdapter;
import cn.ac.ios.bridge.util.Log;
import indi.dc.access.SameAccessSite;
import indi.dc.impact.ImpactAnalysis;
import indi.dc.impact.ImpactRWSameAccessSite;
import indi.dc.rw.RWSameAccessSite;
import indi.dc.extraction.utils.RPCUtils;
import soot.Scene;

import java.util.*;
import java.io.File;

import cn.ac.ios.bridge.entity.Adapter;
import soot.SootClass;
import soot.SootMethod;

import static indi.dc.access.SameClassVariableAccessAnalysis.findSameClsVarAccessesWhole;
import static indi.dc.rw.ReadWriteAnalysis.getReadWriteWhole;


public class Main {
    public static long START = 0L;

    public static Map<String, String> Map = new HashMap<>();

    public static String project = null;

    public static final Adapter adapter = (Adapter)new HadoopAdapter();

    public static final Analyzer analyzer = new Analyzer(adapter);

    public static long SOOT_END = 0L;

    public static String PREFIX = "org.apache.hadoop";

    public static void main(String[] args) {
        // 0. RPCBridge analysis and info re-organization
//        start("Hadoop_3.4.1", "/home/caohch1/Desktop/DCAnalyzer/src/main/resources/hadoop_3.4.1/sys_jars");
//        start("HadoopPure_3.4.1", "E:\\DCAnalyzer\\src\\main\\resources\\hadoopPure_3.4.1\\sys_jars");
        start("HadoopSmall_3.4.1", "E:\\DCAnalyzer\\src\\main\\resources\\hadoopSmall_3.4.1\\sys_jars");
//        start("HadoopSingle_3.4.1", "/home/caohch1/Desktop/DCAnalyzer/src/main/resources/hadoopSingle_3.4.1/sys_jars");

        analyzer.start();
        HashMap<String, HashMap<String, Set<Analyzer.CallSite>>> protocol2CallSite = new HashMap<>();
        for (String protocol : analyzer.impls.keySet()) {
            Set<Analyzer.CallSite> rpcCallSites = analyzer.proxyRPCSite.get(protocol);
            Set<Analyzer.CallSite> handlerSites = analyzer.newHandlerSite.get(protocol);
            HashMap<String, Set<Analyzer.CallSite>> callSites = new HashMap<>();
            callSites.put("rpc", rpcCallSites);
            callSites.put("handler", handlerSites);
            protocol2CallSite.put(protocol, callSites);
        }
        Log.i(new Object[]{"[Res] protocol2CallSite.size() = ", protocol2CallSite.size()});




        /* A. Class variable - Server instance */
        // Todo: 同一个RPC， 调用两次 client-client-server with same RPC
        Log.i("[Start] Class variable - Server instance");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        for (Set<String> implSets : analyzer.impls.values()) {
            for (String impl : implSets) {
                Set<Analyzer.CallSite> rpcCallSites = analyzer.impls.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(impl))
                        .map(entry -> protocol2CallSite.get(entry.getKey()).get("rpc"))
                        .findFirst()
                        .orElse(new HashSet<>());

                SootClass sootClass = Scene.v().getSootClass(impl);
                Set<SameAccessSite> sameClsVarAccesses = findSameClsVarAccessesWhole(sootClass, rpcCallSites);
                sameClsVarAccessSites.addAll(sameClsVarAccesses);
            }
        }
        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});
//
        // 2. May-happen-in-parallel analysis
        Iterator<SameAccessSite> iterator = sameClsVarAccessSites.iterator();
        while (iterator.hasNext()) {
            SameAccessSite sameAccessSite = iterator.next();
            SootMethod rootCauseMethod1 = sameAccessSite.accessSite1.sootMethod;
            SootMethod rootCauseMethod2 = sameAccessSite.accessSite2.sootMethod;

            if (!(RPCUtils.isRPCImplMethod(rootCauseMethod1) || RPCUtils.isRPCImplMethod(rootCauseMethod2))) {
                iterator.remove();
            }
        }
        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});


        // 3. Read and write analysis
        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWhole(sameClsVarAccessSites);
        rwSameClsVarAccessSites.removeIf(rw -> {
            Set<RWSameAccessSite.RWType> rwTypes = new HashSet<>();
            rwTypes.addAll(rw.accessSite1RWTypeMap.values());
            rwTypes.addAll(rw.accessSite2RWTypeMap.values());
            return !rwTypes.contains(RWSameAccessSite.RWType.Write);
        });
        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
        sameClsVarAccessSites = null;
//
        // 4. Impact Analysis
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
        rwSameClsVarAccessSites = null;
//
//        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
//            Log.i("============================================================================================");
//            Log.i("==VAR==", impactRW.sameAccessClsVar);
//            Log.i("==Imp==", impactRW.sameAccessClsVar.getDeclaringClass().toString());
//            // 有bug
//            Log.i("====IHS====", analyzer.newHandlerSite.get(impactRW.sameAccessClsVar.getDeclaringClass().toString()));
//            Log.i("==SYM==", impactRW.symptomSite.symptomType);
//            Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
//            Log.i("==RPC======", impactRW.symptomSite.RPCCallSites);
//            Log.i("==MET==", impactRW.accessSite1.sootMethod);
//            Log.i("==RCS======", impactRW.accessSite1.callerSites);
//            Log.i("==MET==", impactRW.accessSite2.sootMethod);
//            Log.i("===RCS=====", impactRW.accessSite2.callerSites);
//        }
//        impactRWSameAccessSites = null;


//        /* B. Class variable - Common static */
//        Log.i("[Start] Class variable - Common static");
//
//        // 1. Same access pairs extraction
//        Set<String> impls = new HashSet<>();
//        for (Set<String> implSet : analyzer.impls.values()) {
//            impls.addAll(implSet);
//        }
//        Set<SootClass> targetClasses = new HashSet<>();
//        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
//            if (!impls.contains(sootClass.toString()) && sootClass.isConcrete() && !sootClass.isJavaLibraryClass() && sootClass.toString().startsWith(PREFIX)) {
//                targetClasses.add(sootClass);
//            }
//        }
//        Set<SameAccessSite> sameClsVarAccessSites = findSameClsVarAccessesWhole(targetClasses);
//        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});
//
//        // 2. May-happen-in-parallel analysis
//        Iterator<SameAccessSite> iterator = sameClsVarAccessSites.iterator();
//        while (iterator.hasNext()) {
//            SameAccessSite sameAccessSite = iterator.next();
//            SootMethod rootCauseMethod1 = sameAccessSite.accessSite1.sootMethod;
//            SootMethod rootCauseMethod2 = sameAccessSite.accessSite2.sootMethod;
//            sameAccessSite.accessSite1.callSites2RPC = RPCUtils.findRPCRelated(rootCauseMethod1);
//            sameAccessSite.accessSite2.callSites2RPC = RPCUtils.findRPCRelated(rootCauseMethod2);
//            if (!sameAccessSite.accessSite1.callSites2RPC.isEmpty()) {
//                sameAccessSite.accessSite1.callSites2RPC.add(new Analyzer.CallSite(rootCauseMethod1, null, null));
//            }
//            if (!sameAccessSite.accessSite2.callSites2RPC.isEmpty()) {
//                sameAccessSite.accessSite2.callSites2RPC.add(new Analyzer.CallSite(rootCauseMethod2, null, null));
//            }
//
//            if (sameAccessSite.accessSite1.callSites2RPC.isEmpty() && sameAccessSite.accessSite2.callSites2RPC.isEmpty()) {
//                iterator.remove();
//            }
//        }
//        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});
//
//        // 3. Read and write analysis
//        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWhole(sameClsVarAccessSites);
//        rwSameClsVarAccessSites.removeIf(rw -> {
//            Set<RWSameAccessSite.RWType> rwTypes = new HashSet<>();
//            rwTypes.addAll(rw.accessSite1RWTypeMap.values());
//            rwTypes.addAll(rw.accessSite2RWTypeMap.values());
//            return !rwTypes.contains(RWSameAccessSite.RWType.Write);
//        });
//        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
//        sameClsVarAccessSites = null;
//
//        // 4. Impact Analysis
//        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
//        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
//        rwSameClsVarAccessSites = null;
//
//        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
//            Log.i("============================================================================================");
//            Log.i("==VAR==", impactRW.sameAccessClsVar);
//            Log.i("==SYM==", impactRW.symptomSite.symptomType);
//            Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
//            Log.i("==RPC======", impactRW.symptomSite.RPCCallSites);
//            Log.i("==MET==", impactRW.accessSite1.sootMethod);
//            Log.i("==RCS======", impactRW.accessSite1.callSites2RPC);
//            Log.i("==MET==", impactRW.accessSite2.sootMethod);
//            Log.i("===RCS=====", impactRW.accessSite2.callSites2RPC);
//        }
//        impactRWSameAccessSites = null;


//        /* C. Local variable */
//        Log.i("[Start] Local variable");
//
//        // 1. Same access pairs extraction
//        Set<SameAccessSite> sameLocalAccessSites = new HashSet<>();
//        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
//            if (sootClass.isConcrete() && !sootClass.isJavaLibraryClass() && sootClass.toString().startsWith(PREFIX)) {
//                for (SootMethod sootMethod : sootClass.getMethods()) {
//                    if (sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>") || !sootMethod.hasActiveBody())
//                        continue;
//                    sameLocalAccessSites.addAll(findLocalVarAccesses(sootMethod));
//                }
//
//            }
//        }
//        Log.i(new Object[]{"[Res] sameLocalAccessSites.size() = ", sameLocalAccessSites.size()});
//
//        // 2. May-happen-in-parallel analysis
//        Iterator<SameAccessSite> iterator = sameLocalAccessSites.iterator();
//        while (iterator.hasNext()) {
//            SameAccessSite sameAccessSite = iterator.next();
//            SootMethod rootMethod = sameAccessSite.accessSite1.sootMethod;
//            Stmt accessStmt1 = sameAccessSite.accessSite1.accessSites.iterator().next();
//            Stmt accessStmt2 = sameAccessSite.accessSite2.accessSites.iterator().next();
//
//            // 调用异步检查
//            if (!AsyncChecker.areAsyncExecuted(rootMethod, accessStmt1, accessStmt2)) {
//                iterator.remove();
//                continue;
//            }
//
//            // 是否RPC相关
//            sameAccessSite.accessSite1.callSites2RPC = RPCUtils.findRPCRelatedCallee(accessStmt1.getInvokeExpr().getMethod());
//            sameAccessSite.accessSite2.callSites2RPC = RPCUtils.findRPCRelatedCallee(accessStmt2.getInvokeExpr().getMethod());
//
//            if (sameAccessSite.accessSite1.callSites2RPC == null && sameAccessSite.accessSite2.callSites2RPC == null) {
//                iterator.remove();
//            }
//        }
//        Log.i(new Object[]{"[Res] concurrentSameClsVarAccessSites.size() = ", sameLocalAccessSites.size()});
//
//        // 3. Read and write analysis
//        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWhole(sameLocalAccessSites);
//        rwSameClsVarAccessSites.removeIf(rw -> {
//            Set<RWSameAccessSite.RWType> rwTypes = new HashSet<>();
//            rwTypes.addAll(rw.accessSite1RWTypeMap.values());
//            rwTypes.addAll(rw.accessSite2RWTypeMap.values());
//            return !rwTypes.contains(RWSameAccessSite.RWType.Write);
//        });
//        Log.i(new Object[] { "[Res] rwSameClsVarAccessSites.size() = ", rwSameClsVarAccessSites.size()});
//        sameLocalAccessSites = null;
//
//        // 4. Impact Analysis
//        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWhole(rwSameClsVarAccessSites);
//        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
//        rwSameClsVarAccessSites = null;
//
//        for (ImpactRWSameAccessSite impactRW : impactRWSameAccessSites) {
//            Log.i("============================================================================================");
//            Log.i("==VAR==", impactRW.sameAccessLocalVar.getType(), " ", impactRW.sameAccessLocalVar.getName(), " in ", impactRW.accessSite1.sootMethod);
//            Log.i("==SYM==", impactRW.symptomSite.symptomType);
//            Log.i("==CCS======", impactRW.symptomSite.callChain2Symptom);
//            Log.i("==MET==", impactRW.accessSite1.accessSites.iterator().next().getInvokeExpr().getMethod());
//            Log.i("==CSR==", impactRW.accessSite1.callSites2RPC);
//            Log.i("==MET==", impactRW.accessSite2.accessSites.iterator().next().getInvokeExpr().getMethod());
//            Log.i("==CSR==", impactRW.accessSite2.callSites2RPC);
//        }
//        impactRWSameAccessSites = null;

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
    }

    public static Set<String> getJars(String dir) {
        File directory = new File(dir);
        Set<String> jarList = new HashSet<>();

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        jarList.addAll(getJars(file.getAbsolutePath()));
                    } else if (file.getName().toLowerCase().endsWith(".jar")) {
                        jarList.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return jarList;
    }
}