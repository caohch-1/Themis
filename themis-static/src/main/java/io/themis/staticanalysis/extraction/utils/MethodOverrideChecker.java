package indi.dc.extraction.utils;

import cn.ac.ios.bridge.analysis.Analyzer;
import cn.ac.ios.bridge.util.Log;
import soot.*;

import java.util.*;

public class MethodOverrideChecker {
    public static void printProtocolsInfo(Analyzer analyzer){
        for (String protocol : analyzer.impls.keySet()) {
            Log.i("====", protocol, "====================");
            for (SootMethod sootMethod : Scene.v().getSootClass(protocol).getMethods()) {
                Log.i("========", sootMethod, "===================");
            }

            for (String impl : analyzer.impls.get(protocol)) {
                SootClass implClass = Scene.v().getSootClass(impl);
                Log.i("========", implClass, "====================");
                for (SootMethod sootMethod : implClass.getMethods()) {
                    if (MethodOverrideChecker.isOverrideMethod(sootMethod)) Log.i("========", "Override: ",sootMethod, "===================");
                    else Log.i("========", "NewDef: ",sootMethod, "==================");
                }
            }
        }
    }


//    public static boolean isOverrideMethod(SootMethod method) {
//        if (method.isStatic()) return false;
//
//        SootClass declaringClass = method.getDeclaringClass();
//        String methodName = method.getName();
//        List<Type> paramTypes = method.getParameterTypes();
//        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
//
//        // 检查父类继承链
//        if (method.getName().equals("listReservations")) System.out.println(hierarchy.getSuperclassesOf(declaringClass));
//        for (SootClass superClass : hierarchy.getSuperclassesOf(declaringClass)) {
//            if (hasMatchingMethod(superClass, methodName, paramTypes, method.getReturnType(), declaringClass)) {
//                return true;
//            }
//        }
//
//        // 检查实现的接口
//        for (SootClass interfaceClass : declaringClass.getInterfaces()) {
//            if (method.getName().equals("listReservations")) System.out.println(hierarchy.getSuperinterfacesOf(interfaceClass));
//            for (SootClass superInterfaceClass : hierarchy.getSuperinterfacesOf(interfaceClass)) {
//                if (hasMatchingMethod(superInterfaceClass, methodName, paramTypes, method.getReturnType(), declaringClass)) {
//                    return true;
//                }
//            }
//
//        }
//
//        return false;
//    }

    public static boolean isOverrideMethod(SootMethod method) {
        if (method.isStatic()) return false;

        SootClass declaringClass = method.getDeclaringClass();
        String methodName = method.getName();
        List<Type> paramTypes = method.getParameterTypes();
        Type returnType = method.getReturnType();
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();

        // 检查父类链及其实现的接口
        for (SootClass superClass : hierarchy.getSuperclassesOf(declaringClass)) {

            // 检查父类中的直接方法
            if (hasMatchingMethod(superClass, methodName, paramTypes, returnType, declaringClass)) {
                return true;
            }

            // 检查父类实现的接口及其继承链
            for (SootClass interfaceClass : superClass.getInterfaces()) {
                if (checkInterfaceMethod(interfaceClass, methodName, paramTypes, returnType, declaringClass)) {
                    return true;
                }
            }
        }

        // 检查当前类直接实现的接口及其继承链
        for (SootClass interfaceClass : declaringClass.getInterfaces()) {
            if (checkInterfaceMethod(interfaceClass, methodName, paramTypes, returnType, declaringClass)) {
                return true;
            }
        }

        return false;
    }

    // 递归检查接口及其父接口
    private static boolean checkInterfaceMethod(SootClass interfaceClass, String methodName, List<Type> paramTypes, Type returnType, SootClass declaringClass) {
        if (hasMatchingMethod(interfaceClass, methodName, paramTypes, returnType, declaringClass)) {
            return true;
        }

        // 检查父接口
        for (SootClass superInterface : Scene.v().getActiveHierarchy().getSuperinterfacesOf(interfaceClass)) {
            if (checkInterfaceMethod(superInterface, methodName, paramTypes, returnType, declaringClass)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasMatchingMethod(SootClass superClass, String methodName,List<Type> paramTypes,Type returnType,SootClass declaringClass) {
        try {
            SootMethod superMethod = superClass.getMethod(methodName, paramTypes);

            if (!Scene.v().getFastHierarchy().canStoreType(returnType, superMethod.getReturnType())) {
                return false;
            }

            // 修复变量引用（使用参数传入的declaringClass和superClass）
            return superMethod.isPublic() || superMethod.isProtected() || (!superMethod.isPrivate() && areInSamePackage(declaringClass, superClass)); // 使用参数变量

        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean areInSamePackage(SootClass class1, SootClass class2) {
        // 获取类1的包名
        String pkg1 = class1.getName().contains(".") ? class1.getName().substring(0, class1.getName().lastIndexOf('.')) : "";

        // 获取类2的包名
        String pkg2 = class2.getName().contains(".") ? class2.getName().substring(0, class2.getName().lastIndexOf('.')) : "";

        return pkg1.equals(pkg2);
    }
}
