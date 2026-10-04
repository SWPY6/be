package com.swyp.ploutos.market.chart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.market.IndicatorUnit;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.chart.service.MarketChartDetail;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

import tools.jackson.databind.json.JsonMapper;

class MarketChartResponseTest {

    private static final LocalDate TRADE_AT = LocalDate.of(2026, 9, 29);

    @Test
    void 가격을_소수_둘째자리로_반올림한다() {
        // given 환율은 소수 넷째 자리로 저장된다. 표시용으로는 둘째 자리까지만 쓴다
        MarketChartDetail detail = detail(MarketIndicator.USD_KRW, new DailyPrice(
                TRADE_AT,
                new BigDecimal("1358.2050"),
                new BigDecimal("1360.5149"),
                new BigDecimal("1357.1250"),
                new BigDecimal("1359.9099"),
                0L
        ));

        // when
        MarketChartResponse response = MarketChartResponse.from(detail);

        // then 자릿수까지 둘째 자리로 고정된다
        MarketChartResponse.Candle candle = response.candles().getFirst();
        assertThat(candle.open()).isEqualTo(new BigDecimal("1358.21"));
        assertThat(candle.high()).isEqualTo(new BigDecimal("1360.51"));
        assertThat(candle.low()).isEqualTo(new BigDecimal("1357.13"));
        assertThat(candle.close()).isEqualTo(new BigDecimal("1359.91"));
    }

    @Test
    void 거래량_필드가_없다() {
        // given 지표에는 거래량이 없다. 집계가 만든 0을 내보내면 "거래가 없었다"로 읽힌다
        MarketChartDetail detail = detail(MarketIndicator.KOSPI, price());

        // when
        String json = JsonMapper.builder().build().writeValueAsString(MarketChartResponse.from(detail));

        // then
        assertThat(json).doesNotContain("volume").contains("\"close\":6870.81");
    }

    @Test
    void 지표의_표시명과_단위를_함께_내보낸다() {
        // given 프론트가 지표 이름 표를 따로 들고 있지 않도록 서버가 준다
        MarketChartDetail detail = detail(MarketIndicator.USD_KRW, price());

        // when
        MarketChartResponse response = MarketChartResponse.from(detail);

        // then
        assertThat(response.indicator()).isEqualTo(MarketIndicator.USD_KRW);
        assertThat(response.name()).isEqualTo("원/달러 환율");
        assertThat(response.unit()).isEqualTo(IndicatorUnit.KRW);
        assertThat(response.interval()).isEqualTo("1D");
    }

    @Test
    void 봉이_없으면_빈_배열과_null_구간을_반환한다() {
        // given
        MarketChartDetail detail = new MarketChartDetail(
                MarketIndicator.KOSPI,
                ChartInterval.DAY,
                Chart.of(DailyPrices.of(List.of()), Optional.empty(), ChartInterval.DAY)
        );

        // when
        MarketChartResponse response = MarketChartResponse.from(detail);

        // then
        assertThat(response.candles()).isEmpty();
        assertThat(response.from()).isNull();
        assertThat(response.to()).isNull();
        assertThat(response.asOf()).isNull();
    }

    private static MarketChartDetail detail(MarketIndicator indicator, DailyPrice price) {
        return new MarketChartDetail(
                indicator,
                ChartInterval.DAY,
                Chart.of(DailyPrices.of(List.of(price)), Optional.empty(), ChartInterval.DAY)
        );
    }

    private static DailyPrice price() {
        return new DailyPrice(
                TRADE_AT,
                new BigDecimal("6844.41"),
                new BigDecimal("6898.36"),
                new BigDecimal("6782.99"),
                new BigDecimal("6870.81"),
                0L
        );
    }
}
