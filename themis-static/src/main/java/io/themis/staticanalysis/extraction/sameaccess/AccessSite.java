package indi.dc.extraction.sameaccess;

import cn.ac.ios.bridge.analysis.Analyzer;
import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.List;
import java.util.Set;

public class AccessSite {
    public SootMethod sootMethod;
    public Set<Stmt> accessSites;
    public List<List<SootMethod>> chain2StaticAccess;
    public SootField accessVariable = null;
    public Local accessVariableLocal = null;
    public Set<Analyzer.CallSite> callerSites;
    public List<Analyzer.CallSite> callSites2RPC;


    public AccessSite(SootMethod sootMethod, Set<Stmt> accessSites, SootField accessVariable, Set<Analyzer.CallSite> callerSites) {
        this.sootMethod = sootMethod;
        this.accessSites = accessSites;
        this.accessVariable = accessVariable;
        this.callerSites = callerSites;
    }

    public AccessSite(SootMethod sootMethod, Set<Stmt> accessSites, SootField accessVariable, Set<Analyzer.CallSite> callerSites, List<List<SootMethod>> chain2StaticAccess) {
        this.sootMethod = sootMethod;
        this.accessSites = accessSites;
        this.accessVariable = accessVariable;
        this.callerSites = callerSites;
        this.chain2StaticAccess = chain2StaticAccess;
    }

    public AccessSite(SootMethod sootMethod, Set<Stmt> accessSites, Local accessVariable, Set<Analyzer.CallSite> callerSites) {
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
