package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import static com.alibaba.csp.sentinel.dashboard.util.NacosConfigRuleSupport.PARAM_FLOW_DATA_ID_POSTFIX;

import java.util.List;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.ParamFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.util.NacosConfigRuleSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.nacos.api.config.ConfigService;

/**
 * Nacos {@link FlowRule} implement
 *
 * @author <a href="mailto:jiashuai.xie01@gmail.com">Xiejiashuai</a>
 * @date 2019/8/24 21:28
 * @since 1.6.3-ext.RELEASE
 */
@Component("parmFlowRuleNacosProvider")
public class ParmFlowRuleNacosProvider implements DynamicRuleProvider<List<ParamFlowRuleEntity>> {

	@Autowired
	private ConfigService configService;

	@Override
	public List<ParamFlowRuleEntity> getRules(String appName) throws Exception {
		AssertUtil.notEmpty(appName, "app name cannot be empty");
		return NacosConfigRuleSupport.parseRules(configService, appName + PARAM_FLOW_DATA_ID_POSTFIX,
				ParamFlowRuleEntity.class);
	}

}
