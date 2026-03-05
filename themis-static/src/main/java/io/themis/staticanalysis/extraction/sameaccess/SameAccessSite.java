package indi.dc.extraction.sameaccess;

import indi.dc.extraction.sameaccess.AccessSite;
import soot.Local;
import soot.SootField;
import soot.SootMethod;

import java.util.List;

public class SameAccessSite {
    public AccessSite accessSite1;
    public AccessSite accessSite2;
    public SootField sameAccessClsVar;
    public Local sameAccessLocalVar;
    public SameAccessSite(AccessSite accessSite1, AccessSite accessSite2) {
        this.accessSite1 = accessSite1;
        this.accessSite2 = accessSite2;
        if (accessSite1.accessVariableLocal != null) {
            this.sameAccessLocalVar = accessSite1.accessVariableLocal;
        }
        if (accessSite1.accessVariable!= null) {
            this.sameAccessClsVar = accessSite1.accessVariable;

        }
    }



    @Override
    public String toString() {
        return "variable: "+accessSite1.accessVariable+"\n"+accessSite1+"\n"+accessSite2;
    }
}
