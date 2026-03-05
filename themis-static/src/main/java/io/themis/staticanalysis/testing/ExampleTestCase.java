package indi.dc.testing;

public class ExampleTestCase {
    public static final String SERVER_CLASS = "package org.apache.hadoop.mapreduce.v2.hs;\n" +
            "\n" +
            "import java.io.IOException;\n" +
            "import java.net.InetSocketAddress;\n" +
            "\n" +
            "import org.apache.hadoop.classification.InterfaceAudience.Private;\n" +
            "import org.apache.hadoop.conf.Configuration;\n" +
            "import org.apache.hadoop.mapred.JobConf;\n" +
            "import org.apache.hadoop.mapreduce.MRConfig;\n" +
            "import org.apache.hadoop.mapreduce.v2.hs.HistoryServerStateStoreService.HistoryServerState;\n" +
            "import org.apache.hadoop.mapreduce.v2.hs.server.HSAdminServer;\n" +
            "import org.apache.hadoop.mapreduce.v2.jobhistory.JHAdminConfig;\n" +
            "import org.apache.hadoop.mapreduce.v2.util.MRWebAppUtil;\n" +
            "import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;\n" +
            "import org.apache.hadoop.metrics2.source.JvmMetrics;\n" +
            "import org.apache.hadoop.security.SecurityUtil;\n" +
            "import org.apache.hadoop.service.AbstractService;\n" +
            "import org.apache.hadoop.service.CompositeService;\n" +
            "import org.apache.hadoop.util.ExitUtil;\n" +
            "import org.apache.hadoop.util.GenericOptionsParser;\n" +
            "import org.apache.hadoop.util.JvmPauseMonitor;\n" +
            "import org.apache.hadoop.util.ShutdownHookManager;\n" +
            "import org.apache.hadoop.util.StringUtils;\n" +
            "import org.apache.hadoop.yarn.YarnUncaughtExceptionHandler;\n" +
            "import org.apache.hadoop.yarn.conf.YarnConfiguration;\n" +
            "import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;\n" +
            "import org.apache.hadoop.yarn.logaggregation.AggregatedLogDeletionService;\n" +
            "\n" +
            "import org.apache.hadoop.classification.VisibleForTesting;\n" +
            "import org.slf4j.Logger;\n" +
            "import org.slf4j.LoggerFactory;\n" +
            "\n" +
            "/******************************************************************\n" +
            " * {@link JobHistoryServer} is responsible for servicing all job history\n" +
            " * related requests from client.\n" +
            " *\n" +
            " *****************************************************************/\n" +
            "public class JobHistoryServer extends CompositeService {\n" +
            "\n" +
            "  /**\n" +
            "   * Priority of the JobHistoryServer shutdown hook.\n" +
            "   */\n" +
            "  public static final int SHUTDOWN_HOOK_PRIORITY = 30;\n" +
            "\n" +
            "  public static final long historyServerTimeStamp = System.currentTimeMillis();\n" +
            "\n" +
            "  private static final Logger LOG =\n" +
            "      LoggerFactory.getLogger(JobHistoryServer.class);\n" +
            "  private HistoryClientService clientService;\n" +
            "  private JobHistory jobHistoryService;\n" +
            "  protected JHSDelegationTokenSecretManager jhsDTSecretManager;\n" +
            "  private AggregatedLogDeletionService aggLogDelService;\n" +
            "  private HSAdminServer hsAdminServer;\n" +
            "  private HistoryServerStateStoreService stateStore;\n" +
            "  private JvmPauseMonitor pauseMonitor;\n" +
            "\n" +
            "  // utility class to start and stop secret manager as part of service\n" +
            "  // framework and implement state recovery for secret manager on startup\n" +
            "  private class HistoryServerSecretManagerService\n" +
            "      extends AbstractService {\n" +
            "\n" +
            "    public HistoryServerSecretManagerService() {\n" +
            "      super(HistoryServerSecretManagerService.class.getName());\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void serviceStart() throws Exception {\n" +
            "      boolean recoveryEnabled = getConfig().getBoolean(\n" +
            "          JHAdminConfig.MR_HS_RECOVERY_ENABLE,\n" +
            "          JHAdminConfig.DEFAULT_MR_HS_RECOVERY_ENABLE);\n" +
            "      if (recoveryEnabled) {\n" +
            "        assert stateStore.isInState(STATE.STARTED);\n" +
            "        HistoryServerState state = stateStore.loadState();\n" +
            "        jhsDTSecretManager.recover(state);\n" +
            "      }\n" +
            "\n" +
            "      try {\n" +
            "        jhsDTSecretManager.startThreads();\n" +
            "      } catch(IOException io) {\n" +
            "        LOG.error(\"Error while starting the Secret Manager threads\", io);\n" +
            "        throw io;\n" +
            "      }\n" +
            "\n" +
            "      super.serviceStart();\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void serviceStop() throws Exception {\n" +
            "      if (jhsDTSecretManager != null) {\n" +
            "        jhsDTSecretManager.stopThreads();\n" +
            "      }\n" +
            "      super.serviceStop();\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  public JobHistoryServer() {\n" +
            "    super(JobHistoryServer.class.getName());\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  protected void serviceInit(Configuration conf) throws Exception {\n" +
            "    Configuration config = new YarnConfiguration(conf);\n" +
            "\n" +
            "    // This is required for WebApps to use https if enabled.\n" +
            "    MRWebAppUtil.initialize(getConfig());\n" +
            "    try {\n" +
            "      doSecureLogin(conf);\n" +
            "    } catch(IOException ie) {\n" +
            "      throw new YarnRuntimeException(\"History Server Failed to login\", ie);\n" +
            "    }\n" +
            "    jobHistoryService = new JobHistory();\n" +
            "    stateStore = createStateStore(conf);\n" +
            "    this.jhsDTSecretManager = createJHSSecretManager(conf, stateStore);\n" +
            "    clientService = createHistoryClientService();\n" +
            "    aggLogDelService = new AggregatedLogDeletionService();\n" +
            "    hsAdminServer = new HSAdminServer(aggLogDelService, jobHistoryService);\n" +
            "    addService(stateStore);\n" +
            "    addService(new HistoryServerSecretManagerService());\n" +
            "    addService(jobHistoryService);\n" +
            "    addService(clientService);\n" +
            "    addService(aggLogDelService);\n" +
            "    addService(hsAdminServer);\n" +
            "\n" +
            "    DefaultMetricsSystem.initialize(\"JobHistoryServer\");\n" +
            "    JvmMetrics jm = JvmMetrics.initSingleton(\"JobHistoryServer\", null);\n" +
            "    pauseMonitor = new JvmPauseMonitor();\n" +
            "    addService(pauseMonitor);\n" +
            "    jm.setPauseMonitor(pauseMonitor);\n" +
            "\n" +
            "    super.serviceInit(config);\n" +
            "  }\n" +
            "\n" +
            "  @VisibleForTesting\n" +
            "  protected HistoryClientService createHistoryClientService() {\n" +
            "    return new HistoryClientService(jobHistoryService, this.jhsDTSecretManager);\n" +
            "  }\n" +
            "\n" +
            "  protected JHSDelegationTokenSecretManager createJHSSecretManager(\n" +
            "      Configuration conf, HistoryServerStateStoreService store) {\n" +
            "    long secretKeyInterval = \n" +
            "        conf.getLong(MRConfig.DELEGATION_KEY_UPDATE_INTERVAL_KEY, \n" +
            "                     MRConfig.DELEGATION_KEY_UPDATE_INTERVAL_DEFAULT);\n" +
            "      long tokenMaxLifetime =\n" +
            "        conf.getLong(MRConfig.DELEGATION_TOKEN_MAX_LIFETIME_KEY,\n" +
            "                     MRConfig.DELEGATION_TOKEN_MAX_LIFETIME_DEFAULT);\n" +
            "      long tokenRenewInterval =\n" +
            "        conf.getLong(MRConfig.DELEGATION_TOKEN_RENEW_INTERVAL_KEY, \n" +
            "                     MRConfig.DELEGATION_TOKEN_RENEW_INTERVAL_DEFAULT);\n" +
            "      \n" +
            "    return new JHSDelegationTokenSecretManager(secretKeyInterval, \n" +
            "        tokenMaxLifetime, tokenRenewInterval, 3600000, store);\n" +
            "  }\n" +
            "\n" +
            "  protected HistoryServerStateStoreService createStateStore(\n" +
            "      Configuration conf) {\n" +
            "    return HistoryServerStateStoreServiceFactory.getStore(conf);\n" +
            "  }\n" +
            "\n" +
            "  protected void doSecureLogin(Configuration conf) throws IOException {\n" +
            "    InetSocketAddress socAddr = getBindAddress(conf);\n" +
            "    SecurityUtil.login(conf, JHAdminConfig.MR_HISTORY_KEYTAB,\n" +
            "        JHAdminConfig.MR_HISTORY_PRINCIPAL, socAddr.getHostName());\n" +
            "  }\n" +
            "\n" +
            "  /**\n" +
            "   * Retrieve JHS bind address from configuration\n" +
            "   *\n" +
            "   * @param conf\n" +
            "   * @return InetSocketAddress\n" +
            "   */\n" +
            "  public static InetSocketAddress getBindAddress(Configuration conf) {\n" +
            "    return conf.getSocketAddr(JHAdminConfig.MR_HISTORY_ADDRESS,\n" +
            "      JHAdminConfig.DEFAULT_MR_HISTORY_ADDRESS,\n" +
            "      JHAdminConfig.DEFAULT_MR_HISTORY_PORT);\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  protected void serviceStart() throws Exception {\n" +
            "    super.serviceStart();\n" +
            "  }\n" +
            "  \n" +
            "  @Override\n" +
            "  protected void serviceStop() throws Exception {\n" +
            "    DefaultMetricsSystem.shutdown();\n" +
            "    super.serviceStop();\n" +
            "  }\n" +
            "\n" +
            "  @Private\n" +
            "  public HistoryClientService getClientService() {\n" +
            "    return this.clientService;\n" +
            "  }\n" +
            "\n" +
            "  static JobHistoryServer launchJobHistoryServer(String[] args) {\n" +
            "    Thread.\n" +
            "        setDefaultUncaughtExceptionHandler(new YarnUncaughtExceptionHandler());\n" +
            "    StringUtils.startupShutdownMessage(JobHistoryServer.class, args, LOG);\n" +
            "    JobHistoryServer jobHistoryServer = null;\n" +
            "    try {\n" +
            "      jobHistoryServer = new JobHistoryServer();\n" +
            "      ShutdownHookManager.get().addShutdownHook(\n" +
            "          new CompositeServiceShutdownHook(jobHistoryServer),\n" +
            "          SHUTDOWN_HOOK_PRIORITY);\n" +
            "      YarnConfiguration conf = new YarnConfiguration(new JobConf());\n" +
            "      new GenericOptionsParser(conf, args);\n" +
            "      jobHistoryServer.init(conf);\n" +
            "      jobHistoryServer.start();\n" +
            "    } catch (Throwable t) {\n" +
            "      LOG.error(\"Error starting JobHistoryServer\", t);\n" +
            "      ExitUtil.terminate(-1, \"Error starting JobHistoryServer\");\n" +
            "    }\n" +
            "    return jobHistoryServer;\n" +
            "  }\n" +
            "\n" +
            "  public static void main(String[] args) {\n" +
            "    launchJobHistoryServer(args);\n" +
            "  }\n" +
            "}";
    public static final String RPC_CLASS = "package org.apache.hadoop.mapreduce.v2.hs.client;\n" +
            "\n" +
            "import java.io.IOException;\n" +
            "import java.net.InetSocketAddress;\n" +
            "import java.util.Arrays;\n" +
            "\n" +
            "import org.apache.hadoop.classification.InterfaceAudience.Private;\n" +
            "import org.apache.hadoop.conf.Configuration;\n" +
            "import org.apache.hadoop.conf.Configured;\n" +
            "import org.apache.hadoop.fs.CommonConfigurationKeys;\n" +
            "import org.apache.hadoop.mapred.JobConf;\n" +
            "import org.apache.hadoop.mapreduce.v2.api.HSAdminRefreshProtocol;\n" +
            "import org.apache.hadoop.mapreduce.v2.hs.HSProxies;\n" +
            "import org.apache.hadoop.mapreduce.v2.jobhistory.JHAdminConfig;\n" +
            "import org.apache.hadoop.security.RefreshUserMappingsProtocol;\n" +
            "import org.apache.hadoop.security.UserGroupInformation;\n" +
            "import org.apache.hadoop.tools.GetUserMappingsProtocol;\n" +
            "import org.apache.hadoop.util.Tool;\n" +
            "import org.apache.hadoop.util.ToolRunner;\n" +
            "\n" +
            "@Private\n" +
            "public class HSAdmin extends Configured implements Tool {\n" +
            "\n" +
            "  public HSAdmin() {\n" +
            "    super();\n" +
            "  }\n" +
            "\n" +
            "  public HSAdmin(JobConf conf) {\n" +
            "    super(conf);\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  public void setConf(Configuration conf) {\n" +
            "    if (conf != null) {\n" +
            "      conf = addSecurityConfiguration(conf);\n" +
            "    }\n" +
            "    super.setConf(conf);\n" +
            "  }\n" +
            "\n" +
            "  private Configuration addSecurityConfiguration(Configuration conf) {\n" +
            "    conf = new JobConf(conf);\n" +
            "    conf.set(CommonConfigurationKeys.HADOOP_SECURITY_SERVICE_USER_NAME_KEY,\n" +
            "        conf.get(JHAdminConfig.MR_HISTORY_PRINCIPAL, \"\"));\n" +
            "    return conf;\n" +
            "  }\n" +
            "\n" +
            "  /**\n" +
            "   * Displays format of commands.\n" +
            "   * \n" +
            "   * @param cmd\n" +
            "   *          The command that is being executed.\n" +
            "   */\n" +
            "  private static void printUsage(String cmd) {\n" +
            "    if (\"-refreshUserToGroupsMappings\".equals(cmd)) {\n" +
            "      System.err\n" +
            "          .println(\"Usage: mapred hsadmin [-refreshUserToGroupsMappings]\");\n" +
            "    } else if (\"-refreshSuperUserGroupsConfiguration\".equals(cmd)) {\n" +
            "      System.err\n" +
            "          .println(\"Usage: mapred hsadmin [-refreshSuperUserGroupsConfiguration]\");\n" +
            "    } else if (\"-refreshAdminAcls\".equals(cmd)) {\n" +
            "      System.err.println(\"Usage: mapred hsadmin [-refreshAdminAcls]\");\n" +
            "    } else if (\"-refreshLoadedJobCache\".equals(cmd)) {\n" +
            "      System.err.println(\"Usage: mapred hsadmin [-refreshLoadedJobCache]\");\n" +
            "    } else if (\"-refreshJobRetentionSettings\".equals(cmd)) {\n" +
            "      System.err\n" +
            "          .println(\"Usage: mapred hsadmin [-refreshJobRetentionSettings]\");\n" +
            "    } else if (\"-refreshLogRetentionSettings\".equals(cmd)) {\n" +
            "      System.err\n" +
            "          .println(\"Usage: mapred hsadmin [-refreshLogRetentionSettings]\");\n" +
            "    } else if (\"-getGroups\".equals(cmd)) {\n" +
            "      System.err.println(\"Usage: mapred hsadmin\" + \" [-getGroups [username]]\");\n" +
            "    } else {\n" +
            "      System.err.println(\"Usage: mapred hsadmin\");\n" +
            "      System.err.println(\"           [-refreshUserToGroupsMappings]\");\n" +
            "      System.err.println(\"           [-refreshSuperUserGroupsConfiguration]\");\n" +
            "      System.err.println(\"           [-refreshAdminAcls]\");\n" +
            "      System.err.println(\"           [-refreshLoadedJobCache]\");\n" +
            "      System.err.println(\"           [-refreshJobRetentionSettings]\");\n" +
            "      System.err.println(\"           [-refreshLogRetentionSettings]\");\n" +
            "      System.err.println(\"           [-getGroups [username]]\");\n" +
            "      System.err.println(\"           [-help [cmd]]\");\n" +
            "      System.err.println();\n" +
            "      ToolRunner.printGenericCommandUsage(System.err);\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  private static void printHelp(String cmd) {\n" +
            "    String summary = \"hsadmin is the command to execute Job History server administrative commands.\\n\"\n" +
            "        + \"The full syntax is: \\n\\n\"\n" +
            "        + \"mapred hsadmin\"\n" +
            "        + \" [-refreshUserToGroupsMappings]\"\n" +
            "        + \" [-refreshSuperUserGroupsConfiguration]\"\n" +
            "        + \" [-refreshAdminAcls]\"\n" +
            "        + \" [-refreshLoadedJobCache]\"\n" +
            "        + \" [-refreshLogRetentionSettings]\"\n" +
            "        + \" [-refreshJobRetentionSettings]\"\n" +
            "        + \" [-getGroups [username]]\" + \" [-help [cmd]]\\n\";\n" +
            "\n" +
            "    String refreshUserToGroupsMappings = \"-refreshUserToGroupsMappings: Refresh user-to-groups mappings\\n\";\n" +
            "\n" +
            "    String refreshSuperUserGroupsConfiguration = \"-refreshSuperUserGroupsConfiguration: Refresh superuser proxy groups mappings\\n\";\n" +
            "\n" +
            "    String refreshAdminAcls = \"-refreshAdminAcls: Refresh acls for administration of Job history server\\n\";\n" +
            "\n" +
            "    String refreshLoadedJobCache = \"-refreshLoadedJobCache: Refresh loaded job cache of Job history server\\n\";\n" +
            "\n" +
            "    String refreshJobRetentionSettings = \"-refreshJobRetentionSettings:\" + \n" +
            "        \"Refresh job history period,job cleaner settings\\n\";\n" +
            "\n" +
            "    String refreshLogRetentionSettings = \"-refreshLogRetentionSettings:\" + \n" +
            "        \"Refresh log retention period and log retention check interval\\n\";\n" +
            "    \n" +
            "    String getGroups = \"-getGroups [username]: Get the groups which given user belongs to\\n\";\n" +
            "\n" +
            "    String help = \"-help [cmd]: \\tDisplays help for the given command or all commands if none\\n\"\n" +
            "        + \"\\t\\tis specified.\\n\";\n" +
            "\n" +
            "    if (\"refreshUserToGroupsMappings\".equals(cmd)) {\n" +
            "      System.out.println(refreshUserToGroupsMappings);\n" +
            "    } else if (\"help\".equals(cmd)) {\n" +
            "      System.out.println(help);\n" +
            "    } else if (\"refreshSuperUserGroupsConfiguration\".equals(cmd)) {\n" +
            "      System.out.println(refreshSuperUserGroupsConfiguration);\n" +
            "    } else if (\"refreshAdminAcls\".equals(cmd)) {\n" +
            "      System.out.println(refreshAdminAcls);\n" +
            "    } else if (\"refreshLoadedJobCache\".equals(cmd)) {\n" +
            "      System.out.println(refreshLoadedJobCache);\n" +
            "    } else if (\"refreshJobRetentionSettings\".equals(cmd)) {\n" +
            "      System.out.println(refreshJobRetentionSettings);\n" +
            "    } else if (\"refreshLogRetentionSettings\".equals(cmd)) {\n" +
            "      System.out.println(refreshLogRetentionSettings);\n" +
            "    } else if (\"getGroups\".equals(cmd)) {\n" +
            "      System.out.println(getGroups);\n" +
            "    } else {\n" +
            "      System.out.println(summary);\n" +
            "      System.out.println(refreshUserToGroupsMappings);\n" +
            "      System.out.println(refreshSuperUserGroupsConfiguration);\n" +
            "      System.out.println(refreshAdminAcls);\n" +
            "      System.out.println(refreshLoadedJobCache);\n" +
            "      System.out.println(refreshJobRetentionSettings);\n" +
            "      System.out.println(refreshLogRetentionSettings);\n" +
            "      System.out.println(getGroups);\n" +
            "      System.out.println(help);\n" +
            "      System.out.println();\n" +
            "      ToolRunner.printGenericCommandUsage(System.out);\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  private int getGroups(String[] usernames) throws IOException {\n" +
            "    // Get groups users belongs to\n" +
            "    if (usernames.length == 0) {\n" +
            "      usernames = new String[] { UserGroupInformation.getCurrentUser()\n" +
            "          .getUserName() };\n" +
            "    }\n" +
            "\n" +
            "    // Get the current configuration\n" +
            "    Configuration conf = getConf();\n" +
            "\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    GetUserMappingsProtocol getUserMappingProtocol = HSProxies.createProxy(\n" +
            "        conf, address, GetUserMappingsProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "    for (String username : usernames) {\n" +
            "      StringBuilder sb = new StringBuilder();\n" +
            "      sb.append(username + \" :\");\n" +
            "      for (String group : getUserMappingProtocol.getGroupsForUser(username)) {\n" +
            "        sb.append(\" \");\n" +
            "        sb.append(group);\n" +
            "      }\n" +
            "      System.out.println(sb);\n" +
            "    }\n" +
            "\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  private int refreshUserToGroupsMappings() throws IOException {\n" +
            "    // Get the current configuration\n" +
            "    Configuration conf = getConf();\n" +
            "\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    RefreshUserMappingsProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, RefreshUserMappingsProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "    // Refresh the user-to-groups mappings\n" +
            "    refreshProtocol.refreshUserToGroupsMappings();\n" +
            "\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  private int refreshSuperUserGroupsConfiguration() throws IOException {\n" +
            "    // Refresh the super-user groups\n" +
            "    Configuration conf = getConf();\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    RefreshUserMappingsProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, RefreshUserMappingsProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "    // Refresh the super-user group mappings\n" +
            "    refreshProtocol.refreshSuperUserGroupsConfiguration();\n" +
            "\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  private int refreshAdminAcls() throws IOException {\n" +
            "    // Refresh the admin acls\n" +
            "    Configuration conf = getConf();\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    HSAdminRefreshProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, HSAdminRefreshProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "\n" +
            "    refreshProtocol.refreshAdminAcls();\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  private int refreshLoadedJobCache() throws IOException {\n" +
            "    // Refresh the loaded job cache\n" +
            "    Configuration conf = getConf();\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    HSAdminRefreshProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, HSAdminRefreshProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "\n" +
            "    refreshProtocol.refreshLoadedJobCache();\n" +
            "    return 0;\n" +
            "  }\n" +
            "    \n" +
            "  private int refreshJobRetentionSettings() throws IOException {\n" +
            "    // Refresh job retention settings\n" +
            "    Configuration conf = getConf();\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    HSAdminRefreshProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, HSAdminRefreshProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "\n" +
            "    refreshProtocol.refreshJobRetentionSettings();\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  private int refreshLogRetentionSettings() throws IOException {\n" +
            "    // Refresh log retention settings\n" +
            "    Configuration conf = getConf();\n" +
            "    InetSocketAddress address = conf.getSocketAddr(\n" +
            "        JHAdminConfig.JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_ADDRESS,\n" +
            "        JHAdminConfig.DEFAULT_JHS_ADMIN_PORT);\n" +
            "\n" +
            "    HSAdminRefreshProtocol refreshProtocol = HSProxies.createProxy(conf,\n" +
            "        address, HSAdminRefreshProtocol.class,\n" +
            "        UserGroupInformation.getCurrentUser());\n" +
            "\n" +
            "    refreshProtocol.refreshLogRetentionSettings();\n" +
            "    return 0;\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  public int run(String[] args) throws Exception {\n" +
            "    if (args.length < 1) {\n" +
            "      printUsage(\"\");\n" +
            "      return -1;\n" +
            "    }\n" +
            "\n" +
            "    int exitCode = -1;\n" +
            "    int i = 0;\n" +
            "    String cmd = args[i++];\n" +
            "\n" +
            "    if (\"-refreshUserToGroupsMappings\".equals(cmd)\n" +
            "        || \"-refreshSuperUserGroupsConfiguration\".equals(cmd)\n" +
            "        || \"-refreshAdminAcls\".equals(cmd)\n" +
            "        || \"-refreshLoadedJobCache\".equals(cmd)\n" +
            "        || \"-refreshJobRetentionSettings\".equals(cmd)\n" +
            "        || \"-refreshLogRetentionSettings\".equals(cmd)) {\n" +
            "      if (args.length != 1) {\n" +
            "        printUsage(cmd);\n" +
            "        return exitCode;\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "    exitCode = 0;\n" +
            "    if (\"-refreshUserToGroupsMappings\".equals(cmd)) {\n" +
            "      exitCode = refreshUserToGroupsMappings();\n" +
            "    } else if (\"-refreshSuperUserGroupsConfiguration\".equals(cmd)) {\n" +
            "      exitCode = refreshSuperUserGroupsConfiguration();\n" +
            "    } else if (\"-refreshAdminAcls\".equals(cmd)) {\n" +
            "      exitCode = refreshAdminAcls();\n" +
            "    } else if (\"-refreshLoadedJobCache\".equals(cmd)) {\n" +
            "      exitCode = refreshLoadedJobCache();\n" +
            "    } else if (\"-refreshJobRetentionSettings\".equals(cmd)) {\n" +
            "      exitCode = refreshJobRetentionSettings();\n" +
            "    } else if (\"-refreshLogRetentionSettings\".equals(cmd)) {\n" +
            "      exitCode = refreshLogRetentionSettings();\n" +
            "    } else if (\"-getGroups\".equals(cmd)) {\n" +
            "      String[] usernames = Arrays.copyOfRange(args, i, args.length);\n" +
            "      exitCode = getGroups(usernames);\n" +
            "    } else if (\"-help\".equals(cmd)) {\n" +
            "      if (i < args.length) {\n" +
            "        printHelp(args[i]);\n" +
            "      } else {\n" +
            "        printHelp(\"\");\n" +
            "      }\n" +
            "    } else {\n" +
            "      exitCode = -1;\n" +
            "      System.err.println(cmd.substring(1) + \": Unknown command\");\n" +
            "      printUsage(\"\");\n" +
            "    }\n" +
            "    return exitCode;\n" +
            "  }\n" +
            "\n" +
            "  public static void main(String[] args) throws Exception {\n" +
            "    JobConf conf = new JobConf();\n" +
            "    int result = ToolRunner.run(new HSAdmin(conf), args);\n" +
            "    System.exit(result);\n" +
            "  }\n" +
            "}";
    public static final String RPC_METHOD = "public int run(String[] args) throws Exception";

    public static final String TEST_CASE = "public class RPCTest {\n" +
            "    public static void main(String[] args) throws Exception {\n" +
            "        Configuration conf = new Configuration();\n" +
            "\n" +
            "        JobHistoryServer.main(new String[0]);\n" +
            "\n" +
            "        JobConf jobConf = new JobConf(conf);\n" +
            "        HSAdmin admin = new HSAdmin(jobConf);\n" +
            "\n" +
            "        String user = UserGroupInformation.getCurrentUser().getUserName();\n" +
            "        String[] cmdArgs = new String[]{\"-getGroups\", user};\n" +
            "        admin.run(cmdArgs);\n" +
            "    }\n" +
            "}";
}
