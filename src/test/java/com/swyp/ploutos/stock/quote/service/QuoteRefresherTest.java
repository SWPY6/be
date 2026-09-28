package com.swyp.ploutos.stock.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;

class QuoteRefresherTest {

    private static final Map<Long, String> TICKERS = Map.of(1L, "005930", 2L, "000660", 3L, "005380");

    private FakeQuoteCache cache;
    private FakeQuoteProvider provider;
    private QuoteRefresher refresher;

    @BeforeEach
    void setUp() {
        cache = new FakeQuoteCache();
        provider = new FakeQuoteProvider();
        Markets market = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
        refresher = new QuoteRefresher(id -> {
            String ticker = TICKERS.get(id);
            if (ticker == null) {
                throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
            }
            return new StockWithMarket(new Stocks(1L, ticker, ticker, null, StockStatus.ACTIVE, Exchange.KRX, 1L,
                    "대표", LocalDate.of(2000, 1, 1)), market);
        }, cache, provider);
    }

    @Test
    void 활성_종목만_갱신한다() {
        // given 1·3번만 최근에 조회됐다
        cache.active.addAll(List.of(1L, 3L));

        // when
        refresher.refresh();

        // then
        assertThat(provider.calls).containsExactly("005930", "005380");
        assertThat(cache.values).containsOnlyKeys(1L, 3L);
    }

    @Test
    void 리더_락을_못_잡으면_갱신하지_않는다() {
        // given 다른 인스턴스가 이번 주기를 갱신하고 있다
        cache.active.add(1L);
        cache.leadership = false;

        // when
        refresher.refresh();

        // then
        assertThat(provider.calls).isEmpty();
        assertThat(cache.values).isEmpty();
    }

    @Test
    void 한_종목이_실패해도_나머지는_갱신한다() {
        // given 2번은 KIS가 실패하고 99번은 삭제된 종목이다
        cache.active.addAll(List.of(1L, 2L, 99L, 3L));
        provider.failing.add("000660");

        // when
        refresher.refresh();

        // then
        assertThat(cache.values).containsOnlyKeys(1L, 3L);
    }

    @Test
    void 캐시_저장소_장애면_이번_주기를_건너뛴다() {
        // given
        cache.active.add(1L);
        cache.broken = true;

        // when
        refresher.refresh();

        // then
        assertThat(provider.calls).isEmpty();
    }
}
