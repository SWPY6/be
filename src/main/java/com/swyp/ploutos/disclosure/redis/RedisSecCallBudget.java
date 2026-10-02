package com.swyp.ploutos.disclosure.redis;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;

import lombok.RequiredArgsConstructor;

/**
 * 초(epoch second)별 Redis 카운터로 SEC 호출 수를 센다. SEC 상한은 서버 수와 관계없이 합산이므로
 * 여러 인스턴스가 같은 카운터를 공유한다. 상한을 넘으면 기다리지 않고 막는다.
 * Redis에 접근하지 못하면 호출 수를 알 수 없으므로 막는다.
 */
@Component("secCallBudget")
@RequiredArgsConstructor
class RedisSecCallBudget implements DisclosureCallBudget {

    private static final Logger log = LoggerFactory.getLogger(RedisSecCallBudget.class);
    private static final String KEY_PREFIX = "disclosure:sec:calls:";
    // 초가 바뀐 뒤 늦게 도착한 증가도 같은 키에 남도록 조금 더 둔다.
    private static final Duration KEY_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final DisclosureRedisProperties properties;
    private final Clock clock;

    @Override
    public void consume() {
        String key = KEY_PREFIX + clock.instant().getEpochSecond();
        long count = increment(key);
        if (count > properties.secCallsPerSecond()) {
            log.warn("SEC 초당 호출 상한을 넘었다. limit={}, count={}", properties.secCallsPerSecond(), count);
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
    }

    private long increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
            }
            if (count == 1) {
                redisTemplate.expire(key, KEY_TTL);
            }
            return count;
        } catch (DataAccessException e) {
            log.error("SEC 호출 예산 저장소에 접근하지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
    }
}
