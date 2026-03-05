package io.themis.staticanalysis.framework;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RpcSignatureCatalog {
    private static final Set<String> HADOOP_CLIENT_SIGNATURES = immutableSet(new String[]{
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
    });

    private static final Set<String> HADOOP_SERVER_SIGNATURES = immutableSet(new String[]{
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
    });

    private RpcSignatureCatalog() {
    }

    public static boolean isHadoopClientSignature(String signature) {
        return HADOOP_CLIENT_SIGNATURES.contains(signature);
    }

    public static boolean isHadoopServerSignature(String signature) {
        return HADOOP_SERVER_SIGNATURES.contains(signature);
    }

    public static Set<String> hadoopClientSignatures() {
        return HADOOP_CLIENT_SIGNATURES;
    }

    public static Set<String> hadoopServerSignatures() {
        return HADOOP_SERVER_SIGNATURES;
    }

    private static Set<String> immutableSet(String[] values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
