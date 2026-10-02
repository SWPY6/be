package com.swyp.ploutos.news.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
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

    private final StringRedisTemplate redisTemplate;
    private final NewsRedisProperties properties;
    private final Clock clock;

    @Override
    public void consume() {
        long count = increment(todayKey());
        if (count > properties.dailyCallLimit()) {
            log.warn("뉴스 검색 일일 호출 상한을 넘었다. limit={}, count={}", properties.dailyCallLimit(), count);
            throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
        }
    }

    private long increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
            }
            if (count == 1) {
                redisTemplate.expire(key, KEY_TTL);
            }
            return count;
        } catch (DataAccessException e) {
            log.error("뉴스 호출 예산 저장소에 접근하지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
        }
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now(clock.withZone(BUDGET_ZONE)).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
