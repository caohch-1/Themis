package indi.dc.access;

import cn.ac.ios.bridge.analysis.Analyzer;
import soot.SootMethod;
import soot.*;
import soot.jimple.Stmt;

import java.util.List;
import java.util.Set;

public class AccessSite {
    public SootMethod sootMethod;
    public Set<Stmt> accessSites;
    public SootField accessVariable = null;
    public Local accessVariableLocal = null;
    public Set<Analyzer.CallSite> callerSites;
    public List<Analyzer.CallSite> callSites2RPC;

    public AccessSite(SootMethod sootMethod,  Set<Stmt> accessSites, SootField accessVariable, Set<Analyzer.CallSite> callerSites) {
        this.sootMethod = sootMethod;
        this.accessSites = accessSites;
        this.accessVariable = accessVariable;
        this.callerSites = callerSites;
    }

    public AccessSite(SootMethod sootMethod,  Set<Stmt> accessSites, Local accessVariable, Set<Analyzer.CallSite> callerSites) {
        this.sootMethod = sootMethod;
        this.accessSites = accessSites;
        this.accessVariableLocal = accessVariable;
        this.callerSites = callerSites;
    }

    @Override
    public String toString() {
        return "method: "+sootMethod+"\nsites: "+accessSites+"\ncallerSites: "+callerSites;
    }
}
