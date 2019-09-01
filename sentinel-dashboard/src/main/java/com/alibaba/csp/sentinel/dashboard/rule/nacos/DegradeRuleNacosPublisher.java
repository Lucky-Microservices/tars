package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import static com.alibaba.csp.sentinel.dashboard.util.NacosConfigRuleSupport.DEGRADE_DATA_ID_POSTFIX;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.dashboard.util.NacosConfigRuleSupport;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.nacos.api.config.ConfigService;

/**
 * Hi,添加点描述吧
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @date 2019/8/24 22:37
 * @since 1.x.x.RELEASE
 */
@Component("degradeRuleNacosPublisher")
public class DegradeRuleNacosPublisher
		implements DynamicRulePublisher<List<DegradeRuleEntity>> {

	@Autowired
	private ConfigService configService;

	@Override
	public void publish(String appName, List<DegradeRuleEntity> rules) throws Exception {
		AssertUtil.notEmpty(appName, "app name cannot be empty");
		NacosConfigRuleSupport.saveRules(configService, appName + DEGRADE_DATA_ID_POSTFIX,
				rules);
	}

}
