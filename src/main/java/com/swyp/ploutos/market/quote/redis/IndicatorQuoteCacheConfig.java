package com.swyp.ploutos.market.quote.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link IndicatorQuoteCacheProperties}는 스캔 대상이 아니므로 여기서 등록한다. */
@Configuration
@EnableConfigurationProperties(IndicatorQuoteCacheProperties.class)
class IndicatorQuoteCacheConfig {
}
