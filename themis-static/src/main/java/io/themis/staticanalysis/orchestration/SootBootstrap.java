package io.themis.staticanalysis.orchestration;

import soot.G;
import soot.PackManager;
import soot.options.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SootBootstrap {
    public void initialize(String classPath, String processDir) {
        G.reset();
        Options.v().set_prepend_classpath(true);
        Options.v().set_soot_classpath(classPath);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().setPhaseOption("cg.spark", "on");
        List<String> dirs = new ArrayList<>();
        dirs.add(processDir);
        Options.v().set_process_dir(dirs);
        Options.v().set_output_format(Options.output_format_none);
        soot.Scene.v().loadNecessaryClasses();
        PackManager.v().runPacks();
    }

    public List<String> emptyArgs() {
        return Collections.emptyList();
    }
}
