package com.swyp.ploutos.market.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.StoredRange;

class IndicatorDailyPriceSyncPolicyTest {

    private static final MarketIndicator INDICATOR = MarketIndicator.KOSPI;
    // 2026-08-12 수요일
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate FROM = LocalDate.of(2026, 5, 12);

    private final IndicatorDailyPriceSyncPolicy policy = new IndicatorDailyPriceSyncPolicy(
            Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void 저장된_봉이_구간을_덮으면_참이다() {
        // given 시작일 이전부터 어제까지 저장돼 있다
        StoredRange stored = new StoredRange(
                Optional.of(LocalDate.of(2026, 1, 2)), Optional.of(WEDNESDAY.minusDays(1)));

        // when
        boolean covers = policy.covers(stored, FROM, WEDNESDAY);

        // then
        assertThat(covers).isTrue();
    }

    @Test
    void 저장된_봉이_시작일을_덮지_못하면_거짓이다() {
        // given 가장 이른 저장일이 시작일보다 늦다
        StoredRange stored = new StoredRange(
                Optional.of(LocalDate.of(2026, 6, 1)), Optional.of(WEDNESDAY.minusDays(1)));

        // when
        boolean covers = policy.covers(stored, FROM, WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 저장된_봉이_마지막_거래일에_못_미치면_거짓이다() {
        // given 끝이 하루 낡았다
        StoredRange stored = new StoredRange(
                Optional.of(LocalDate.of(2026, 1, 2)), Optional.of(WEDNESDAY.minusDays(2)));

        // when
        boolean covers = policy.covers(stored, FROM, WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 저장된_봉이_없으면_거짓이다() {
        // given 저장된 일봉이 없다

        // when
        boolean covers = policy.covers(StoredRange.empty(), FROM, WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 주말이면_직전_금요일을_마지막_거래일로_본다() {
        // given
        LocalDate friday = LocalDate.of(2026, 8, 14);
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        LocalDate sunday = LocalDate.of(2026, 8, 16);
        LocalDate monday = LocalDate.of(2026, 8, 17);

        // when & then
        assertThat(policy.lastTradingDayBefore(saturday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(sunday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(monday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(WEDNESDAY)).isEqualTo(WEDNESDAY.minusDays(1));
    }

    @Test
    void 오늘은_지표_타임존으로_계산한다() {
        // given 2026-03-08 뉴욕 서머타임 시작. UTC 03-09 02:00 = 뉴욕 03-08 22:00(EDT) = 서울 03-09 11:00
        IndicatorDailyPriceSyncPolicy policy = new IndicatorDailyPriceSyncPolicy(
                Clock.fixed(Instant.parse("2026-03-09T02:00:00Z"), ZoneOffset.UTC));

        // when
        LocalDate domestic = policy.today(MarketIndicator.KOSPI);
        LocalDate overseas = policy.today(MarketIndicator.NASDAQ);
        LocalDate exchangeRate = policy.today(MarketIndicator.USD_KRW);

        // then
        assertThat(domestic).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(overseas).isEqualTo(LocalDate.of(2026, 3, 8));
        assertThat(exchangeRate).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void 같은_지표의_동기화는_하루_한_번만_시도한다() {
        // given 지표 하나로 같은 구간을 반복 요청한다

        // when
        boolean first = policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);
        boolean second = policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);
        boolean nextDay = policy.tryStartSync(INDICATOR, FROM, WEDNESDAY.plusDays(1));
        boolean otherIndicator = policy.tryStartSync(MarketIndicator.KOSDAQ, FROM, WEDNESDAY);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(nextDay).isTrue();
        assertThat(otherIndicator).isTrue();
    }

    @Test
    void 같은_날_더_이른_시작일을_요청하면_다시_동기화한다() {
        // given 아침에 1개월 구간으로 이미 시도했다
        policy.tryStartSync(INDICATOR, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // when 같은 날 줌 아웃으로 3년 구간을 요청한다. 저장된 봉이 덮지 못하므로 받아 와야 한다
        boolean retried = policy.tryStartSync(INDICATOR, WEDNESDAY.minusYears(3), WEDNESDAY);

        // then
        assertThat(retried).isTrue();
    }

    @Test
    void 같은_날_같거나_늦은_시작일이면_다시_동기화하지_않는다() {
        // given 3년 구간으로 이미 받아 뒀다
        policy.tryStartSync(INDICATOR, WEDNESDAY.minusYears(3), WEDNESDAY);

        // when
        boolean sameStart = policy.tryStartSync(INDICATOR, WEDNESDAY.minusYears(3), WEDNESDAY);
        boolean narrower = policy.tryStartSync(INDICATOR, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // then
        assertThat(sameStart).isFalse();
        assertThat(narrower).isFalse();
    }

    @Test
    void 시도를_취소하면_같은_날_다시_시도할_수_있다() {
        // given
        policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);

        // when
        policy.cancelSync(INDICATOR, FROM, WEDNESDAY);
        boolean retried = policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);

        // then
        assertThat(retried).isTrue();
    }

    @Test
    void 다른_요청이_기록을_바꿨으면_취소해도_그_기록은_남는다() {
        // given 좁은 구간 시도가 실패하는 사이 다른 요청이 더 이른 시작일로 시도권을 얻었다
        policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);
        policy.tryStartSync(INDICATOR, FROM.minusYears(1), WEDNESDAY);

        // when
        policy.cancelSync(INDICATOR, FROM, WEDNESDAY);
        boolean retried = policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);

        // then
        assertThat(retried).isFalse();
    }

    @Test
    void 좁은_구간을_시도해도_그날_받아_둔_넓은_구간의_기록은_남는다() {
        // given 넓게 받아 둔 뒤 좁은 요청이 들어왔다. 거절이 기록을 덮으면 안 된다
        policy.tryStartSync(INDICATOR, WEDNESDAY.minusYears(3), WEDNESDAY);
        policy.tryStartSync(INDICATOR, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // when 중간 구간을 요청한다
        boolean middle = policy.tryStartSync(INDICATOR, WEDNESDAY.minusYears(1), WEDNESDAY);

        // then 3년 구간 기록이 남아 있어 헛동기화를 하지 않는다
        assertThat(middle).isFalse();
    }

    @Test
    void 동시에_시도해도_한_요청만_시도권을_얻는다() throws Exception {
        // given 지표가 5개로 고정이라 첫 요청이 한꺼번에 몰리기 쉽다
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // when 스레드를 한꺼번에 풀어 같은 지표·같은 구간을 동시에 시도한다
        List<Future<Boolean>> results = IntStream.range(0, threads)
                .mapToObj(ignored -> pool.submit(() -> {
                    start.await();
                    return policy.tryStartSync(INDICATOR, FROM, WEDNESDAY);
                }))
                .toList();
        start.countDown();
        long granted = 0;
        for (Future<Boolean> result : results) {
            granted += result.get() ? 1 : 0;
        }
        pool.shutdown();

        // then 둘이 같이 동기화하면 같은 행을 저장하다 유니크 제약에 걸린다
        assertThat(granted).isEqualTo(1);
    }
}
