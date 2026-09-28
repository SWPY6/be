package com.swyp.ploutos.stock.quote.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 현재가 캐시 설정. 값 TTL은 갱신 주기보다 길어야 갱신 사이에 값이 사라지지 않는다.
 * 활성 창 안에 조회된 종목만 갱신 대상이다.
 */
@ConfigurationProperties(prefix = "ploutos.quote")
record QuoteCacheProperties(long cacheTtlSeconds, long activeWindowSeconds) {

    QuoteCacheProperties {
        if (cacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.quote.cache-ttl-seconds 는 1 이상이어야 합니다.");
        }
        if (activeWindowSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.quote.active-window-seconds 는 1 이상이어야 합니다.");
        }
    }

    Duration cacheTtl() {
        return Duration.ofSeconds(cacheTtlSeconds);
    }

    Duration activeWindow() {
        return Duration.ofSeconds(activeWindowSeconds);
    }
}
