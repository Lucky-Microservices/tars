/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
public class NacosConfigRuleSupport {

	private static final String GROUP_ID = "SENTINEL_GROUP";

	public static final String FLOW_DATA_ID_POSTFIX = "-flow-rules";
	public static final String PARAM_FLOW_DATA_ID_POSTFIX = "-param-rules";
	public static final String CLUSTER_MAP_DATA_ID_POSTFIX = "-cluster-map";
	public static final String DEGRADE_DATA_ID_POSTFIX = "-degrade-rules";
	public static final String AUTHORITY_DATA_ID_POSTFIX = "-authority-rules";
	public static final String GATEWAY_API_DATA_ID_POSTFIX = "-gateway-api-rules";
	public static final String GATEWAY_ROUTE_DATA_ID_POSTFIX = "-gateway-route-rules";
	public static final String SYSTEM_DATA_ID_POSTFIX = "-system-rules";

	/**
	 * cc for `cluster-client`
	 */
	public static final String CLIENT_CONFIG_DATA_ID_POSTFIX = "-cc-config";
	/**
	 * cs for `cluster-server`
	 */
	public static final String SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX = "-cs-transport-config";
	public static final String SERVER_FLOW_CONFIG_DATA_ID_POSTFIX = "-cs-flow-config";
	public static final String SERVER_NAMESPACE_SET_DATA_ID_POSTFIX = "-cs-namespace-set";

	public static ObjectMapper objectMapper;

	/**
	 * 把入参类型为S的值转换成List<T>对象
	 * 
	 * @param configService 配置服务
	 * @param dataId nacos data id
	 * @param <T> 需要转换成的目标对象类型
	 * @return 转换后的对象
	 * @throws NacosException
	 */
	public static <T> List<T> parseRules(ConfigService configService, String dataId,
			Class<T> clazz) throws NacosException {

		String ruleContent = configService.getConfig(dataId, GROUP_ID, 3000);

		if (!StringUtils.hasText(ruleContent)) {
			return new ArrayList<T>();
		}

		return parseObject(clazz, ruleContent);

	}

	/**
	 * 转换参数类型，并且
	 *
	 * @param configService 配置服务
	 * @param dataId nacos data id
	 * @param rules 规则集合
	 * @param <T> 被转换对象类型
	 * @throws NacosException
	 */
	public static <T> void saveRules(ConfigService configService, String dataId,
			List<T> rules) throws NacosException {
		configService.publishConfig(dataId, GROUP_ID, toJsonString(rules));
	}

	private static <T> String toJsonString(T object) {
		try {
			return objectMapper.writeValueAsString(object);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static <T> List<T> parseObject(Class<T> clazz, String ruleContent) {
		JavaType javaType = objectMapper.getTypeFactory()
				.constructParametricType(ArrayList.class, clazz);
		try {
			return objectMapper.readValue(ruleContent, javaType);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
