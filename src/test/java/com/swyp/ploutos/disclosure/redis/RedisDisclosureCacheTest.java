package com.swyp.ploutos.disclosure.redis;

import static com.swyp.ploutos.disclosure.redis.RedisTemplates.CLOSED_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.REDIS_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureCache.CachedSearch;
import com.swyp.ploutos.disclosure.service.DisclosureProvider.SearchResult;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisDisclosureCacheTest {

    private static final long TTL_SECONDS = 1;
    private static final String CORP_CODE = "00126380";
    private static final Instant FETCHED_AT = Instant.parse("2026-10-02T04:55:12Z");
    private static final FiledDateRange RANGE =
            new FiledDateRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));
    private static final FiledDateRange OTHER_RANGE =
            new FiledDateRange(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 2));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;
    private RedisDisclosureCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        redisTemplate.delete(List.of(
                RedisDisclosureCache.key(DisclosureSource.DART, CORP_CODE, RANGE),
                RedisDisclosureCache.key(DisclosureSource.DART, CORP_CODE, OTHER_RANGE),
                RedisDisclosureCache.key(DisclosureSource.SEC, CORP_CODE, RANGE)
        ));
        cache = cacheOn(redisTemplate);
    }

    @Test
    void 저장한_목록을_같은_값으로_읽는다() {
        // given
        CachedSearch search = search(false);

        // when
        cache.put(DisclosureSource.DART, CORP_CODE, RANGE, search);

        // then
        assertThat(cache.find(DisclosureSource.DART, CORP_CODE, RANGE)).contains(search);
    }

    @Test
    void 키는_공급자와_법인_ID와_접수일_범위로_구분한다() {
        // given
        cache.put(DisclosureSource.DART, CORP_CODE, RANGE, search(true));

        // when
        Optional<CachedSearch> otherRange = cache.find(DisclosureSource.DART, CORP_CODE, OTHER_RANGE);
        Optional<CachedSearch> otherSource = cache.find(DisclosureSource.SEC, CORP_CODE, RANGE);

        // then
        assertThat(otherRange).isEmpty();
        assertThat(otherSource).isEmpty();
        assertThat(RedisDisclosureCache.key(DisclosureSource.DART, CORP_CODE, RANGE))
                .isEqualTo("disclosure:v2:dart:00126380:20260902:20261002");
    }

    @Test
    void SEC_공시의_접수_시각과_링크_종류도_그대로_읽는다() {
        // given
        Disclosure disclosure = Disclosure.sec(
                "0000320193", "0001140361-26-038028", "4", "FORM 4", "Apple Inc.", "2026-09-29",
                "2026-09-29T22:44:50.000Z", "xslF345X06/form4.xml"
        ).orElseThrow();
        CachedSearch search = new CachedSearch(new SearchResult(List.of(disclosure), true), FETCHED_AT);

        // when
        cache.put(DisclosureSource.SEC, CORP_CODE, RANGE, search);

        // then
        assertThat(cache.find(DisclosureSource.SEC, CORP_CODE, RANGE)).contains(search);
    }

    @Test
    void TTL이_지나면_값이_사라진다() throws InterruptedException {
        // given
        cache.put(DisclosureSource.DART, CORP_CODE, RANGE, search(true));

        // when
        Thread.sleep(TTL_SECONDS * 1_000 + 200);

        // then
        assertThat(cache.find(DisclosureSource.DART, CORP_CODE, RANGE)).isEmpty();
    }

    @Test
    void 저장된_값을_읽지_못하면_캐시_미스로_본다() {
        // given
        redisTemplate.opsForValue().set(RedisDisclosureCache.key(DisclosureSource.DART, CORP_CODE, RANGE), "{broken");

        // when
        Optional<CachedSearch> found = cache.find(DisclosureSource.DART, CORP_CODE, RANGE);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void Redis에_접근하지_못하면_조회와_저장이_실패한다() {
        // given
        RedisDisclosureCache broken = cacheOn(template("localhost", CLOSED_PORT));

        // when & then
        assertQuotaExceeded(() -> broken.find(DisclosureSource.DART, CORP_CODE, RANGE));
        assertQuotaExceeded(() -> broken.put(DisclosureSource.DART, CORP_CODE, RANGE, search(true)));
    }

    private static void assertQuotaExceeded(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    private static CachedSearch search(boolean exhausted) {
        Disclosure disclosure = Disclosure.dart(
                "20260930000123", "[기재정정]분기보고서", "삼성전자", "삼성전자", "유", "20260930"
        ).orElseThrow();
        return new CachedSearch(new SearchResult(List.of(disclosure), exhausted), FETCHED_AT);
    }

    private static RedisDisclosureCache cacheOn(StringRedisTemplate template) {
        return new RedisDisclosureCache(
                template, JsonMapper.builder().build(), new DisclosureRedisProperties(TTL_SECONDS, 16_000, 5, 24)
        );
    }
}
