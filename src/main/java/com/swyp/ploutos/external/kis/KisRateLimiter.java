package com.swyp.ploutos.external.kis;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * KIS 를 부르기 전에 스스로 간격을 둔다. 명세: {@code docs/SPEC-kis-client.md}「호출 제한」
 *
 * <p>한도에 부딪힌 뒤 1초 쉬는 복구 장치({@link RestClientKisApiClient}의 재시도)만으로는
 * 부족하다. 산업 흐름이 1,599종목을 훑자 <b>거의 모든 호출이 한도 초과로 1초씩 쉬어</b>
 * 한 바퀴가 27분이 됐다(실측 2026-10-12). 제한기는 예방이고 재시도는 복구다.
 *
 * <p><b>간격만 벌리고 동시 실행을 막지 않는다.</b> 순번을 잡는 동안만 잠그고, 기다림과 HTTP 는
 * 잠금 밖에서 한다. 잠근 채로 자면 뒤 호출까지 함께 멈춰 동시성이 사라진다.
 *
 * <p>JVM 안에서만 센다. 인스턴스가 둘이 되면 각자 제 몫을 보내 합이 한도를 넘으므로 Redis
 * 카운터로 옮겨야 한다.
 */
@Component
class KisRateLimiter {

    private final long intervalNanos;

    /**
     * 다음 호출을 보낼 수 있는 가장 이른 시각. {@link #reserve}가 잠금 안에서만 건드린다.
     * 첫 호출이 기다리지 않도록 과거로 둔다 — {@code System.nanoTime()}은 음수일 수 있어
     * 초기값으로 쓸 수 없다.
     */
    private long nextSlotNanos = Long.MIN_VALUE;

    KisRateLimiter(KisProperties properties) {
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / properties.maxCallsPerSecond();
    }

    /** 내 순번이 올 때까지 기다린다. 이미 지났으면 기다리지 않는다. */
    void acquire() {
        sleepUntil(reserve(System.nanoTime()));
    }

    /**
     * 순번을 하나 잡고 그 시각을 돌려준다.
     *
     * <p>현재 시각을 <b>인자로 받는다.</b> 그래야 테스트가 실제로 기다리지 않고 간격 규칙을
     * 검증할 수 있다 — 시간을 재는 테스트는 느리고 잘 깨진다.
     */
    synchronized long reserve(long nowNanos) {
        long slot = Math.max(nowNanos, nextSlotNanos);
        nextSlotNanos = slot + intervalNanos;
        return slot;
    }

    private static void sleepUntil(long slotNanos) {
        long waitNanos = slotNanos - System.nanoTime();
        if (waitNanos <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofNanos(waitNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
