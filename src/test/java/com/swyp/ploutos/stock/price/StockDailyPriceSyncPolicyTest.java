package com.swyp.ploutos.stock.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;

class StockDailyPriceSyncPolicyTest {

    private static final LocalDate LISTED_LONG_AGO = LocalDate.of(2000, 1, 1);
    // 2026-08-12 수요일
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate FROM = LocalDate.of(2026, 5, 12);

    private static StockWithMarket stockListedOn(LocalDate listedAt) {
        Stocks stock = new Stocks(1L, "005930", "삼성전자", null, StockStatus.ACTIVE, Exchange.KRX,
                1L, "대표", listedAt);
        Markets market = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
        return new StockWithMarket(stock, market);
    }

    private final StockDailyPriceSyncPolicy policy = new StockDailyPriceSyncPolicy(
            Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void 저장된_봉이_구간을_덮으면_동기화하지_않는다() {
        // given
        StoredRange stored = new StoredRange(Optional.of(LocalDate.of(2026, 1, 2)), Optional.of(WEDNESDAY.minusDays(1)));

        // when
        boolean covers = policy.covers(stored, LocalDate.of(2026, 5, 12), stockListedOn(LISTED_LONG_AGO), WEDNESDAY);

        // then
        assertThat(covers).isTrue();
    }

    @Test
    void 저장된_봉이_시작일을_덮지_못하면_동기화한다() {
        // given
        StoredRange stored = new StoredRange(Optional.of(LocalDate.of(2026, 6, 1)), Optional.of(WEDNESDAY.minusDays(1)));

        // when
        boolean covers = policy.covers(stored, LocalDate.of(2026, 5, 12), stockListedOn(LISTED_LONG_AGO), WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 저장된_봉이_마지막_거래일에_못_미치면_동기화한다() {
        // given
        StoredRange stored = new StoredRange(Optional.of(LocalDate.of(2026, 1, 2)), Optional.of(WEDNESDAY.minusDays(2)));

        // when
        boolean covers = policy.covers(stored, LocalDate.of(2026, 5, 12), stockListedOn(LISTED_LONG_AGO), WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 저장된_봉이_없으면_동기화한다() {
        // when
        boolean covers = policy.covers(StoredRange.empty(), LocalDate.of(2026, 5, 12), stockListedOn(LISTED_LONG_AGO), WEDNESDAY);

        // then
        assertThat(covers).isFalse();
    }

    @Test
    void 상장일이_시작일보다_늦으면_덮은_것으로_본다() {
        // given
        LocalDate listedAt = LocalDate.of(2026, 7, 1);
        StoredRange stored = new StoredRange(Optional.of(listedAt), Optional.of(WEDNESDAY.minusDays(1)));

        // when
        boolean covers = policy.covers(stored, LocalDate.of(2026, 5, 12), stockListedOn(listedAt), WEDNESDAY);

        // then
        assertThat(covers).isTrue();
    }

    @Test
    void 주말이면_직전_금요일을_마지막_거래일로_본다() {
        // given
        LocalDate sunday = LocalDate.of(2026, 8, 16);
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        LocalDate monday = LocalDate.of(2026, 8, 17);
        LocalDate friday = LocalDate.of(2026, 8, 14);

        // when & then
        assertThat(policy.lastTradingDayBefore(sunday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(saturday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(monday)).isEqualTo(friday);
        assertThat(policy.lastTradingDayBefore(WEDNESDAY)).isEqualTo(WEDNESDAY.minusDays(1));
    }

    @Test
    void 같은_종목의_동기화는_하루_한_번만_시도한다() {
        // given
        Long stockId = 1L;

        // when
        boolean first = policy.tryStartSync(stockId, FROM, WEDNESDAY);
        boolean second = policy.tryStartSync(stockId, FROM, WEDNESDAY);
        boolean nextDay = policy.tryStartSync(stockId, FROM, WEDNESDAY.plusDays(1));
        boolean otherStock = policy.tryStartSync(2L, FROM, WEDNESDAY);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(nextDay).isTrue();
        assertThat(otherStock).isTrue();
    }

    @Test
    void 같은_날_더_이른_시작일을_요청하면_다시_동기화한다() {
        // given
        // 아침에 1개월 구간으로 이미 동기화를 시도했다.
        Long stockId = 1L;
        policy.tryStartSync(stockId, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // when
        // 같은 날 줌 아웃으로 3년 구간을 요청한다. 저장된 봉이 덮지 못하므로 받아 와야 한다.
        boolean retried = policy.tryStartSync(stockId, WEDNESDAY.minusYears(3), WEDNESDAY);

        // then
        assertThat(retried).isTrue();
    }

    @Test
    void 같은_날_같거나_늦은_시작일이면_다시_동기화하지_않는다() {
        // given
        Long stockId = 1L;
        policy.tryStartSync(stockId, WEDNESDAY.minusYears(3), WEDNESDAY);

        // when
        boolean sameStart = policy.tryStartSync(stockId, WEDNESDAY.minusYears(3), WEDNESDAY);
        boolean narrower = policy.tryStartSync(stockId, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // then
        assertThat(sameStart).isFalse();
        assertThat(narrower).isFalse();
    }

    @Test
    void 좁은_구간을_시도해도_그날_받아_둔_넓은_구간의_기록은_남는다() {
        // given
        // 넓게 받아 둔 뒤 좁은 요청이 여러 번 와도, 중간 구간 요청이 헛동기화를 일으키면 안 된다.
        Long stockId = 1L;
        policy.tryStartSync(stockId, WEDNESDAY.minusYears(3), WEDNESDAY);
        policy.tryStartSync(stockId, WEDNESDAY.minusMonths(1), WEDNESDAY);

        // when
        boolean middle = policy.tryStartSync(stockId, WEDNESDAY.minusYears(1), WEDNESDAY);

        // then
        assertThat(middle).isFalse();
    }

    @Test
    void 미국_종목은_뉴욕_날짜로_오늘을_계산한다() {
        // given
        // 2026-03-08 뉴욕 서머타임 시작. UTC 03-09 02:00 = 뉴욕 03-08 22:00(EDT) = 서울 03-09 11:00
        StockDailyPriceSyncPolicy policy = new StockDailyPriceSyncPolicy(
                Clock.fixed(Instant.parse("2026-03-09T02:00:00Z"), ZoneOffset.UTC));

        // when
        LocalDate newYork = policy.today(Country.US);
        LocalDate seoul = policy.today(Country.KR);

        // then
        assertThat(newYork).isEqualTo(LocalDate.of(2026, 3, 8));
        assertThat(seoul).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void 동시에_시도해도_한_요청만_시도권을_얻는다() throws Exception {
        // given 경합 구간이 좁아 한 라운드로는 재현되지 않는다. 매 라운드 새 정책으로 여러 번 돈다
        int threads = 32;
        int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // when 스레드가 미리 CPU에서 돌고 있다가 같은 종목·같은 구간을 동시에 시도한다
        long maxGranted = 0;
        for (int round = 0; round < rounds; round++) {
            StockDailyPriceSyncPolicy policy = new StockDailyPriceSyncPolicy(
                    Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));
            AtomicBoolean go = new AtomicBoolean(false);
            List<Future<Boolean>> results = IntStream.range(0, threads)
                    .mapToObj(ignored -> pool.submit(() -> {
                        while (!go.get()) {
                            Thread.onSpinWait();
                        }
                        return policy.tryStartSync(1L, FROM, WEDNESDAY);
                    }))
                    .toList();
            go.set(true);
            long granted = 0;
            for (Future<Boolean> result : results) {
                granted += result.get() ? 1 : 0;
            }
            maxGranted = Math.max(maxGranted, granted);
        }
        pool.shutdown();

        // then 둘이 같이 동기화하면 같은 거래일을 저장하다 유니크 제약에 걸린다
        assertThat(maxGranted).isEqualTo(1);
    }
}
