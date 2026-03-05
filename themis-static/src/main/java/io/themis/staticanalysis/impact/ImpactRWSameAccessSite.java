package indi.dc.impact;

import indi.dc.access.AccessSite;
import indi.dc.rw.RWSameAccessSite;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImpactRWSameAccessSite extends RWSameAccessSite {
    public static enum SymptomType {
        Exception,
        Log,
        Loop,
        Abort
    }

    public static class SymptomSite {
        public List<Stmt> RPCCallSites;
        public List<SootMethod> callChain2Symptom;
        public Stmt symptomSite;
        public SymptomType symptomType;

        public SymptomSite(List<Stmt> RPCCallSite, Stmt symptomSite, List<SootMethod> callChain2Symptom, SymptomType symptomType) {
            this.RPCCallSites = RPCCallSite;
            this.callChain2Symptom = callChain2Symptom;
            this.symptomSite = symptomSite;
            this.symptomType = symptomType;
        }

        @Override
        public String toString() {
            return "SymptomType: " + this.symptomType + ", CallChainToSymptom: " + this.callChain2Symptom + ", SymptomSite: " + this.symptomSite + ", RPCCallSite: " + this.RPCCallSites;
        }
    }

    // sameAccessClsVar被传入RPC,记录RPC call site和symptom site
    public SymptomSite symptomSite;

    public ImpactRWSameAccessSite(AccessSite accessSite1, Map<Stmt, RWType> accessSite1RWTypeMap, RWType rwType1, AccessSite accessSite2, Map<Stmt, RWType> accessSite2RWTypeMap, RWType rwType2) {
        super(accessSite1, accessSite2);
        this.accessSite1RWTypeMap = accessSite1RWTypeMap;
        this.accessSite2RWTypeMap = accessSite2RWTypeMap;
        this.rwType1 = rwType1;
        this.rwType2 = rwType2;
    }

    public ImpactRWSameAccessSite(RWSameAccessSite rwSameAccessSite) {
        super(rwSameAccessSite.accessSite1, rwSameAccessSite.accessSite2);
        this.rwType1 = rwSameAccessSite.rwType1;
        this.rwType2 = rwSameAccessSite.rwType2;
    }

    public ImpactRWSameAccessSite(RWSameAccessSite rwSameAccessSite, SymptomSite symptomSite) {
        super(rwSameAccessSite.accessSite1, rwSameAccessSite.accessSite2);
        this.rwType1 = rwSameAccessSite.rwType1;
        this.rwType2 = rwSameAccessSite.rwType2;
        this.symptomSite = symptomSite;
    }
}
