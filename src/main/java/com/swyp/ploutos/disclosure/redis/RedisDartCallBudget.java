package com.swyp.ploutos.disclosure.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.external.redis.RedisCounter;

/**
 * 날짜(KST)별 Redis 카운터로 DART 일일 호출 수를 센다. 여러 인스턴스가 같은 카운터를 공유한다.
 * 공시검색과 고유번호 파일은 같은 카운터를 쓰되 상한이 다르다 — 공시검색이 낮은 상한에서 멈춰
 * 남은 몫이 법인 매핑 갱신에 남는다. 상한마다 하나씩 {@link DisclosureRedisConfig}에서 만든다.
 * Redis에 접근하지 못하면 호출 수를 알 수 없으므로 막는다 — 열어 두면 장애 중 호출이 한도 없이 나간다.
 */
class RedisDartCallBudget implements DisclosureCallBudget {

    private static final Logger log = LoggerFactory.getLogger(RedisDartCallBudget.class);
    private static final String KEY_PREFIX = "disclosure:dart:calls:";
    private static final ZoneId BUDGET_ZONE = ZoneId.of("Asia/Seoul");
    // 날짜가 바뀐 뒤에도 전날 카운터를 확인할 수 있게 하루 더 남긴다.
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final RedisCounter redisCounter;
    private final long limit;
    private final Clock clock;

    RedisDartCallBudget(RedisCounter redisCounter, long limit, Clock clock) {
        this.redisCounter = redisCounter;
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    public void consume() {
        OptionalLong count = increment(todayKey());
        if (count.isEmpty()) {
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
        // 상한에 닿는 마지막 허용 호출에서만 남긴다. 거절될 때마다 남기면 그날 내내 같은 로그가 쌓인다.
        if (count.getAsLong() == limit) {
            log.warn("DART 일일 호출 상한에 닿았다. 이후 호출은 막는다. limit={}", limit);
        }
    }

    private OptionalLong increment(String key) {
        try {
            return redisCounter.increment(key, limit, KEY_TTL);
        } catch (DataAccessException e) {
            log.error("공시 호출 예산 저장소에 접근하지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now(clock.withZone(BUDGET_ZONE)).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
