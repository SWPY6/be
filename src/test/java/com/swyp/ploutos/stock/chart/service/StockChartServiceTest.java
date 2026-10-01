package com.swyp.ploutos.stock.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.swyp.ploutos.stock.chart.ChartCandle;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.QuoteReader;

class StockChartServiceTest {

    private static final Long STOCK_ID = 1L;
    private static final Long MISSING_STOCK_ID = 99L;

    /** KST 2026-08-12 14:31:05 */
    private static final Instant NOW = Instant.parse("2026-08-12T05:31:05Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    private FakeDailyPriceReader dailyPriceReader;
    private FakeQuoteReader quoteReader;
    private StockChartService service;

    @BeforeEach
    void setUp() {
        StockWithMarket stock = new StockWithMarket(
                new Stocks(STOCK_ID, "005380", "현대차", null, StockStatus.ACTIVE, Exchange.KRX, 1L, "대표",
                        LocalDate.of(2000, 1, 1)),
                new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        dailyPriceReader = new FakeDailyPriceReader();
        quoteReader = new FakeQuoteReader();
        service = new StockChartService(
                id -> {
                    if (id.equals(MISSING_STOCK_ID)) {
                        throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
                    }
                    return stock;
                },
                dailyPriceReader,
                quoteReader,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 구간을_생략하면_오늘까지_최근_2개월을_조회한다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, null);

        // then 시장 현지 오늘에서 달력 기준 2개월을 뺀 날부터 오늘까지다
        assertThat(dailyPriceReader.requestedFrom).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(dailyPriceReader.requestedTo).isEqualTo(TODAY);
        assertThat(detail.currency()).isEqualTo(Currency.KRW);
    }

    @Test
    void 구간을_지정하면_그대로_조회한다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));
        LocalDate from = LocalDate.of(2024, 3, 4);
        LocalDate to = LocalDate.of(2026, 3, 4);

        // when
        service.read(STOCK_ID, from, to, "1M");

        // then
        assertThat(dailyPriceReader.requestedFrom).isEqualTo(from);
        assertThat(dailyPriceReader.requestedTo).isEqualTo(to);
    }

    @Test
    void 봉_단위를_생략하면_일봉이다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, null);

        // then
        assertThat(detail.interval()).isEqualTo(ChartInterval.DAY);
    }

    @Test
    void 봉_단위를_지정하면_그대로_적용한다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, "3M");

        // then
        assertThat(detail.interval()).isEqualTo(ChartInterval.QUARTER);
    }

    @Test
    void 평균_거래량은_일봉_모듈에_묻지_않는다() {
        // given 기준선은 응답에 담긴 봉으로 직접 계산한다. 일봉 모듈에 다시 물으면 DB·외부 왕복이 는다
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, null);

        // then
        assertThat(dailyPriceReader.averageCalled).isFalse();
        assertThat(detail.chart().averageVolume()).contains(100_000L);
    }

    @Test
    void 평균_거래량은_당일_봉을_제외하고_계산한다() {
        // given 확정 봉 기준 평균과 당일 누적 거래량이 확연히 다르다
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));
        quoteReader.quote = quote(BigDecimal.valueOf(50_000), 9_999_999L);

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, null);

        // then 당일 봉은 붙었지만 평균은 확정 봉 기준 그대로다
        ChartCandle today = detail.chart().candles().getLast();
        assertThat(today.closed()).isFalse();
        assertThat(today.volume()).isEqualTo(9_999_999L);
        assertThat(detail.chart().averageVolume()).contains(100_000L);
    }

    @Test
    void 당일_시가가_없으면_당일_봉을_붙이지_않는다() {
        // given 개장 전에는 KIS가 시가를 0으로 준다. 진행 중인 봉이 있는지는 이 서비스가 판단한다
        dailyPriceReader.prices = DailyPrices.of(List.of(price(TODAY.minusDays(1))));
        quoteReader.quote = notOpenedYet();

        // when
        StockChartDetail detail = service.read(STOCK_ID, null, null, null);

        // then 확정 봉만 남고 기준 시각도 비어 있다
        assertThat(detail.chart().candles()).hasSize(1);
        assertThat(detail.chart().candles().getLast().closed()).isTrue();
        assertThat(detail.chart().asOf()).isEmpty();
    }

    @Test
    void 없는_종목이면_시세를_조회하지_않는다() {
        // given & when & then
        assertThatThrownBy(() -> service.read(MISSING_STOCK_ID, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        assertThat(quoteReader.called).isFalse();
    }

    @Test
    void 허용되지_않은_봉_단위면_종목도_조회하지_않는다() {
        // given & when & then
        assertThatThrownBy(() -> service.read(STOCK_ID, null, null, "2W"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(quoteReader.called).isFalse();
    }

    @Test
    void 구간이_오년을_넘으면_시세를_조회하지_않는다() {
        // given & when & then
        assertThatThrownBy(() -> service.read(STOCK_ID, LocalDate.of(2000, 1, 1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(quoteReader.called).isFalse();
        assertThat(dailyPriceReader.requestedFrom).isNull();
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(
                tradeAt,
                BigDecimal.valueOf(48000),
                BigDecimal.valueOf(49500),
                BigDecimal.valueOf(47500),
                BigDecimal.valueOf(49000),
                100_000L
        );
    }

    /** 개장 전 시세. KIS는 시가·고가·저가와 거래량을 0으로 준다. */
    private static Quote notOpenedYet() {
        return new Quote(
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(49000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                BigDecimal.ZERO,
                BigDecimal.valueOf(300_000_000_000L),
                Currency.KRW,
                OffsetDateTime.parse("2026-08-12T14:31:05+09:00"),
                PriceTiming.REALTIME
        );
    }

    private static Quote quote(BigDecimal price, long volume) {
        return new Quote(
                price,
                BigDecimal.valueOf(49000),
                BigDecimal.valueOf(48000),
                BigDecimal.valueOf(50500),
                BigDecimal.valueOf(47800),
                volume,
                BigDecimal.valueOf(60_000_000),
                BigDecimal.valueOf(300_000_000_000L),
                Currency.KRW,
                OffsetDateTime.parse("2026-08-12T14:31:05+09:00"),
                PriceTiming.REALTIME
        );
    }

    private static final class FakeDailyPriceReader implements DailyPriceReader {

        private DailyPrices prices = DailyPrices.of(List.of());
        private LocalDate requestedFrom;
        private LocalDate requestedTo;
        private boolean averageCalled;

        @Override
        public DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to) {
            requestedFrom = from;
            requestedTo = to;
            return prices;
        }

        @Override
        public Optional<Long> averageVolume20d(Long stockId) {
            averageCalled = true;
            return Optional.empty();
        }
    }

    private static final class FakeQuoteReader implements QuoteReader {

        private Quote quote = quote(BigDecimal.valueOf(50_000), 1_200L);
        private boolean called;

        @Override
        public Quote read(Long stockId) {
            called = true;
            return quote;
        }

        @Override
        public Quote readWithoutTracking(Long stockId) {
            throw new UnsupportedOperationException("차트는 갱신 대상으로 표시하며 읽는다");
        }
    }
}
