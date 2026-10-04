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
import com.swyp.ploutos.external.redis.RedisCounter;

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
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 0, 1, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.daily-call-limit");
        assertThatThrownBy(() -> new DisclosureRedisProperties(0, 16_000, 15_000, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.cache-ttl-seconds");
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 16_000, 15_000, 5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.issuer-code-refresh-hours");
    }

    @Test
    void 공시검색_상한이_전체_상한_이상이면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 16_000, 16_000, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.daily-search-call-limit");
        assertThatThrownBy(() -> new DisclosureRedisProperties(600, 16_000, 0, 5, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.disclosure.daily-search-call-limit");
    }

    @Test
    void 공시검색_상한에_걸려도_같은_카운터의_전체_상한까지는_법인_매핑_갱신이_호출할_수_있다() {
        // given
        RedisDartCallBudget search = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST, LIMIT - 1);
        RedisDartCallBudget all = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST, LIMIT);
        for (int i = 0; i < LIMIT - 1; i++) {
            search.consume();
        }

        // when
        for (int i = 0; i < 5; i++) {
            assertQuotaExceeded(search);
        }

        // then
        assertThatCode(all::consume).doesNotThrowAnyException();
        assertQuotaExceeded(all);
        assertThat(redisTemplate.opsForValue().get("disclosure:dart:calls:20260930")).isEqualTo(String.valueOf(LIMIT));
    }

    @Test
    void 만료_없이_남은_카운터도_다음_호출에서_만료를_건다() {
        // given
        redisTemplate.opsForValue().set("disclosure:dart:calls:20260930", "1");
        RedisDartCallBudget budget = budgetAt(redisTemplate, BEFORE_MIDNIGHT_KST);

        // when
        budget.consume();

        // then
        assertThat(redisTemplate.opsForValue().get("disclosure:dart:calls:20260930")).isEqualTo("2");
        Long ttl = redisTemplate.getExpire("disclosure:dart:calls:20260930", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofDays(1).toSeconds(), Duration.ofDays(2).toSeconds());
    }

    private static void assertQuotaExceeded(RedisDartCallBudget budget) {
        assertThatThrownBy(budget::consume)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    private static RedisDartCallBudget budgetAt(StringRedisTemplate template, Instant now) {
        return budgetAt(template, now, LIMIT);
    }

    private static RedisDartCallBudget budgetAt(StringRedisTemplate template, Instant now, long limit) {
        return new RedisDartCallBudget(new RedisCounter(template), limit, Clock.fixed(now, ZoneOffset.UTC));
    }
}
