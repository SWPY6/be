package com.swyp.ploutos.disclosure.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공시 캐시·호출 예산·법인 매핑 갱신 주기 설정. 상한은 공급자 한도보다 낮게 잡아 여유를 둔다.
 *
 * @param dailyCallLimit       DART 일일 호출 상한
 * @param dailySearchCallLimit DART 공시검색 일일 상한. 같은 카운터에서 이 값을 넘으면 공시검색만 멈추고,
 *                             {@code dailyCallLimit}까지 남은 몫은 법인 매핑 갱신(고유번호 파일)에 남긴다.
 */
@ConfigurationProperties(prefix = "ploutos.disclosure")
record DisclosureRedisProperties(
        long cacheTtlSeconds, long dailyCallLimit, long dailySearchCallLimit, long issuerCodeRefreshHours
) {

    DisclosureRedisProperties {
        if (cacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.cache-ttl-seconds 는 1 이상이어야 합니다.");
        }
        if (dailyCallLimit <= 0) {
            throw new IllegalArgumentException("ploutos.disclosure.daily-call-limit 는 1 이상이어야 합니다.");
        }
        if (dailySearchCallLimit <= 0 || dailySearchCallLimit >= dailyCallLimit) {
            throw new IllegalArgumentException(
                    "ploutos.disclosure.daily-search-call-limit 는 1 이상, daily-call-limit 미만이어야 합니다."
            );
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
