package com.swyp.ploutos.disclosure.redis;

import static com.swyp.ploutos.disclosure.redis.RedisTemplates.CLOSED_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.REDIS_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
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
import com.swyp.ploutos.external.redis.RedisCounter;

@Testcontainers
class RedisSecCallBudgetTest {

    private static final long PER_SECOND = 2;
    private static final Instant SECOND = Instant.parse("2026-10-02T05:00:00.100Z");
    private static final Instant NEXT_SECOND = Instant.parse("2026-10-02T05:00:01.000Z");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        redisTemplate.delete(List.of(key(SECOND), key(NEXT_SECOND)));
    }

    @Test
    void 같은_초에_상한까지는_허용하고_넘으면_막는다() {
        // given
        RedisSecCallBudget budget = budgetAt(redisTemplate, SECOND);
        budget.consume();
        budget.consume();

        // when & then
        assertQuotaExceeded(budget);
    }

    @Test
    void 초가_바뀌면_다시_센다() {
        // given
        RedisSecCallBudget current = budgetAt(redisTemplate, SECOND);
        current.consume();
        current.consume();
        RedisSecCallBudget next = budgetAt(redisTemplate, NEXT_SECOND);

        // when & then
        assertThatCode(next::consume).doesNotThrowAnyException();
    }

    @Test
    void 호출_수는_인스턴스끼리_공유하고_DART_예산과_다른_키를_쓴다() {
        // given
        budgetAt(redisTemplate, SECOND).consume();
        budgetAt(redisTemplate, SECOND).consume();

        // when & then
        assertQuotaExceeded(budgetAt(redisTemplate, SECOND));
        assertThat(redisTemplate.opsForValue().get(key(SECOND))).isEqualTo("2");
        assertThat(redisTemplate.getExpire(key(SECOND), TimeUnit.SECONDS)).isBetween(1L, 5L);
    }

    @Test
    void Redis에_접근하지_못하면_호출을_막는다() {
        // given
        RedisSecCallBudget broken = budgetAt(template("localhost", CLOSED_PORT), SECOND);

        // when & then
        assertQuotaExceeded(broken);
    }

    @Test
    void 초당_상한이_0이하면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 16_000, 15_000, 0, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.sec-calls-per-second");
    }

    private static String key(Instant instant) {
        return "disclosure:sec:calls:" + instant.getEpochSecond();
    }

    private static void assertQuotaExceeded(RedisSecCallBudget budget) {
        assertThatThrownBy(budget::consume)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    private static RedisSecCallBudget budgetAt(StringRedisTemplate template, Instant now) {
        return new RedisSecCallBudget(
                new RedisCounter(template),
                new DisclosureRedisProperties(600, 16_000, 15_000, PER_SECOND, 24),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }
}
