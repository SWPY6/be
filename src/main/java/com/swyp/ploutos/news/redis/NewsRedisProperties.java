package com.swyp.ploutos.news.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 뉴스 캐시·호출 예산 설정. 일일 상한은 공급자 한도보다 낮게 잡아 여유를 둔다.
 */
@ConfigurationProperties(prefix = "ploutos.news")
record NewsRedisProperties(long cacheTtlSeconds, long dailyCallLimit) {

    NewsRedisProperties {
        if (cacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.news.cache-ttl-seconds 는 1 이상이어야 합니다.");
        }
        if (dailyCallLimit <= 0) {
            throw new IllegalArgumentException("ploutos.news.daily-call-limit 는 1 이상이어야 합니다.");
        }
    }

    Duration cacheTtl() {
        return Duration.ofSeconds(cacheTtlSeconds);
    }
}
