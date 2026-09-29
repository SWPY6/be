package com.swyp.ploutos.news.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.service.NewsCache.CachedSearch;
import com.swyp.ploutos.news.service.NewsProvider.SearchResult;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisNewsCacheTest {

    private static final int REDIS_PORT = 6379;
    private static final int CLOSED_PORT = 6390;
    private static final long TTL_SECONDS = 1;
    private static final Instant FETCHED_AT = Instant.parse("2026-09-30T04:55:12Z");
    private static final OffsetDateTime PUBLISHED_AT =
            OffsetDateTime.of(2026, 9, 30, 9, 12, 0, 0, ZoneOffset.ofHours(9));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;
    private RedisNewsCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        redisTemplate.delete(List.of("news:v1:1", "news:v1:2", "news:v1:3"));
        cache = cacheOn(redisTemplate);
    }

    @Test
    void 저장한_검색_결과를_같은_값으로_읽는다() {
        // given
        CachedSearch search = search(true);

        // when
        cache.put(1L, search);

        // then
        assertThat(cache.find(1L)).contains(search);
    }

    @Test
    void 캐시에서_읽어도_기사_시각의_오프셋과_수집_시각이_그대로다() {
        // given
        cache.put(1L, search(false));

        // when
        CachedSearch found = cache.find(1L).orElseThrow();

        // then
        assertThat(found.result().articles().getFirst().publishedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(found.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(found.result().exhausted()).isFalse();
    }

    @Test
    void 저장한_적_없으면_캐시_미스다() {
        // when
        Optional<CachedSearch> found = cache.find(2L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void TTL이_지나면_값이_사라진다() throws InterruptedException {
        // given
        cache.put(3L, search(true));

        // when
        Thread.sleep(TTL_SECONDS * 1_000 + 200);

        // then
        assertThat(cache.find(3L)).isEmpty();
    }

    @Test
    void 저장된_값을_읽지_못하면_캐시_미스로_본다() {
        // given
        redisTemplate.opsForValue().set("news:v1:1", "{broken");

        // when
        Optional<CachedSearch> found = cache.find(1L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void Redis에_접근하지_못하면_조회와_저장이_실패한다() {
        // given
        RedisNewsCache broken = cacheOn(template("localhost", CLOSED_PORT));

        // when & then
        assertQuotaExceeded(() -> broken.find(1L));
        assertQuotaExceeded(() -> broken.put(1L, search(true)));
    }

    private static void assertQuotaExceeded(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NEWS_QUOTA_EXCEEDED);
    }

    private static CachedSearch search(boolean exhausted) {
        NewsArticle article = NewsArticle.from(
                "<b>삼성전자</b> 증설", "요약", "https://news.mt.co.kr/mtview.php?no=1", null, PUBLISHED_AT
        ).orElseThrow();
        return new CachedSearch(new SearchResult(List.of(article), exhausted), FETCHED_AT);
    }

    private static RedisNewsCache cacheOn(StringRedisTemplate template) {
        return new RedisNewsCache(template, JsonMapper.builder().build(), new NewsRedisProperties(TTL_SECONDS, 20_000));
    }

    private static StringRedisTemplate template(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
