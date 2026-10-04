package com.swyp.ploutos.news.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.redis.RedisCounter;
import com.swyp.ploutos.news.service.NewsCallBudget;

import lombok.RequiredArgsConstructor;

/**
 * 날짜(KST)별 Redis 카운터로 일일 호출 수를 센다. 여러 인스턴스가 같은 카운터를 공유한다.
 * Redis에 접근하지 못하면 호출 수를 알 수 없으므로 막는다 — 열어 두면 장애 중 호출이 한도 없이 나간다.
 */
@Component
@RequiredArgsConstructor
class RedisNewsCallBudget implements NewsCallBudget {

    private static final Logger log = LoggerFactory.getLogger(RedisNewsCallBudget.class);
    private static final String KEY_PREFIX = "news:naver:calls:";
    private static final ZoneId BUDGET_ZONE = ZoneId.of("Asia/Seoul");
    // 날짜가 바뀐 뒤에도 전날 카운터를 확인할 수 있게 하루 더 남긴다.
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final RedisCounter redisCounter;
    private final NewsRedisProperties properties;
    private final Clock clock;

    @Override
    public void consume() {
        OptionalLong count = increment(todayKey());
        if (count.isEmpty()) {
            throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
        }
        // 상한에 닿는 마지막 허용 호출에서만 남긴다. 거절될 때마다 남기면 그날 내내 같은 로그가 쌓인다.
        if (count.getAsLong() == properties.dailyCallLimit()) {
            log.warn("뉴스 검색 일일 호출 상한에 닿았다. 이후 호출은 막는다. limit={}", properties.dailyCallLimit());
        }
    }

    private OptionalLong increment(String key) {
        try {
            return redisCounter.increment(key, properties.dailyCallLimit(), KEY_TTL);
        } catch (DataAccessException e) {
            // 같은 Redis를 먼저 조회하는 뉴스 캐시가 접근 실패를 이미 기록하므로 여기서는 남기지 않는다.
            throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
        }
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now(clock.withZone(BUDGET_ZONE)).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
