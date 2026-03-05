package indi.dc.extraction.utils;

import cn.ac.ios.bridge.util.Log;
import indi.dc.Main;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.*;

public class RPCSignatures {
    public static String[] CLIENTS = new String[]{
            "<org.apache.hadoop.ipc.ProxyCombiner: java.lang.Object combine(java.lang.Class,java.lang.Object[])>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy waitForProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy waitForProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,long)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy waitForProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,int,org.apache.hadoop.io.retry.RetryPolicy,long)>",
            "<org.apache.hadoop.ipc.RPC: java.lang.Object getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.RPC: java.lang.Object getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RPC: java.lang.Object getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RPC: java.lang.Object getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration)>",
            "<org.apache.hadoop.ipc.RPC: org.apache.hadoop.ipc.ProtocolProxy getProtocolProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration)>",
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy)>",
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProtocolMetaInfoProxy(org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProtocolMetaInfoProxy(org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.ProtocolProxy getProtocolMetaInfoProxy(org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProxy(java.lang.Class,long,java.net.InetSocketAddress,org.apache.hadoop.security.UserGroupInformation,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory,int,org.apache.hadoop.io.retry.RetryPolicy,java.util.concurrent.atomic.AtomicBoolean,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.ProtocolProxy getProtocolMetaInfoProxy(org.apache.hadoop.ipc.Client$ConnectionId,org.apache.hadoop.conf.Configuration,javax.net.SocketFactory)>"

    };

    public static String[] SERVERS = new String[]{
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RPC$Builder: org.apache.hadoop.ipc.RPC$Server build()>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.yarn.factories.RpcServerFactory: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int,java.lang.String)>",
            "<org.apache.hadoop.yarn.factories.impl.pb.RpcServerFactoryPBImpl: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int)>",
            "<org.apache.hadoop.yarn.factories.impl.pb.RpcServerFactoryPBImpl: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int,java.lang.String)>",
            "<org.apache.hadoop.yarn.factories.impl.pb.RpcServerFactoryPBImpl: org.apache.hadoop.ipc.Server createServer(java.lang.Class,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int,org.apache.hadoop.thirdparty.protobuf.BlockingService,java.lang.String)>",
            "<org.apache.hadoop.yarn.ipc.HadoopYarnProtoRPC: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int,java.lang.String)>",
            "<org.apache.hadoop.yarn.ipc.YarnRPC: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int,java.lang.String)>",
            "<org.apache.hadoop.yarn.ipc.YarnRPC: org.apache.hadoop.ipc.Server getServer(java.lang.Class,java.lang.Object,java.net.InetSocketAddress,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,int)>",
            "<org.apache.hadoop.yarn.server.resourcemanager.ApplicationMasterService: org.apache.hadoop.ipc.Server getServer(org.apache.hadoop.yarn.ipc.YarnRPC,org.apache.hadoop.conf.Configuration,java.net.InetSocketAddress,org.apache.hadoop.yarn.server.resourcemanager.security.AMRMTokenSecretManager)>",
            "<org.apache.hadoop.yarn.server.resourcemanager.OpportunisticContainerAllocatorAMService: org.apache.hadoop.ipc.Server getServer(org.apache.hadoop.yarn.ipc.YarnRPC,org.apache.hadoop.conf.Configuration,java.net.InetSocketAddress,org.apache.hadoop.yarn.server.resourcemanager.security.AMRMTokenSecretManager)>",

            "<org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ResourceLocalizationService: org.apache.hadoop.ipc.Server createServer()>",
            "<org.apache.hadoop.hdfs.server.namenode.NameNodeAdapter: org.apache.hadoop.ipc.Server getRpcServer(org.apache.hadoop.hdfs.server.namenode.NameNode)>"
    };

    public static Set<String> SERVERS_SET = new HashSet<>(Arrays.asList(SERVERS));

    public static String[] HBASE_SERVERS = new String[]{
            "<org.apache.hadoop.ipc.RpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.WritableRpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine2: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
            "<org.apache.hadoop.ipc.RPC$Builder: org.apache.hadoop.ipc.RPC$Server build()>",
            "<org.apache.hadoop.ipc.ProtobufRpcEngine: org.apache.hadoop.ipc.RPC$Server getServer(java.lang.Class,java.lang.Object,java.lang.String,int,int,int,int,boolean,org.apache.hadoop.conf.Configuration,org.apache.hadoop.security.token.SecretManager,java.lang.String,org.apache.hadoop.ipc.AlignmentContext)>",
    };

    public static Set<String> HBASE_SERVERS_SET = new HashSet<>(Arrays.asList(HBASE_SERVERS));

//    public static void main(String[] args) {
//        String systemName = "HadoopPure_3.4.1";
//        String systemPath = "E:\\DCAnalyzer\\src\\main\\resources\\"+systemName+"\\sys_jars";
////        start("HadoopSmall_3.4.1", "E:\\DCAnalyzer\\src\\main\\resources\\hadoopSmall_3.4.1\\sys_jars");
//        String outputPathAddress = "E:\\DCAnalyzer\\src\\main\\resources\\logs\\AddressProperty_" + systemName + ".txt";
//        String outputPathRPC = "E:\\DCAnalyzer\\src\\main\\resources\\logs\\RPCBridge_"+systemName+".txt";
//
//        Main.start(systemName, systemPath);
//
//        Type rpcServerType1 = Scene.v().getSootClass("org.apache.hadoop.ipc.RPC$Server").getType();
//        Type rpcServerType2 = Scene.v().getSootClass("org.apache.hadoop.ipc.Server").getType();
//        Type rpcClientType1 = Scene.v().getSootClass("org.apache.hadoop.ipc.ProtocolProxy").getType();
//        String rpcClientType2 = "java.lang.Object";
//
//        Log.i(rpcServerType1);
//        Log.i(rpcServerType2);
//        Log.i("======================================================================");
//
//        Log.i(rpcClientType1);
//        Log.i(rpcClientType2);
//        Log.i("======================================================================");
//
//        List<String> serverSigs = new ArrayList<>();
//        List<String> clientSigs = new ArrayList<>();
//        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
//            for (SootMethod sootMethod : sootClass.getMethods()) {
//                if ((sootMethod.getReturnType().equals(rpcServerType1) || sootMethod.getReturnType().equals(rpcServerType2))) {
//                    serverSigs.add(sootMethod.getSignature());
//                } else if (
//                        sootMethod.getReturnType().equals(rpcClientType1) ||
//                                (
//                                        sootMethod.getReturnType().toString().equals(rpcClientType2) &&
//                                                sootMethod.getName().equals("getProxy") &&
//                                                sootMethod.getDeclaringClass().getName().contains("org.apache.hadoop.ipc.RPC")
//                                )
//                ) {
//                    clientSigs.add(sootMethod.getSignature());
//                }
//            }
//        }
//
//        for (String serverSig : serverSigs) {
//            Log.i(serverSig);
//        }
//
//        Log.i("======================================================================");
//
//        for (String clientSig : clientSigs) {
//            Log.i(clientSig);
//        }
//    }
}
