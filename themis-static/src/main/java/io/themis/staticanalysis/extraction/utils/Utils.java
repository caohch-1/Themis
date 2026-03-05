package indi.dc.extraction.utils;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.analysis.CallGraphBuilder;
import cn.ac.ios.bridge.analysis.SootConfig;
import cn.ac.ios.bridge.util.Log;
import soot.Scene;
import soot.SootClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class Utils {

    public static Set<String> getJars(String dir) {
        File directory = new File(dir);
        Set<String> jarList = new HashSet<>();

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        jarList.addAll(getJars(file.getAbsolutePath()));
                    } else if (file.getName().toLowerCase().endsWith(".jar")) {
                        jarList.add(file.getAbsolutePath());
                    }
                }
            }
        }
        return jarList;
    }

    public static boolean isSubclassOrDescendant(SootClass classToCheck, SootClass targetClass) {
        SootClass currentClass = classToCheck;
        while (currentClass != null) {
            if (currentClass.equals(targetClass)) {
                return true; // 找到目标类，说明是子类
            }
            // 如果当前类有父类，检查其父类
            if (currentClass.hasSuperclass()) {
                currentClass = currentClass.getSuperclass();
            } else {
                break;
            }
        }
        return false; // 没有找到目标类
    }

}
