package com.alibaba.csp.sentinel.dashboard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.dashboard.domain.cluster.ClusterGroupEntity;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import static com.alibaba.csp.sentinel.dashboard.DemoConstants.CLIENT_CONFIG_POSTFIX;

/**
 * Hi,添加点描述吧
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @date 2019/8/25 11:49
 * @since 1.x.x.RELEASE
 */
public class EmbeddedClusterInitFunc implements InitFunc {

	private static final String APP_NAME = AppNameUtil.getAppName();

	private final String remoteAddress = "localhost:8848";
	private final String groupId = "SENTINEL_GROUP";

	private final String configDataId = APP_NAME + CLIENT_CONFIG_POSTFIX;
	private final String clusterMapDataId = APP_NAME + DemoConstants.CLUSTER_MAP_POSTFIX;
	private final String namespaceSetDataId = APP_NAME + DemoConstants.SERVER_NAMESPACE_SET_POSTFIX;
	private final String serverTransportDataId = APP_NAME + "-cs-transport-config";

	@Override
	public void init() throws Exception {

		// Token server transport config extracted from assign map:
		// 初始化Token Server相关配置 比如 token server 端口
		initServerTransportConfigProperty();
        // 初始化一个配置 namespace 的 Nacos 数据源
        ReadableDataSource<String, Set<String>> namespaceDs =
                new NacosDataSource<>(remoteAddress, groupId,
                        namespaceSetDataId, source -> JSON.parseObject(source, new TypeReference<Set<String>>() {}));
        ClusterServerConfigManager.registerNamespaceSetProperty(namespaceDs.getProperty());

		// Register token client related data source.
		// Token client common config:
		initClientConfigProperty();
		// Token client assign config (e.g. target token server) retrieved from assign
		// map:
		initClientServerAssignProperty();

		// Init cluster state property for extracting mode from cluster map data source.
		// 根据状态 启动token client或者token server
		initStateProperty();
	}

	private void initClientConfigProperty() {
		ReadableDataSource<String, ClusterClientConfig> clientConfigDs = new NacosDataSource<>(
				remoteAddress, groupId, configDataId, source -> JSON.parseObject(source,
						new TypeReference<ClusterClientConfig>() {
						}));
		ClusterClientConfigManager
				.registerClientConfigProperty(clientConfigDs.getProperty());
	}

	private void initServerTransportConfigProperty() {
		ReadableDataSource<String, ServerTransportConfig> serverTransportDs = new NacosDataSource<>(
				remoteAddress, groupId, clusterMapDataId, source -> {
					List<ClusterGroupEntity> groupList = JSON.parseObject(source,
							new TypeReference<List<ClusterGroupEntity>>() {
							});
					return Optional.ofNullable(groupList)
							.flatMap(this::extractServerTransportConfig).orElse(null);
				});
		ClusterServerConfigManager
				.registerServerTransportProperty(serverTransportDs.getProperty());
	}

	private void initClientServerAssignProperty() {
		// Cluster map format:
		// [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
		// machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard
		// (transport module)
		ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(
				remoteAddress, groupId, clusterMapDataId, source -> {
					List<ClusterGroupEntity> groupList = JSON.parseObject(source,
							new TypeReference<List<ClusterGroupEntity>>() {
							});
					return Optional.ofNullable(groupList)
							.flatMap(this::extractClientAssignment).orElse(null);
				});
		ClusterClientConfigManager
				.registerServerAssignProperty(clientAssignDs.getProperty());
	}

	private void initStateProperty() {
		// Cluster map format:
		// [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
		// machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard
		// (transport module)
		ReadableDataSource<String, Integer> clusterModeDs = new NacosDataSource<>(
				remoteAddress, groupId, clusterMapDataId, source -> {
					List<ClusterGroupEntity> groupList = JSON.parseObject(source,
							new TypeReference<List<ClusterGroupEntity>>() {
							});
					return Optional.ofNullable(groupList).map(this::extractMode)
							.orElse(ClusterStateManager.CLUSTER_NOT_STARTED);
				});
		ClusterStateManager.registerProperty(clusterModeDs.getProperty());
	}

	private int extractMode(List<ClusterGroupEntity> groupList) {
		// If any server group machineId matches current, then it's token server.
		if (groupList.stream().anyMatch(this::machineEqual)) {
			return ClusterStateManager.CLUSTER_SERVER;
		}
		// If current machine belongs to any of the token server group, then it's token
		// client.
		// Otherwise it's unassigned, should be set to NOT_STARTED.
		boolean canBeClient = groupList.stream().flatMap(e -> e.getClientSet().stream())
				.filter(Objects::nonNull).anyMatch(e -> e.equals(getCurrentMachineId()));
		return canBeClient ? ClusterStateManager.CLUSTER_CLIENT
				: ClusterStateManager.CLUSTER_NOT_STARTED;
	}

	private Optional<ServerTransportConfig> extractServerTransportConfig(
			List<ClusterGroupEntity> groupList) {
		return groupList.stream().filter(this::machineEqual).findAny()
				.map(e -> new ServerTransportConfig().setPort(e.getPort())
						// todo 配置中获取
						.setIdleSeconds(600));
	}

	private Optional<ClusterClientAssignConfig> extractClientAssignment(
			List<ClusterGroupEntity> groupList) {
		if (groupList.stream().anyMatch(this::machineEqual)) {
			return Optional.empty();
		}
		// Build client assign config from the client set of target server group.
		for (ClusterGroupEntity group : groupList) {
			if (group.getClientSet().contains(getCurrentMachineId())) {
				String ip = group.getIp();
				Integer port = group.getPort();
				return Optional.of(new ClusterClientAssignConfig(ip, port));
			}
		}
		return Optional.empty();
	}

	private boolean machineEqual(/* @Valid */ ClusterGroupEntity group) {
		return getCurrentMachineId().equals(group.getMachineId());
	}

	private String getCurrentMachineId() {
		// Note: thisDemoConstants may not work well for container-based env.
		// todo 修改获取IP 方式
		return HostNameUtil.getIp() + SEPARATOR + TransportConfig.getRuntimePort();
	}

	private static final String SEPARATOR = "@";

}
