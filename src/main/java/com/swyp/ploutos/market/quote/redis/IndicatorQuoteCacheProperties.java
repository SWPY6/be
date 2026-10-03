package com.swyp.ploutos.market.quote.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지표 현재값 캐시 설정. 지표가 5개로 고정이라 갱신 스케줄러가 없고, TTL이 KIS 호출 간격을 정한다.
 */
@ConfigurationProperties(prefix = "ploutos.market-quote")
record IndicatorQuoteCacheProperties(long cacheTtlSeconds) {

    IndicatorQuoteCacheProperties {
        if (cacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.market-quote.cache-ttl-seconds 는 1 이상이어야 합니다.");
        }
    }

    Duration cacheTtl() {
        return Duration.ofSeconds(cacheTtlSeconds);
    }
}
