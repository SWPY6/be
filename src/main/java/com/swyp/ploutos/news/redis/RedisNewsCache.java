package com.swyp.ploutos.news.redis;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.news.service.NewsCache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 뉴스 검색 결과를 Redis에 JSON으로 캐시한다. Redis에 접근하지 못하면 {@code NEWS_QUOTA_EXCEEDED}를 던진다 —
 * 호출 예산도 같은 Redis에 있어 어차피 네이버를 부를 수 없다. 저장된 값을 읽지 못하면 캐시 미스로 본다.
 */
@Component
class RedisNewsCache implements NewsCache {

    private static final Logger log = LoggerFactory.getLogger(RedisNewsCache.class);
    // 저장 형식이 바뀌면 버전을 올려 이전 형식의 값을 읽지 않게 한다.
    private static final String KEY_PREFIX = "news:v1:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final NewsRedisProperties properties;

    // Jackson은 읽을 때 시각을 컨텍스트 타임존으로 옮긴다. 그대로 두면 캐시에서 읽은 기사만
    // 오프셋이 UTC로 바뀐다.
    RedisNewsCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper, NewsRedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper.rebuild()
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        this.properties = properties;
    }

    @Override
    public Optional<CachedSearch> find(Long stockId) {
        try {
            String json = redisTemplate.opsForValue().get(key(stockId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, CachedSearch.class));
        } catch (JacksonException e) {
            log.warn("저장된 뉴스를 읽지 못했다. 캐시 미스로 본다. stockId={}", stockId, e);
            return Optional.empty();
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void put(Long stockId, CachedSearch search) {
        try {
            redisTemplate.opsForValue().set(key(stockId), jsonMapper.writeValueAsString(search), properties.cacheTtl());
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private static BusinessException unavailable(DataAccessException e) {
        log.error("뉴스 캐시 저장소에 접근하지 못했습니다: {}", e.getMessage());
        return new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
    }

    private static String key(Long stockId) {
        return KEY_PREFIX + stockId;
    }
}
