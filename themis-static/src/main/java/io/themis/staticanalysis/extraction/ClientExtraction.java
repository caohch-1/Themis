package indi.dc.extraction;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.analysis.CallGraphBuilder;
import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.entity.Adapter;
import cn.ac.ios.bridge.entity.HadoopAdapter;
import cn.ac.ios.bridge.util.Log;
import indi.dc.extraction.impact.ImpactAnalysis;
import indi.dc.extraction.impact.ImpactRWSameAccessSite;
import indi.dc.extraction.readwrite.RWSameAccessSite;
import indi.dc.extraction.sameaccess.SameAccessSite;
import indi.dc.extraction.utils.AsyncChecker;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;

import java.io.File;
import java.util.*;

import static indi.dc.extraction.readwrite.ReadWriteAnalysis.*;
import static indi.dc.extraction.sameaccess.SameClassVariableAccessAnalysis.*;
import static indi.dc.extraction.utils.SameCallerAnalyzer.haveCommonCaller;
import static indi.dc.extraction.utils.Utils.getJars;

public class ClientExtraction {
    public static final Adapter adapter = (Adapter) new HadoopAdapter();
    public static final Analyzer analyzer = new Analyzer(adapter);
    public static Set<String> protos;
    public static Set<String> impls;
    public static Map<String, Set<String>> protocol2impls;
    public static Map<String, String> impl2proto;
    public static HashMap<String, Set<Analyzer.CallSite>> impl2handlerSite;
    public static HashMap<String, Set<Analyzer.CallSite>> proxy2CallSite;
    public static CallGraph cg;

    public static void main(String[] args) {
//        start("HadoopSmall_3.4.1", "E:\\DCAnalyzer\\src\\main\\resources\\hadoopSmall_3.4.1\\sys_jars");
        start("HadoopPure_3.4.1", "E:\\DCAnalyzer\\src\\main\\resources\\hadoopPure_3.4.1\\sys_jars");

        analyzer.start();

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

        /* A. Instance Variable*/
        instanceAndStaticPairs();

        /* B. Local variable*/
        localPairs();

    }

    public static void instanceAndStaticPairs() {
        /* A. Instance Variable*/
        // Todo: 同一个RPC， 调用两次 client-client-server with same RPC
        Log.i("[Start] Client: Instance/Static variable");

        // 1. Same access pairs extraction
        Set<SameAccessSite> sameClsVarAccessSites = new HashSet<>();
        for (String proto : proxy2CallSite.keySet()) {
            for (Analyzer.CallSite callSite : proxy2CallSite.get(proto)) {
                sameClsVarAccessSites.addAll(findSameClsVarAccessesClient(callSite));
            }
        }

        Log.i(new Object[] { "[Res] sameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

        // 2. May-happen-in-parallel analysis
        Iterator<SameAccessSite> iterator = sameClsVarAccessSites.iterator();
        while (iterator.hasNext()) {
            SameAccessSite sameAccessSite = iterator.next();
            SootMethod sootMethod1 = sameAccessSite.accessSite1.sootMethod;
            SootMethod sootMethod2 = sameAccessSite.accessSite2.sootMethod;

            if (haveCommonCaller(sootMethod1, sootMethod2, cg)) {
                iterator.remove();
            }
        }
        Log.i(new Object[] { "[Res] concurrentSameClsVarAccessSites.size() = ", sameClsVarAccessSites.size()});

        // 3. Read and write analysis
        Set<RWSameAccessSite> rwSameClsVarAccessSites = getReadWriteWholeClient(sameClsVarAccessSites);
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
        Set<ImpactRWSameAccessSite> impactRWSameAccessSites = ImpactAnalysis.getImpactSitesWholeClient(rwSameClsVarAccessSites);
        Log.i("[Res] impactRWSameAccessSites.size() = ", impactRWSameAccessSites.size());
        rwSameClsVarAccessSites = null;
    }

    public static void localPairs() {
        Log.i("[Start] Client: Local variable");
        // 1. Same access pairs extraction
        Set<SameAccessSite> sameLocalVarAccessSites = new HashSet<>();
        for (String proto : proxy2CallSite.keySet()) {
            for (Analyzer.CallSite callSite : proxy2CallSite.get(proto)) {
                sameLocalVarAccessSites.addAll(findSameClsVarAccessesClientLocal(callSite));
            }
        }

        Log.i(new Object[] { "[Res] sameLocalVarAccessSites.size() = ", sameLocalVarAccessSites.size()});

        // 2. May-happen-in-parallel analysis
        Iterator<SameAccessSite> iterator = sameLocalVarAccessSites.iterator();
        while (iterator.hasNext()) {
            SameAccessSite sameAccessSite = iterator.next();
            SootMethod rootMethod = sameAccessSite.accessSite1.sootMethod;
            Stmt accessStmt1 = sameAccessSite.accessSite1.accessSites.iterator().next();
            Stmt accessStmt2 = sameAccessSite.accessSite2.accessSites.iterator().next();

            // 调用异步检查
            if (!AsyncChecker.areAsyncExecuted(rootMethod, accessStmt1, accessStmt2)) {
                iterator.remove();
            }

        }
        Log.i(new Object[]{"[Res] concurrentSameLocalVarAccessSites.size() = ", sameLocalVarAccessSites.size()});

        // Todo: 目前为0，先不管了
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

}
