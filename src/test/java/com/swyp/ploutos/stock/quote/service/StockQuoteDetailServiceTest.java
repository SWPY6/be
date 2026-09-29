package com.swyp.ploutos.stock.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.Quote;

class StockQuoteDetailServiceTest {

    private static final Long STOCK_ID = 1L;
    private static final Long MISSING_STOCK_ID = 99L;
    private static final Quote QUOTE = FakeQuoteProvider.quote(new BigDecimal("248000"));

    private FakeQuoteReader quoteReader;
    private FakeDailyPriceReader dailyPriceReader;
    private StockQuoteDetailService service;

    @BeforeEach
    void setUp() {
        StockWithMarket stock = new StockWithMarket(
                new Stocks(1L, "005380", "현대차", null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표",
                        LocalDate.of(2000, 1, 1)),
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        quoteReader = new FakeQuoteReader();
        dailyPriceReader = new FakeDailyPriceReader();
        service = new StockQuoteDetailService(id -> {
            if (id.equals(MISSING_STOCK_ID)) {
                throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
            }
            return stock;
        }, quoteReader, dailyPriceReader);
    }

    @Test
    void 종목_정보와_시세를_함께_돌려준다() {
        // given
        dailyPriceReader.average = Optional.of(250_000L);

        // when
        StockQuoteDetail detail = service.read(STOCK_ID);

        // then
        assertThat(detail.ticker()).isEqualTo("005380");
        assertThat(detail.name()).isEqualTo("현대차");
        assertThat(detail.quote()).isEqualTo(QUOTE);
    }

    @Test
    void 평균_거래량이_있으면_배수를_계산한다() {
        // given 당일 거래량 245,000주
        dailyPriceReader.average = Optional.of(250_000L);

        // when
        StockQuoteDetail detail = service.read(STOCK_ID);

        // then
        assertThat(detail.volumeRatio20d()).isEqualByComparingTo("0.98");
    }

    @Test
    void 평균_거래량이_없으면_배수는_null이다() {
        // given
        dailyPriceReader.average = Optional.empty();

        // when
        StockQuoteDetail detail = service.read(STOCK_ID);

        // then
        assertThat(detail.volumeRatio20d()).isNull();
    }

    @Test
    void 없는_종목이면_시세를_조회하지_않고_예외를_던진다() {
        // given 없는 종목 ID

        // when & then
        assertThatThrownBy(() -> service.read(MISSING_STOCK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        assertThat(quoteReader.calls).isEmpty();
    }

    private static class FakeQuoteReader implements QuoteReader {

        final List<Long> calls = new ArrayList<>();

        @Override
        public Quote read(Long stockId) {
            calls.add(stockId);
            return QUOTE;
        }

        @Override
        public Quote readWithoutTracking(Long stockId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeDailyPriceReader implements DailyPriceReader {

        Optional<Long> average = Optional.empty();

        @Override
        public DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> averageVolume20d(Long stockId) {
            return average;
        }
    }
}
