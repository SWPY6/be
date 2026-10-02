package com.swyp.ploutos.disclosure.redis;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureCache;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공시 목록을 공급자·법인·접수일 범위별로 Redis에 JSON으로 캐시한다. 기간 시각이 아니라 날짜로 키를 잡아
 * 기본 기간(요청 시각 기준)으로 조회해도 같은 날에는 같은 키를 쓴다.
 * Redis에 접근하지 못하면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다 — 호출 예산도 같은 Redis에 있어
 * 어차피 공급자를 부를 수 없다. 저장된 값을 읽지 못하면 캐시 미스로 본다.
 */
@Component
@RequiredArgsConstructor
class RedisDisclosureCache implements DisclosureCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDisclosureCache.class);
    // 저장 형식이 바뀌면 버전을 올려 이전 형식의 값을 읽지 않게 한다.
    private static final String KEY_PREFIX = "disclosure:v2:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final DisclosureRedisProperties properties;

    @Override
    public Optional<CachedSearch> find(DisclosureSource source, String issuerId, FiledDateRange range) {
        try {
            String json = redisTemplate.opsForValue().get(key(source, issuerId, range));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, CachedSearch.class));
        } catch (JacksonException e) {
            log.warn("저장된 공시를 읽지 못했다. 캐시 미스로 본다. source={}, issuerId={}", source, issuerId, e);
            return Optional.empty();
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void put(DisclosureSource source, String issuerId, FiledDateRange range, CachedSearch search) {
        try {
            redisTemplate.opsForValue().set(
                    key(source, issuerId, range), jsonMapper.writeValueAsString(search), properties.cacheTtl()
            );
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private static BusinessException unavailable(DataAccessException e) {
        log.error("공시 캐시 저장소에 접근하지 못했습니다: {}", e.getMessage());
        return new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    static String key(DisclosureSource source, String issuerId, FiledDateRange range) {
        return KEY_PREFIX + source.name().toLowerCase(Locale.ROOT) + ":" + issuerId + ":"
                + range.from().format(DateTimeFormatter.BASIC_ISO_DATE) + ":"
                + range.to().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
