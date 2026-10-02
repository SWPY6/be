package com.swyp.ploutos.disclosure.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공시 캐시·호출 예산·법인 매핑 갱신 주기 설정. 상한은 공급자 한도보다 낮게 잡아 여유를 둔다.
 *
 * @param dailyCallLimit    DART 일일 호출 상한
 * @param secCallsPerSecond SEC 초당 호출 상한(서버 합산)
 */
@ConfigurationProperties(prefix = "ploutos.disclosure")
record DisclosureRedisProperties(
        long cacheTtlSeconds, long dailyCallLimit, long secCallsPerSecond, long issuerCodeRefreshHours
) {

    DisclosureRedisProperties {
        if (cacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.cache-ttl-seconds 는 1 이상이어야 합니다.");
        }
        if (dailyCallLimit <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.daily-call-limit 는 1 이상이어야 합니다.");
        }
        if (secCallsPerSecond <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.sec-calls-per-second 는 1 이상이어야 합니다.");
        }
        if (issuerCodeRefreshHours <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.issuer-code-refresh-hours 는 1 이상이어야 합니다.");
        }
    }

    Duration cacheTtl() {
        return Duration.ofSeconds(cacheTtlSeconds);
    }

    Duration issuerCodeRefreshInterval() {
        return Duration.ofHours(issuerCodeRefreshHours);
    }
}
