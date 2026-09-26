package com.swyp.ploutos.stock.quote.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.QuoteCache;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 현재가를 Redis에 JSON으로 캐시한다. Redis에 접근하지 못하면 {@code MARKET_DATA_UNAVAILABLE}을 던진다 —
 * 캐시 없이 KIS를 직접 부르면 장애 중 요청이 모두 KIS로 몰린다. 저장된 값을 읽지 못하면 캐시 미스로 본다.
 */
@Component
@RequiredArgsConstructor
class RedisQuoteCache implements QuoteCache {

    private static final Logger log = LoggerFactory.getLogger(RedisQuoteCache.class);
    private static final String VALUE_PREFIX = "quote:";
    private static final String LOCK_PREFIX = "quote:lock:";
    private static final String ACTIVE_KEY = "quote:active";
    private static final String REFRESH_LOCK_KEY = "quote:refresh:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(9);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final QuoteCacheProperties properties;
    private final Clock clock;

    @Override
    public Optional<Quote> find(Long stockId) {
        try {
            String json = redisTemplate.opsForValue().get(valueKey(stockId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, Quote.class));
        } catch (JacksonException e) {
            log.warn("저장된 시세를 읽지 못했다. 캐시 미스로 본다. stockId={}", stockId, e);
            return Optional.empty();
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void put(Long stockId, Quote quote) {
        try {
            redisTemplate.opsForValue().set(valueKey(stockId), jsonMapper.writeValueAsString(quote), properties.cacheTtl());
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean tryLock(Long stockId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey(stockId), "1", LOCK_TTL));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void unlock(Long stockId) {
        try {
            redisTemplate.delete(lockKey(stockId));
        } catch (DataAccessException e) {
            // 해제하지 못해도 락 TTL로 풀린다. 호출자의 결과를 바꿀 이유가 없다.
            log.warn("시세 캐시 락을 해제하지 못했다. TTL로 풀린다. stockId={}", stockId, e);
        }
    }

    @Override
    public void markActive(Long stockId) {
        try {
            redisTemplate.opsForZSet().add(ACTIVE_KEY, stockId.toString(), clock.millis());
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public List<Long> activeStockIds() {
        try {
            long cutoff = clock.millis() - properties.activeWindow().toMillis();
            redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_KEY, Double.NEGATIVE_INFINITY, cutoff);
            Set<String> members = redisTemplate.opsForZSet().range(ACTIVE_KEY, 0, -1);
            if (members == null) {
                return List.of();
            }
            return members.stream().map(Long::valueOf).toList();
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean tryRefreshLeadership() {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(REFRESH_LOCK_KEY, "1", REFRESH_LOCK_TTL));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private static BusinessException unavailable(DataAccessException e) {
        log.error("시세 캐시 저장소에 접근하지 못했습니다: {}", e.getMessage());
        return new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private static String valueKey(Long stockId) {
        return VALUE_PREFIX + stockId;
    }

    private static String lockKey(Long stockId) {
        return LOCK_PREFIX + stockId;
    }
}
