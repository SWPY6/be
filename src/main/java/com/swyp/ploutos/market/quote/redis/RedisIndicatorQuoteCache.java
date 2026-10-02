package com.swyp.ploutos.market.quote.redis;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.quote.service.IndicatorQuoteCache;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 지표 현재값을 Redis에 JSON으로 캐시한다. Redis에 접근하지 못하면 {@code MARKET_DATA_UNAVAILABLE}을 던진다 —
 * 캐시 없이 KIS를 직접 부르면 장애 중 요청이 모두 KIS로 몰린다. 저장된 값을 읽지 못하면 캐시 미스로 본다.
 */
@Component
@RequiredArgsConstructor
class RedisIndicatorQuoteCache implements IndicatorQuoteCache {

    private static final Logger log = LoggerFactory.getLogger(RedisIndicatorQuoteCache.class);
    private static final String VALUE_PREFIX = "market-quote:";
    private static final String LOCK_PREFIX = "market-quote:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final IndicatorQuoteCacheProperties properties;

    @Override
    public Optional<IndicatorQuote> find(MarketIndicator indicator) {
        try {
            String json = redisTemplate.opsForValue().get(valueKey(indicator));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, IndicatorQuote.class));
        } catch (JacksonException e) {
            log.warn("저장된 지표 시세를 읽지 못했다. 캐시 미스로 본다. indicator={}", indicator, e);
            return Optional.empty();
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void put(MarketIndicator indicator, IndicatorQuote quote) {
        try {
            redisTemplate.opsForValue()
                    .set(valueKey(indicator), jsonMapper.writeValueAsString(quote), properties.cacheTtl());
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean tryLock(MarketIndicator indicator) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey(indicator), "1", LOCK_TTL));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void unlock(MarketIndicator indicator) {
        try {
            redisTemplate.delete(lockKey(indicator));
        } catch (DataAccessException e) {
            // 해제하지 못해도 락 TTL로 풀린다. 호출자의 결과를 바꿀 이유가 없다.
            log.warn("지표 시세 캐시 락을 해제하지 못했다. TTL로 풀린다. indicator={}", indicator, e);
        }
    }

    private static BusinessException unavailable(DataAccessException e) {
        log.error("지표 시세 캐시 저장소에 접근하지 못했습니다: {}", e.getMessage());
        return new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private static String valueKey(MarketIndicator indicator) {
        return VALUE_PREFIX + indicator.name();
    }

    private static String lockKey(MarketIndicator indicator) {
        return LOCK_PREFIX + indicator.name();
    }
}
