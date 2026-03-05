package indi.dc.rw;

import indi.dc.access.AccessSite;
import indi.dc.access.SameAccessSite;
import soot.jimple.Stmt;

import java.util.HashMap;
import java.util.Map;

public class RWSameAccessSite extends SameAccessSite {
    public enum RWType {
        Read,
        Write,
        ReadWrite
    }

    public Map<Stmt, RWType> accessSite1RWTypeMap = new HashMap<Stmt, RWType>();
    public Map<Stmt, RWType> accessSite2RWTypeMap = new HashMap<Stmt, RWType>();
    public RWType rwType1 = null;
    public RWType rwType2 = null;

    public RWSameAccessSite(AccessSite accessSite1, AccessSite accessSite2) {
        super(accessSite1, accessSite2);
    }


}
