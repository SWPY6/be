package com.swyp.ploutos.news.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.swyp.ploutos.external.redis.RedisCounter;

@Testcontainers
class RedisNewsCallBudgetTest {

    private static final int REDIS_PORT = 6379;
    private static final int CLOSED_PORT = 6390;
    private static final long LIMIT = 3;
    // 2026-09-30 23:59:59 KST
    private static final Instant BEFORE_MIDNIGHT_KST = Instant.parse("2026-09-30T14:59:59Z");
    // 2026-10-01 00:00:00 KST
    private static final Instant MIDNIGHT_KST = Instant.parse("2026-09-30T15:00:00Z");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        redisTemplate.delete(List.of("news:naver:calls:20260930", "news:naver:calls:20261001"));
    }

    @Test
    void 한도까지는_호출을_허용한다() {
        // given
        RedisNewsCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when & then
        assertThatCode(() -> {
            for (int i = 0; i < LIMIT; i++) {
                budget.consume();
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void 한도를_넘으면_호출을_막는다() {
        // given
        RedisNewsCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        for (int i = 0; i < LIMIT; i++) {
            budget.consume();
        }

        // when & then
        assertQuotaExceeded(budget);
    }

    @Test
    void 호출_수는_인스턴스끼리_공유한다() {
        // given
        RedisNewsCallBudget first = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        RedisNewsCallBudget second = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        first.consume();
        first.consume();
        second.consume();

        // when & then
        assertQuotaExceeded(second);
    }

    @Test
    void 한국시간_자정이_지나면_새_날짜로_다시_센다() {
        // given
        RedisNewsCallBudget yesterday = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        for (int i = 0; i < LIMIT; i++) {
            yesterday.consume();
        }
        RedisNewsCallBudget today = budgetAt(redisTemplate, MIDNIGHT_KST);

        // when & then
        assertThatCode(today::consume).doesNotThrowAnyException();
        assertThat(redisTemplate.opsForValue().get("news:naver:calls:20261001")).isEqualTo("1");
    }

    @Test
    void 날짜별_카운터는_만료된다() {
        // given
        RedisNewsCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when
        budget.consume();

        // then
        Long ttl = redisTemplate.getExpire("news:naver:calls:20260930", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofDays(1).toSeconds(), Duration.ofDays(2).toSeconds());
    }

    @Test
    void 상한에_걸린_호출은_세지_않는다() {
        // given
        RedisNewsCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        for (int i = 0; i < LIMIT; i++) {
            budget.consume();
        }

        // when
        for (int i = 0; i < 3; i++) {
            assertQuotaExceeded(budget);
        }

        // then
        assertThat(redisTemplate.opsForValue().get("news:naver:calls:20260930")).isEqualTo(String.valueOf(LIMIT));
    }

    @Test
    void 만료_없이_남은_카운터도_다음_호출에서_만료를_건다() {
        // given
        redisTemplate.opsForValue().set("news:naver:calls:20260930", "1");
        RedisNewsCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when
        budget.consume();

        // then
        assertThat(redisTemplate.opsForValue().get("news:naver:calls:20260930")).isEqualTo("2");
        Long ttl = redisTemplate.getExpire("news:naver:calls:20260930", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofDays(1).toSeconds(), Duration.ofDays(2).toSeconds());
    }

    @Test
    void Redis에_접근하지_못하면_호출을_막는다() {
        // given
        RedisNewsCallBudget broken = budgetAt(template("localhost", CLOSED_PORT), BEFORE_MIDNIGHT_KST);

        // when & then
        assertQuotaExceeded(broken);
    }

    @Test
    void 일일_상한이_0이하면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new NewsRedisProperties(600, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.news.daily-call-limit");
    }

    private static void assertQuotaExceeded(RedisNewsCallBudget budget) {
        assertThatThrownBy(budget::consume)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NEWS_QUOTA_EXCEEDED);
    }

    private static RedisNewsCallBudget budgetAt(StringRedisTemplate template, Instant now) {
        return new RedisNewsCallBudget(
                new RedisCounter(template),
                new NewsRedisProperties(600, LIMIT),
                Clock.fixed(now, ZoneOffset.UTC)
        );
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
