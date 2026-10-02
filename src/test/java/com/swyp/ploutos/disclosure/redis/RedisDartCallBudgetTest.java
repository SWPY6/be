package com.swyp.ploutos.disclosure.redis;

import static com.swyp.ploutos.disclosure.redis.RedisTemplates.CLOSED_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.REDIS_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.template;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

@Testcontainers
class RedisDartCallBudgetTest {

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
        redisTemplate.delete(List.of("disclosure:dart:calls:20260930", "disclosure:dart:calls:20261001"));
    }

    @Test
    void 한도까지는_호출을_허용한다() {
        // given
        RedisDartCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

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
        RedisDartCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        for (int i = 0; i < LIMIT; i++) {
            budget.consume();
        }

        // when & then
        assertQuotaExceeded(budget);
    }

    @Test
    void 뉴스_예산과_다른_키로_센다() {
        // given
        RedisDartCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when
        budget.consume();

        // then
        assertThat(redisTemplate.opsForValue().get("disclosure:dart:calls:20260930")).isEqualTo("1");
        assertThat(redisTemplate.hasKey("news:naver:calls:20260930")).isFalse();
    }

    @Test
    void 한국시간_자정이_지나면_새_날짜로_다시_센다() {
        // given
        RedisDartCallBudget yesterday = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);
        for (int i = 0; i < LIMIT; i++) {
            yesterday.consume();
        }
        RedisDartCallBudget today = budgetAt(redisTemplate, MIDNIGHT_KST);

        // when & then
        assertThatCode(today::consume).doesNotThrowAnyException();
        assertThat(redisTemplate.opsForValue().get("disclosure:dart:calls:20261001")).isEqualTo("1");
    }

    @Test
    void 날짜별_카운터는_만료된다() {
        // given
        RedisDartCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when
        budget.consume();

        // then
        Long ttl = redisTemplate.getExpire("disclosure:dart:calls:20260930", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofDays(1).toSeconds(), Duration.ofDays(2).toSeconds());
    }

    @Test
    void Redis에_접근하지_못하면_호출을_막는다() {
        // given
        RedisDartCallBudget broken = budgetAt(template("localhost", CLOSED_PORT), BEFORE_MIDNIGHT_KST);

        // when & then
        assertQuotaExceeded(broken);
    }

    @Test
    void 설정값이_0이하면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 0, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.daily-call-limit");
        assertThatThrownBy(() -> new DisclosureRedisProperties(0, 16_000, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.cache-ttl-seconds");
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 16_000, 5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.issuer-code-refresh-hours");
    }

    private static void assertQuotaExceeded(RedisDartCallBudget budget) {
        assertThatThrownBy(budget::consume)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    private static RedisDartCallBudget budgetAt(StringRedisTemplate template, Instant now) {
        return new RedisDartCallBudget(
                template,
                new DisclosureRedisProperties(600, LIMIT, 5, 24),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }
}
