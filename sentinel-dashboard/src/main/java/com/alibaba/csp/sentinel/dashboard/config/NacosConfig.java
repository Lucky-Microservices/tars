package com.alibaba.csp.sentinel.dashboard.config;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.csp.sentinel.dashboard.util.NacosConfigRuleSupport;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Nacos config class
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @since 1.6.3-ext.RELEASE
 */
@Configuration
@ConfigurationProperties(prefix = "nacos.config")
public class NacosConfig implements ApplicationContextAware {

	private String serverAddr;

	@Bean
	public ConfigService nacosConfigService() throws Exception {
		return ConfigFactory.createConfigService(serverAddr);
	}

	public String getServerAddr() {
		return serverAddr;
	}

	public void setServerAddr(String serverAddr) {
		this.serverAddr = serverAddr;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		NacosConfigRuleSupport.objectMapper=applicationContext.getBean(ObjectMapper.class);
	}
}
