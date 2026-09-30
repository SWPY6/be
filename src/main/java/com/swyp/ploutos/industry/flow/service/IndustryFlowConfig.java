package com.swyp.ploutos.industry.flow.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link IndustryFlowProperties}는 스캔 대상이 아니므로 여기서 등록한다. */
@Configuration
@EnableConfigurationProperties(IndustryFlowProperties.class)
class IndustryFlowConfig {
}
