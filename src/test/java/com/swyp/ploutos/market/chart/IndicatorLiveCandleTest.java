package com.swyp.ploutos.market.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.stock.chart.ChartRange;
import com.swyp.ploutos.stock.chart.LiveCandle;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

class IndicatorLiveCandleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 9, 29);

    /** 코스피 9/28·9/29 확정 봉. 실측값이다. */
    private static final DailyPrices KOSPI_CLOSED = DailyPrices.of(List.of(
            price(LocalDate.of(2026, 9, 28), "7057.86", "7065.90", "6889.68", "6889.74"),
            price(YESTERDAY, "6844.41", "6898.36", "6782.99", "6870.81")
    ));

    @Test
    void 전일종가가_마지막_확정봉_종가와_같으면_오늘_봉을_붙인다() {
        // given 9/30 장중 코스피. 전일 종가가 마지막 확정 봉(9/29) 종가와 같다
        IndicatorQuote quote = kospiQuote("6902.33", "6870.81", "6875.20", "6910.45", "6861.02",
                "2026-09-30T10:15:03+09:00");

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, KOSPI_CLOSED, rangeTo(TODAY), TODAY);

        // then 종가 자리에 현재값이 들어가고 거래량은 0이다
        assertThat(live).isPresent();
        DailyPrice price = live.get().price();
        assertThat(price.tradeAt()).isEqualTo(TODAY);
        assertThat(price.open()).isEqualByComparingTo("6875.20");
        assertThat(price.high()).isEqualByComparingTo("6910.45");
        assertThat(price.low()).isEqualByComparingTo("6861.02");
        assertThat(price.close()).isEqualByComparingTo("6902.33");
        assertThat(price.volume()).isZero();
        assertThat(live.get().asOf()).isEqualTo(quote.valueAt());
    }

    @Test
    void 전일종가가_마지막_확정봉_종가와_다르면_붙이지_않는다() {
        // given 9/30 05:21 실측. 시가가 0이 아니지만 값은 전부 9/29 거래일의 것이다.
        // 전일 종가 6889.74는 9/28 종가라 마지막 확정 봉(9/29) 종가 6870.81과 다르다
        IndicatorQuote quote = kospiQuote("6870.81", "6889.74", "6844.41", "6898.36", "6782.99",
                "2026-09-30T05:21:00+09:00");

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, KOSPI_CLOSED, rangeTo(TODAY), TODAY);

        // then 그대로 붙이면 9/30 자리에 9/29와 똑같은 가짜 봉이 생긴다
        assertThat(live).isEmpty();
    }

    @Test
    void 해외_지수도_전일종가가_다르면_붙이지_않는다() {
        // given 한국 9/30 14:00 = 뉴욕 9/30 01:00 실측. 뉴욕은 이미 9/30이지만 시세는 9/29 장의 것이다.
        // 전일 종가 26820.38은 9/28 종가라 마지막 확정 봉(9/29) 종가 26797.54와 다르다
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 9, 28), "26935.76", "26990.02", "26709.69", "26820.38"),
                price(YESTERDAY, "26908.76", "26919.01", "26717.95", "26797.54")
        ));
        IndicatorQuote quote = new IndicatorQuote(
                MarketIndicator.NASDAQ,
                new BigDecimal("26797.54"),
                new BigDecimal("26820.38"),
                new BigDecimal("26908.76"),
                new BigDecimal("26919.01"),
                new BigDecimal("26717.95"),
                OffsetDateTime.parse("2026-09-30T01:00:00-04:00")
        );

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, closed, rangeTo(TODAY), TODAY);

        // then 날짜가 아니라 값의 연속성으로 보기 때문에 국내와 같은 규칙이 통한다
        assertThat(live).isEmpty();
    }

    @Test
    void 확정봉이_없으면_붙이지_않는다() {
        // given 구간 안에 확정 봉이 하나도 없어 견줄 종가가 없다
        IndicatorQuote quote = kospiQuote("6902.33", "6870.81", "6875.20", "6910.45", "6861.02",
                "2026-09-30T10:15:03+09:00");

        // when
        Optional<LiveCandle> live =
                IndicatorLiveCandle.of(quote, DailyPrices.of(List.of()), rangeTo(TODAY), TODAY);

        // then
        assertThat(live).isEmpty();
    }

    @Test
    void 시가가_0이면_붙이지_않는다() {
        // given 장 시작 전에는 KIS가 시가를 0으로 준다
        IndicatorQuote quote = kospiQuote("6870.81", "6870.81", "0", "0", "0",
                "2026-09-30T08:30:00+09:00");

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, KOSPI_CLOSED, rangeTo(TODAY), TODAY);

        // then
        assertThat(live).isEmpty();
    }

    @Test
    void 구간의_끝이_오늘보다_이전이면_붙이지_않는다() {
        // given 과거 구간을 조회한다. 붙이면 요청하지 않은 오늘 봉이 끼어든다
        IndicatorQuote quote = kospiQuote("6902.33", "6870.81", "6875.20", "6910.45", "6861.02",
                "2026-09-30T10:15:03+09:00");

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, KOSPI_CLOSED, rangeTo(YESTERDAY), TODAY);

        // then
        assertThat(live).isEmpty();
    }

    @Test
    void 자릿수가_달라도_같은_값이면_이어진_것으로_본다() {
        // given 환율은 소수 넷째 자리까지 저장한다. KIS가 주는 전일 종가는 자릿수가 다를 수 있다
        DailyPrices closed = DailyPrices.of(List.of(
                price(YESTERDAY, "1358.2000", "1360.5000", "1357.1000", "1359.9000")
        ));
        IndicatorQuote quote = new IndicatorQuote(
                MarketIndicator.USD_KRW,
                new BigDecimal("1361.40"),
                new BigDecimal("1359.90"),
                new BigDecimal("1360.10"),
                new BigDecimal("1362.00"),
                new BigDecimal("1359.50"),
                OffsetDateTime.parse("2026-09-30T10:15:03+09:00")
        );

        // when
        Optional<LiveCandle> live = IndicatorLiveCandle.of(quote, closed, rangeTo(TODAY), TODAY);

        // then 1359.9000과 1359.90은 같은 값이다
        assertThat(live).isPresent();
        assertThat(live.get().price().close()).isEqualByComparingTo("1361.40");
    }

    private static ChartRange rangeTo(LocalDate to) {
        return new ChartRange(to.minusMonths(2), to);
    }

    private static DailyPrice price(LocalDate tradeAt, String open, String high, String low, String close) {
        return new DailyPrice(
                tradeAt,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                0L
        );
    }

    private static IndicatorQuote kospiQuote(String value, String previousClose, String open, String high,
            String low, String valueAt) {
        return new IndicatorQuote(
                MarketIndicator.KOSPI,
                new BigDecimal(value),
                new BigDecimal(previousClose),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                OffsetDateTime.parse(valueAt)
        );
    }
}
