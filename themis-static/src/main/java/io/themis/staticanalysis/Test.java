package indi.dc;

import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.util.Log;
import indi.dc.impact.ImpactRWSameAccessSite;
import soot.*;
import soot.Main;
import soot.jimple.*;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.util.*;

public class Test {
    public static void main(String[] args) {
        Set<String> jars = new HashSet<>(indi.dc.Main.getJars("/home/caohch1/Desktop/DCAnalyzer/src/main/resources/hadoop_3.4.1/sys_jars"));

        for (String jar : jars) {
            System.out.println(jar);
            G.reset();
            List<String> processList = new ArrayList<>();
            processList.add(jar);
            Options.v().set_process_dir(processList);
            Options.v().set_no_bodies_for_excluded(true);
            Options.v().set_output_format(12);
            Options.v().set_src_prec(4);
            Options.v().allow_phantom_refs();
            Options.v().set_whole_program(true);
            Options.v().setPhaseOption("spark", "on");
            Options.v().setPhaseOption("spark", "contextsensitive:true");
            Options.v().setPhaseOption("spark", "field-sensitive:true");
            Options.v().setPhaseOption("spark", "heap-insensitive:false");
            Scene.v().addBasicClass("java.lang.reflect.Proxy");
            Pack p1 = PackManager.v().getPack("jtp");
            String phaseName = "jtp.bt";
            Transform t1 = new Transform(phaseName, (Transformer)new BodyTransformer() {
                protected void internalTransform(Body b, String phase, Map<String, String> options) {
                    try {
                        b.getMethod().setActiveBody(b);
                    } catch (Exception e) {
                        Log.e(new Object[] { e });
                    }
                }
            });
            p1.add(t1);
            Main.v().autoSetOptions();
            try {
                Scene.v().loadNecessaryClasses();
                PackManager.v().runPacks();
            } catch (Exception e) {
                Log.e(new Object[] { e });
            }
        }

    }
}
