package com.swyp.ploutos.market.chart.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.chart.service.MarketChartDetail;
import com.swyp.ploutos.market.chart.service.MarketChartService;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.chart.LiveCandle;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

@WebMvcTest(MarketChartController.class)
@AutoConfigureMockMvc(addFilters = false)
class MarketChartControllerTest {

    private static final String PATH = "/api/v1/markets/indicators/{indicator}/chart";

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private MarketChartService marketChartService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 지표_차트를_요청하면_봉을_오름차순으로_반환한다() throws Exception {
        // given 9/28·9/29는 실측값이고 9/30은 진행 중인 봉이다
        given(marketChartService.read(MarketIndicator.KOSPI, LocalDate.of(2026, 9, 28), null, null))
                .willReturn(kospiDetail(Optional.of(live())));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI").param("from", "2026-09-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicator").value("KOSPI"))
                .andExpect(jsonPath("$.data.name").value("코스피"))
                .andExpect(jsonPath("$.data.unit").value("POINT"))
                .andExpect(jsonPath("$.data.interval").value("1D"))
                .andExpect(jsonPath("$.data.from").value("2026-09-28"))
                .andExpect(jsonPath("$.data.to").value("2026-09-30"))
                .andExpect(jsonPath("$.data.asOf").value("2026-09-30T10:15:03+09:00"))
                .andExpect(jsonPath("$.data.candles.length()").value(3))
                .andExpect(jsonPath("$.data.candles[0].tradeAt").value("2026-09-28"))
                .andExpect(jsonPath("$.data.candles[0].open").value(7057.86))
                .andExpect(jsonPath("$.data.candles[0].high").value(7065.90))
                .andExpect(jsonPath("$.data.candles[0].low").value(6889.68))
                .andExpect(jsonPath("$.data.candles[0].close").value(6889.74))
                .andExpect(jsonPath("$.data.candles[0].closed").value(true))
                .andExpect(jsonPath("$.data.candles[1].tradeAt").value("2026-09-29"))
                .andExpect(jsonPath("$.data.candles[1].close").value(6870.81))
                .andExpect(jsonPath("$.data.candles[2].tradeAt").value("2026-09-30"))
                .andExpect(jsonPath("$.data.candles[2].close").value(6902.33))
                .andExpect(jsonPath("$.data.candles[2].closed").value(false))
                .andExpect(jsonPath("$.data.candles[0]").value(hasKey("closed")));
    }

    @Test
    void 거래량_필드를_내보내지_않는다() throws Exception {
        // given 지표에는 거래량이 없다
        given(marketChartService.read(MarketIndicator.KOSPI, null, null, null))
                .willReturn(kospiDetail(Optional.empty()));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candles[0].volume").doesNotExist())
                .andExpect(jsonPath("$.data.averageVolume").doesNotExist());
    }

    @Test
    void 진행_중인_봉이_없으면_asOf가_null이다() throws Exception {
        // given 장 시작 전이라 현재값이 이미 확정된 거래일의 것이다
        given(marketChartService.read(MarketIndicator.NASDAQ, null, null, null))
                .willReturn(new MarketChartDetail(MarketIndicator.NASDAQ, ChartInterval.DAY,
                        Chart.of(closedPrices(), Optional.empty(), ChartInterval.DAY)));

        // when & then 키는 남고 값만 null이다
        mockMvc.perform(get(PATH, "NASDAQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hasKey("asOf")))
                .andExpect(jsonPath("$.data.asOf").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("나스닥"))
                .andExpect(jsonPath("$.data.to").value("2026-09-29"))
                .andExpect(jsonPath("$.data.candles.length()").value(2));
    }

    @Test
    void 구간과_봉_단위를_생략하면_서비스에_null로_넘긴다() throws Exception {
        // given 기본값은 서비스가 정한다. 컨트롤러는 받은 것을 그대로 넘긴다
        given(marketChartService.read(MarketIndicator.KOSPI, null, null, null))
                .willReturn(kospiDetail(Optional.empty()));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1D"));
    }

    @Test
    void 잘못된_지표면_400과_P001을_반환한다() throws Exception {
        // given 바인딩에서 막히므로 서비스에 닿지 않는다
        String unknown = "DOW";

        // when & then
        mockMvc.perform(get(PATH, unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 소문자_지표면_400과_P001을_반환한다() throws Exception {
        // given 지표는 대문자만 받는다
        String lowercase = "kospi";

        // when & then
        mockMvc.perform(get(PATH, lowercase))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 잘못된_봉_단위면_400과_P001을_반환한다() throws Exception {
        // given
        given(marketChartService.read(MarketIndicator.KOSPI, null, null, "2W"))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI").param("interval", "2W"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 잘못된_날짜_형식이면_400과_P001을_반환한다() throws Exception {
        // given 서비스에 닿기 전에 바인딩에서 막힌다
        String badDate = "notadate";

        // when & then
        mockMvc.perform(get(PATH, "KOSPI").param("from", badDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 시작일이_종료일보다_뒤면_400과_P001을_반환한다() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 9, 29);
        LocalDate to = LocalDate.of(2026, 9, 1);
        given(marketChartService.read(MarketIndicator.KOSPI, from, to, null))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI").param("from", "2026-09-29").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 구간이_5년을_넘으면_400과_P001을_반환한다() throws Exception {
        // given
        given(marketChartService.read(MarketIndicator.KOSPI, LocalDate.of(2015, 1, 1), null, null))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI").param("from", "2015-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 시세_조회에_실패하면_502와_P007을_반환한다() throws Exception {
        // given
        given(marketChartService.read(MarketIndicator.KOSPI, null, null, null))
                .willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        mockMvc.perform(get(PATH, "KOSPI"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P007"));
    }

    private static MarketChartDetail kospiDetail(Optional<LiveCandle> live) {
        return new MarketChartDetail(MarketIndicator.KOSPI, ChartInterval.DAY,
                Chart.of(closedPrices(), live, ChartInterval.DAY));
    }

    /** 코스피 9/28·9/29 확정 봉. 실측값이다. */
    private static DailyPrices closedPrices() {
        return DailyPrices.of(List.of(
                price(LocalDate.of(2026, 9, 28), "7057.86", "7065.90", "6889.68", "6889.74"),
                price(LocalDate.of(2026, 9, 29), "6844.41", "6898.36", "6782.99", "6870.81")
        ));
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

    private static LiveCandle live() {
        DailyPrice today = price(LocalDate.of(2026, 9, 30), "6875.20", "6910.45", "6861.02", "6902.33");
        return new LiveCandle(today, OffsetDateTime.parse("2026-09-30T10:15:03+09:00"));
    }
}
