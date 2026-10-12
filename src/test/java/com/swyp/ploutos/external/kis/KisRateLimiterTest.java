package com.swyp.ploutos.external.kis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * 간격 규칙만 검증한다. 실제로 기다리지는 않는다 — {@code reserve}가 현재 시각을 인자로 받으므로
 * 시계 없이 볼 수 있다. 시간을 재는 테스트는 느리고 기계 사정에 따라 깨진다.
 */
class KisRateLimiterTest {

    private static final String BASE_URL = "https://kis.test";
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long NOW = 1_000_000_000L;

    @Test
    void 연속_호출은_설정한_간격만큼_벌어진다() {
        // given 초당 10건이면 간격은 100ms 다
        KisRateLimiter limiter = limiterOf(10);

        // when 같은 시각에 세 번 순번을 잡는다
        long first = limiter.reserve(NOW);
        long second = limiter.reserve(NOW);
        long third = limiter.reserve(NOW);

        // then 먼저 온 호출은 바로 가고, 뒤는 간격만큼 밀린다
        assertThat(first).isEqualTo(NOW);
        assertThat(second - first).isEqualTo(SECOND / 10);
        assertThat(third - second).isEqualTo(SECOND / 10);
    }

    @Test
    void 드문드문_부르면_기다리지_않는다() {
        // given 한 번 부르고 간격보다 훨씬 오래 쉰다
        KisRateLimiter limiter = limiterOf(10);
        limiter.reserve(NOW);

        // when
        long slot = limiter.reserve(NOW + SECOND);

        // then 밀리지 않고 지금이 순번이다
        assertThat(slot).isEqualTo(NOW + SECOND);
    }

    @Test
    void 초당_호출_수를_올리면_간격이_좁아진다() {
        // given
        KisRateLimiter slow = limiterOf(10);
        KisRateLimiter fast = limiterOf(20);

        // when
        slow.reserve(NOW);
        fast.reserve(NOW);

        // then 20건이면 50ms, 10건이면 100ms 다
        assertThat(slow.reserve(NOW) - NOW).isEqualTo(SECOND / 10);
        assertThat(fast.reserve(NOW) - NOW).isEqualTo(SECOND / 20);
    }

    private static KisRateLimiter limiterOf(int callsPerSecond) {
        return new KisRateLimiter(new KisProperties(BASE_URL, "key", "secret", callsPerSecond));
    }
}
