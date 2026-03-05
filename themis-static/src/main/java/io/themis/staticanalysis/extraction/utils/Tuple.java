package indi.dc.extraction.utils;


import soot.SootField;
import soot.SootMethod;

public class Tuple {
    public final SootMethod func1;
    public final SootMethod func4; // common caller of func2 & func3
    public final SootMethod func2;
    public final SootMethod func3;
    public final SootField classVar;

    public Tuple(SootMethod f1, SootMethod f4, SootMethod f2, SootMethod f3, SootField var) {
        this.func1 = f1; this.func4 = f4; this.func2 = f2; this.func3 = f3; this.classVar = var;
    }

    @Override public String toString() {
        return String.format("<%s, %s, | %s, %s, | %s>",
                name(func1), name(func4), name(func2), name(func3), classVar.getSignature());
    }
    private static String sig(SootMethod m){ return m==null? "null": m.getSignature(); }
    private static String name(SootMethod m){ return m==null? "null": m.getName(); }
}