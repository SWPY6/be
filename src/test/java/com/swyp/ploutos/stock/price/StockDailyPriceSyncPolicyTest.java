package com.swyp.ploutos.stock.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

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
        boolean first = policy.tryStartSync(stockId, WEDNESDAY);
        boolean second = policy.tryStartSync(stockId, WEDNESDAY);
        boolean nextDay = policy.tryStartSync(stockId, WEDNESDAY.plusDays(1));
        boolean otherStock = policy.tryStartSync(2L, WEDNESDAY);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(nextDay).isTrue();
        assertThat(otherStock).isTrue();
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
}
