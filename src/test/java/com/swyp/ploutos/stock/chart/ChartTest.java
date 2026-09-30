package com.swyp.ploutos.stock.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

class ChartTest {

    // 2026-08-12 수요일. 같은 주의 월요일은 08-10이다.
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final OffsetDateTime PRICE_AT =
            OffsetDateTime.of(2026, 8, 12, 14, 31, 5, 0, ZoneOffset.ofHours(9));

    @Test
    void 구간을_지정하면_해당_구간의_거래일_봉만_오름차순으로_반환한다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(TODAY.minusDays(2)),
                price(TODAY.minusDays(4)),
                price(TODAY.minusDays(3))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.candles()).extracting(ChartCandle::tradeAt)
                .containsExactly(TODAY.minusDays(4), TODAY.minusDays(3), TODAY.minusDays(2));
        assertThat(chart.candles()).allMatch(ChartCandle::closed);
    }

    @Test
    void 봉마다_시가_고가_저가_종가_거래량을_포함한다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(new DailyPrice(
                TODAY.minusDays(1),
                BigDecimal.valueOf(244280),
                BigDecimal.valueOf(251224),
                BigDecimal.valueOf(241056),
                BigDecimal.valueOf(248000),
                245000L
        )));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        ChartCandle candle = chart.candles().getFirst();
        assertThat(candle.open()).isEqualByComparingTo("244280");
        assertThat(candle.high()).isEqualByComparingTo("251224");
        assertThat(candle.low()).isEqualByComparingTo("241056");
        assertThat(candle.close()).isEqualByComparingTo("248000");
        assertThat(candle.volume()).isEqualTo(245000L);
    }

    @Test
    void 장중이면_당일_진행중_봉을_마지막에_붙인다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(price(TODAY.minusDays(1))));
        Quote quote = quote(BigDecimal.valueOf(50000), BigDecimal.valueOf(49000), 1_200L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.DAY, TODAY);

        // then
        ChartCandle today = chart.candles().getLast();
        assertThat(chart.candles()).hasSize(2);
        assertThat(today.tradeAt()).isEqualTo(TODAY);
        assertThat(today.closed()).isFalse();
        assertThat(today.close()).isEqualByComparingTo("50000");
        assertThat(today.volume()).isEqualTo(1_200L);
        assertThat(chart.asOf()).contains(PRICE_AT);
        assertThat(chart.to()).contains(TODAY);
    }

    @Test
    void 당일_시가가_없으면_당일_봉을_붙이지_않는다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(price(TODAY.minusDays(1))));
        Quote 개장_전 = quote(BigDecimal.valueOf(50000), BigDecimal.ZERO, 0L);

        // when
        Chart chart = Chart.of(closed, Optional.of(개장_전), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.candles()).hasSize(1);
        assertThat(chart.candles().getLast().closed()).isTrue();
        assertThat(chart.asOf()).isEmpty();
    }

    @Test
    void 당일_봉이_이미_확정되어_있으면_붙이지_않는다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(price(TODAY.minusDays(1)), price(TODAY)));
        Quote quote = quote(BigDecimal.valueOf(50000), BigDecimal.valueOf(49000), 1_200L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.candles()).hasSize(2);
        assertThat(chart.candles()).allMatch(ChartCandle::closed);
        assertThat(chart.asOf()).isEmpty();
    }

    @Test
    void 당일_봉이_이미_확정되어_있으면_월봉에도_중복으로_더하지_않는다() {
        // given
        // 장 마감 후 동기화가 끝난 상태. 당일 거래량이 8월 봉에 두 번 들어가면 안 된다.
        DailyPrices closed = DailyPrices.of(List.of(price(LocalDate.of(2026, 8, 3)), price(TODAY)));
        Quote quote = quote(BigDecimal.valueOf(50000), BigDecimal.valueOf(49000), 1_200L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.MONTH, TODAY);

        // then
        assertThat(chart.candles()).hasSize(1);
        assertThat(chart.candles().getFirst().volume()).isEqualTo(200_000L);
        assertThat(chart.candles().getFirst().closed()).isTrue();
        assertThat(chart.asOf()).isEmpty();
    }

    @Test
    void 시작일과_종료일은_실제_포함된_봉의_거래일이다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(TODAY.minusDays(5)),
                price(TODAY.minusDays(3))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.from()).contains(TODAY.minusDays(5));
        assertThat(chart.to()).contains(TODAY.minusDays(3));
    }

    @Test
    void 봉이_없으면_빈_배열을_반환한다() {
        // given
        DailyPrices 없음 = DailyPrices.of(List.of());

        // when
        Chart chart = Chart.of(없음, Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.candles()).isEmpty();
        assertThat(chart.from()).isEmpty();
        assertThat(chart.to()).isEmpty();
        assertThat(chart.asOf()).isEmpty();
        assertThat(chart.averageVolume()).isEmpty();
    }

    @Test
    void 월봉이면_같은_달_봉을_하나로_묶는다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 7, 1)),
                price(LocalDate.of(2026, 7, 15)),
                price(LocalDate.of(2026, 7, 31)),
                price(LocalDate.of(2026, 8, 3)),
                price(LocalDate.of(2026, 8, 4))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.MONTH, TODAY);

        // then
        assertThat(chart.candles()).extracting(ChartCandle::tradeAt)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 3));
    }

    @Test
    void 묶인_봉의_시가는_첫_봉_고가는_최댓값_저가는_최솟값_종가는_마지막_봉이다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 7, 1), 100, 120, 95, 110, 1_000L),
                price(LocalDate.of(2026, 7, 15), 110, 150, 90, 130, 2_000L),
                price(LocalDate.of(2026, 7, 31), 130, 140, 125, 135, 3_000L)
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.MONTH, TODAY);

        // then
        ChartCandle july = chart.candles().getFirst();
        assertThat(july.open()).isEqualByComparingTo("100");
        assertThat(july.high()).isEqualByComparingTo("150");
        assertThat(july.low()).isEqualByComparingTo("90");
        assertThat(july.close()).isEqualByComparingTo("135");
    }

    @Test
    void 묶인_봉의_거래량은_합계다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 7, 1), 100, 120, 95, 110, 1_000L),
                price(LocalDate.of(2026, 7, 15), 110, 150, 90, 130, 2_000L),
                price(LocalDate.of(2026, 7, 31), 130, 140, 125, 135, 3_000L)
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.MONTH, TODAY);

        // then
        assertThat(chart.candles().getFirst().volume()).isEqualTo(6_000L);
    }

    @Test
    void 묶인_봉의_거래일은_버킷의_첫_거래일이다() {
        // given
        // 7월 1일이 휴장이면 7월 봉의 거래일은 달력상 1일이 아니라 첫 거래일인 7월 2일이다.
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 7, 2)),
                price(LocalDate.of(2026, 7, 3))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.MONTH, TODAY);

        // then
        assertThat(chart.candles().getFirst().tradeAt()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void 구간_시작이_달_중간이면_첫_봉은_불완전한_채로_포함된다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 7, 20)),
                price(LocalDate.of(2026, 7, 21)),
                price(LocalDate.of(2026, 8, 3)),
                price(LocalDate.of(2026, 8, 4))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.MONTH, TODAY);

        // then
        assertThat(chart.candles()).hasSize(2);
        assertThat(chart.candles().getFirst().tradeAt()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(chart.candles().getFirst().volume()).isEqualTo(200_000L);
    }

    @Test
    void 장중이면_마지막_버킷에_당일_봉이_합쳐지고_미확정이다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 8, 3), 100, 120, 95, 110, 1_000L),
                price(LocalDate.of(2026, 8, 4), 110, 130, 105, 125, 2_000L)
        ));
        Quote quote = quote(BigDecimal.valueOf(140), BigDecimal.valueOf(125),
                BigDecimal.valueOf(145), BigDecimal.valueOf(90), 3_000L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.MONTH, TODAY);

        // then
        ChartCandle august = chart.candles().getFirst();
        assertThat(chart.candles()).hasSize(1);
        assertThat(august.tradeAt()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(august.closed()).isFalse();
        assertThat(august.open()).isEqualByComparingTo("100");
        assertThat(august.high()).isEqualByComparingTo("145");
        assertThat(august.low()).isEqualByComparingTo("90");
        assertThat(august.close()).isEqualByComparingTo("140");
        assertThat(august.volume()).isEqualTo(6_000L);
        assertThat(chart.asOf()).contains(PRICE_AT);
    }

    @Test
    void 당일이_새_버킷의_첫날이면_새_봉으로_붙는다() {
        // given
        // 오늘(08-12 수)은 08-10 시작 주에 속하고, 지난주 봉과 같은 버킷이 아니다.
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 8, 6)),
                price(LocalDate.of(2026, 8, 7))
        ));
        Quote quote = quote(BigDecimal.valueOf(50000), BigDecimal.valueOf(49000), 1_200L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.WEEK, TODAY);

        // then
        assertThat(chart.candles()).hasSize(2);
        assertThat(chart.candles().getFirst().closed()).isTrue();
        assertThat(chart.candles().getLast().tradeAt()).isEqualTo(TODAY);
        assertThat(chart.candles().getLast().closed()).isFalse();
    }

    @Test
    void 주봉은_월요일을_기준으로_묶는다() {
        // given
        // 08-03(월)~08-07(금)이 한 주, 08-10(월)~08-11(화)이 다음 주다.
        DailyPrices closed = DailyPrices.of(List.of(
                price(LocalDate.of(2026, 8, 3)),
                price(LocalDate.of(2026, 8, 7)),
                price(LocalDate.of(2026, 8, 10)),
                price(LocalDate.of(2026, 8, 11))
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.WEEK, TODAY);

        // then
        assertThat(chart.candles()).extracting(ChartCandle::tradeAt)
                .containsExactly(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));
        assertThat(chart.candles()).allMatch(candle -> candle.volume() == 200_000L);
    }

    @Test
    void 평균_거래량은_확정_봉만으로_계산한다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(TODAY.minusDays(2), 100, 120, 95, 110, 1_000L),
                price(TODAY.minusDays(1), 110, 130, 105, 125, 3_000L)
        ));
        Quote quote = quote(BigDecimal.valueOf(140), BigDecimal.valueOf(125), 999_999L);

        // when
        Chart chart = Chart.of(closed, Optional.of(quote), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.averageVolume()).contains(2_000L);
    }

    @Test
    void 확정_봉이_스무개보다_적으면_있는_만큼_평균한다() {
        // given
        DailyPrices closed = DailyPrices.of(List.of(
                price(TODAY.minusDays(3), 100, 120, 95, 110, 100L),
                price(TODAY.minusDays(2), 100, 120, 95, 110, 200L),
                price(TODAY.minusDays(1), 100, 120, 95, 110, 300L)
        ));

        // when
        Chart chart = Chart.of(closed, Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.averageVolume()).contains(200L);
    }

    @Test
    void 확정_봉이_스무개를_넘으면_마지막_스무개만_평균한다() {
        // given
        // 거래량이 1부터 25까지인 봉 25개. 마지막 20개(6~25)의 평균은 15다.
        List<DailyPrice> prices = IntStream.rangeClosed(1, 25)
                .mapToObj(day -> price(LocalDate.of(2026, 7, 1).plusDays(day - 1), 100, 120, 95, 110, day))
                .toList();

        // when
        Chart chart = Chart.of(DailyPrices.of(prices), Optional.empty(), ChartInterval.DAY, TODAY);

        // then
        assertThat(chart.averageVolume()).contains(15L);
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return price(tradeAt, 48000, 49500, 47500, 49000, 100_000L);
    }

    private static DailyPrice price(LocalDate tradeAt, long open, long high, long low, long close, long volume) {
        return new DailyPrice(
                tradeAt,
                BigDecimal.valueOf(open),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close),
                volume
        );
    }

    private static Quote quote(BigDecimal price, BigDecimal open, long volume) {
        return quote(price, open, BigDecimal.valueOf(50500), BigDecimal.valueOf(48800), volume);
    }

    private static Quote quote(BigDecimal price, BigDecimal open, BigDecimal high, BigDecimal low, long volume) {
        return new Quote(
                price,
                BigDecimal.valueOf(49000),
                open,
                high,
                low,
                volume,
                BigDecimal.valueOf(60_000_000),
                BigDecimal.valueOf(300_000_000_000L),
                Currency.KRW,
                PRICE_AT,
                PriceTiming.REALTIME
        );
    }
}
