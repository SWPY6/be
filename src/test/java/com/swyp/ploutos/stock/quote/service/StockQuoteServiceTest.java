package com.swyp.ploutos.stock.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.swyp.ploutos.stock.quote.Quote;

class StockQuoteServiceTest {

    private static final Long STOCK_ID = 1L;
    private static final Long MISSING_STOCK_ID = 99L;

    private FakeQuoteCache cache;
    private FakeQuoteProvider provider;
    private StockQuoteService service;

    @BeforeEach
    void setUp() {
        cache = new FakeQuoteCache();
        provider = new FakeQuoteProvider();
        StockWithMarket stock = new StockWithMarket(
                new Stocks(1L, "005930", "삼성전자", null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표",
                        LocalDate.of(2000, 1, 1)),
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        service = new StockQuoteService(id -> {
            if (id.equals(MISSING_STOCK_ID)) {
                throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
            }
            return stock;
        }, cache, provider);
    }

    @Test
    void 캐시가_있으면_외부를_호출하지_않고_같은_값을_반환한다() {
        // given
        Quote cached = FakeQuoteProvider.quote(new BigDecimal("250000"));
        cache.values.put(STOCK_ID, cached);

        // when
        Quote quote = service.read(STOCK_ID);

        // then
        assertThat(quote).isEqualTo(cached);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 조회하면_활성_종목으로_표시한다() {
        // given
        cache.values.put(STOCK_ID, FakeQuoteProvider.quote(new BigDecimal("250000")));

        // when
        service.read(STOCK_ID);

        // then
        assertThat(cache.marked).containsExactly(STOCK_ID);
    }

    @Test
    void 캐시_미스_시_락을_잡은_요청만_외부를_호출한다() {
        // given 캐시가 비어 있고 락도 비어 있다

        // when
        Quote quote = service.read(STOCK_ID);

        // then 외부를 한 번 호출해 저장하고 락을 푼다
        assertThat(provider.calls).hasSize(1);
        assertThat(cache.values).containsEntry(STOCK_ID, quote);
        assertThat(cache.unlocked).containsExactly(STOCK_ID);
    }

    @Test
    void 락을_못_잡은_요청은_캐시가_채워지면_그_값을_반환한다() {
        // given 다른 요청이 락을 잡고 있다가, 세 번째 조회 때 값을 채운다
        Quote filled = FakeQuoteProvider.quote(new BigDecimal("251000"));
        cache.fillAfter(3, STOCK_ID, filled);

        // when
        Quote quote = service.read(STOCK_ID);

        // then
        assertThat(quote).isEqualTo(filled);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 락_대기가_끝나도_캐시가_비어_있으면_외부를_호출하지_않고_예외를_던진다() {
        // given 다른 요청이 락을 잡고 끝내 값을 채우지 않는다
        cache.locked.add(STOCK_ID);

        // when & then
        assertThatThrownBy(() -> service.read(STOCK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(provider.calls).isEmpty();
        assertThat(cache.finds).isEqualTo(1 + StockQuoteService.MAX_POLLS);
    }

    @Test
    void 캐시_저장소_장애면_외부를_호출하지_않고_예외를_던진다() {
        // given
        cache.broken = true;

        // when & then
        assertThatThrownBy(() -> service.read(STOCK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 외부_호출이_실패하면_예외를_던지고_락을_해제한다() {
        // given
        provider.failing.add("005930");

        // when & then
        assertThatThrownBy(() -> service.read(STOCK_ID)).isInstanceOf(BusinessException.class);
        assertThat(cache.values).doesNotContainKey(STOCK_ID);
        assertThat(cache.unlocked).containsExactly(STOCK_ID);
    }

    @Test
    void 없는_종목이면_예외를_던진다() {
        // given 없는 종목 ID

        // when & then
        assertThatThrownBy(() -> service.read(MISSING_STOCK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        assertThat(cache.marked).isEmpty();
    }
}
