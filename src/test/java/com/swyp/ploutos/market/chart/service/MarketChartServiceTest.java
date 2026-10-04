package com.swyp.ploutos.market.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.service.IndicatorDailyPriceReader;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.quote.service.IndicatorQuoteReader;
import com.swyp.ploutos.stock.chart.ChartCandle;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

class MarketChartServiceTest {

    /** KST 2026-09-30 09:00 = 뉴욕 2026-09-29 20:00. 두 지표의 "오늘"이 서로 다른 시각이다. */
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate NEW_YORK_TODAY = LocalDate.of(2026, 9, 29);

    private FakeIndicatorDailyPriceReader dailyPriceReader;
    private FakeIndicatorQuoteReader quoteReader;
    private MarketChartService service;

    @BeforeEach
    void setUp() {
        dailyPriceReader = new FakeIndicatorDailyPriceReader();
        quoteReader = new FakeIndicatorQuoteReader();
        service = new MarketChartService(dailyPriceReader, quoteReader, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 오늘은_지표_타임존으로_계산한다() {
        // given 같은 시각이지만 서울은 9/30, 뉴욕은 아직 9/29다
        dailyPriceReader.prices = DailyPrices.of(List.of(price(LocalDate.of(2026, 9, 28))));

        // when
        service.read(MarketIndicator.KOSPI, null, null, null);
        LocalDate seoulTo = dailyPriceReader.requestedTo;
        service.read(MarketIndicator.NASDAQ, null, null, null);

        // then 구간의 끝이 각 지표의 현지 오늘이다
        assertThat(seoulTo).isEqualTo(SEOUL_TODAY);
        assertThat(dailyPriceReader.requestedTo).isEqualTo(NEW_YORK_TODAY);
    }

    @Test
    void 구간을_생략하면_오늘까지_최근_2개월을_조회한다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(LocalDate.of(2026, 9, 28))));

        // when
        service.read(MarketIndicator.KOSPI, null, null, null);

        // then 달력 기준 2개월을 뺀 날부터 오늘까지다
        assertThat(dailyPriceReader.requestedFrom).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(dailyPriceReader.requestedTo).isEqualTo(SEOUL_TODAY);
        assertThat(dailyPriceReader.requestedIndicator).isEqualTo(MarketIndicator.KOSPI);
    }

    @Test
    void 구간을_지정하면_그대로_조회한다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(LocalDate.of(2026, 9, 28))));
        LocalDate from = LocalDate.of(2024, 3, 4);
        LocalDate to = LocalDate.of(2026, 3, 4);

        // when
        MarketChartDetail detail = service.read(MarketIndicator.KOSPI, from, to, "1M");

        // then
        assertThat(dailyPriceReader.requestedFrom).isEqualTo(from);
        assertThat(dailyPriceReader.requestedTo).isEqualTo(to);
        assertThat(detail.interval()).isEqualTo(ChartInterval.MONTH);
        assertThat(detail.indicator()).isEqualTo(MarketIndicator.KOSPI);
    }

    @Test
    void 봉_단위를_생략하면_일봉이다() {
        // given
        dailyPriceReader.prices = DailyPrices.of(List.of(price(LocalDate.of(2026, 9, 28))));

        // when
        MarketChartDetail detail = service.read(MarketIndicator.KOSPI, null, null, null);

        // then
        assertThat(detail.interval()).isEqualTo(ChartInterval.DAY);
    }

    @Test
    void 장중이면_진행_중인_봉이_차트_끝에_붙는다() {
        // given 전일 종가가 마지막 확정 봉(9/29) 종가와 같아 오늘 봉으로 이어진다
        dailyPriceReader.prices = DailyPrices.of(List.of(price(LocalDate.of(2026, 9, 29))));
        quoteReader.quote = quote(new BigDecimal("6902.33"), new BigDecimal("49000"));

        // when
        MarketChartDetail detail = service.read(MarketIndicator.KOSPI, null, null, null);

        // then 서울 오늘 날짜로 미확정 봉이 붙는다
        ChartCandle last = detail.chart().candles().getLast();
        assertThat(last.tradeAt()).isEqualTo(SEOUL_TODAY);
        assertThat(last.closed()).isFalse();
        assertThat(detail.chart().asOf()).isPresent();
    }

    @Test
    void 잘못된_봉_단위면_조회하지_않고_예외를_던진다() {
        // given & when & then
        assertThatThrownBy(() -> service.read(MarketIndicator.KOSPI, null, null, "2W"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(dailyPriceReader.requestedIndicator).isNull();
        assertThat(quoteReader.called).isFalse();
    }

    @Test
    void 구간이_오년을_넘으면_조회하지_않는다() {
        // given & when & then
        assertThatThrownBy(() -> service.read(MarketIndicator.KOSPI, LocalDate.of(2000, 1, 1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(dailyPriceReader.requestedIndicator).isNull();
        assertThat(quoteReader.called).isFalse();
    }

    /** 지표 일봉은 거래량이 없어 volume이 0이다. */
    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(
                tradeAt,
                new BigDecimal("6844.41"),
                new BigDecimal("6898.36"),
                new BigDecimal("6782.99"),
                new BigDecimal("49000"),
                0L
        );
    }

    private static IndicatorQuote quote(BigDecimal value, BigDecimal previousClose) {
        return new IndicatorQuote(
                MarketIndicator.KOSPI,
                value,
                previousClose,
                new BigDecimal("6875.20"),
                new BigDecimal("6910.45"),
                new BigDecimal("6861.02"),
                OffsetDateTime.parse("2026-09-30T09:00:00+09:00")
        );
    }

    private static final class FakeIndicatorDailyPriceReader implements IndicatorDailyPriceReader {

        private DailyPrices prices = DailyPrices.of(List.of());
        private MarketIndicator requestedIndicator;
        private LocalDate requestedFrom;
        private LocalDate requestedTo;

        @Override
        public DailyPrices findBetween(MarketIndicator indicator, LocalDate from, LocalDate to) {
            requestedIndicator = indicator;
            requestedFrom = from;
            requestedTo = to;
            return prices;
        }
    }

    private static final class FakeIndicatorQuoteReader implements IndicatorQuoteReader {

        private IndicatorQuote quote = quote(new BigDecimal("6902.33"), new BigDecimal("6870.81"));
        private boolean called;

        @Override
        public IndicatorQuote read(MarketIndicator indicator) {
            called = true;
            return quote;
        }
    }
}
