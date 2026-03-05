package indi.dc.extraction.address;

import cn.ac.ios.bridge.analysis.Analyzer;
import indi.dc.extraction.utils.RPCSignatures;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static indi.dc.extraction.SeverExtraction.start;

public class ServerAddressExtraction {

    public static void getServerAddressProperty(String protocol, String impl, Analyzer.CallSite handlerSite) {
        SootClass implServerSootClass = Scene.v().getSootClassUnsafe(impl);
        if (implServerSootClass == null || !implServerSootClass.isApplicationClass() || !implServerSootClass.isConcrete()) { return ; }


        for (SootMethod method : implServerSootClass.getMethods()) {
            if (!method.hasActiveBody()) { continue; }
            Body body = method.retrieveActiveBody();
            for (Unit unit : body.getUnits()) {
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof AssignStmt && stmt.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    SootMethod invokedMethod = invokeExpr.getMethod();
                    if (RPCSignatures.SERVERS_SET.contains(invokedMethod.getSignature())) {
                        AssignStmt assignStmt = (AssignStmt) stmt;
                        Value rpcServerValue = assignStmt.getLeftOp();

                        // Todo: 具体分析

                    }
                }
            }
        }

    }
}
