package com.swyp.ploutos.stock.chart.controller;

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

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.chart.LiveCandle;
import com.swyp.ploutos.stock.chart.service.StockChartDetail;
import com.swyp.ploutos.stock.chart.service.StockChartService;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

@WebMvcTest(StockChartController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockChartControllerTest {

    private static final Long STOCK_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockChartService stockChartService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 구간과_봉_단위를_생략하면_서비스에_null로_넘긴다() throws Exception {
        // given 기본값은 서비스가 정한다. 컨트롤러는 받은 것을 그대로 넘긴다
        given(stockChartService.read(STOCK_ID, null, null, null))
                .willReturn(detail(ChartInterval.DAY, Optional.empty()));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1D"));
    }

    @Test
    void ISO_날짜를_그대로_서비스에_넘긴다() throws Exception {
        // given
        LocalDate from = LocalDate.of(2025, 1, 2);
        LocalDate to = LocalDate.of(2026, 1, 2);
        given(stockChartService.read(STOCK_ID, from, to, "1M"))
                .willReturn(detail(ChartInterval.MONTH, Optional.empty()));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID)
                        .param("from", "2025-01-02")
                        .param("to", "2026-01-02")
                        .param("interval", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1M"));
    }

    @Test
    void 장중이면_마지막_봉이_진행중_봉이다() throws Exception {
        // given
        given(stockChartService.read(STOCK_ID, null, null, "1D"))
                .willReturn(detail(ChartInterval.DAY, Optional.of(live())));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID).param("interval", "1D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(1))
                .andExpect(jsonPath("$.data.interval").value("1D"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.from").value("2026-08-10"))
                .andExpect(jsonPath("$.data.to").value("2026-08-12"))
                .andExpect(jsonPath("$.data.asOf").value("2026-08-12T14:31:05+09:00"))
                .andExpect(jsonPath("$.data.averageVolume").value(100000))
                .andExpect(jsonPath("$.data.candles.length()").value(3))
                .andExpect(jsonPath("$.data.candles[0].tradeAt").value("2026-08-10"))
                .andExpect(jsonPath("$.data.candles[0].open").value(48000))
                .andExpect(jsonPath("$.data.candles[0].high").value(49500))
                .andExpect(jsonPath("$.data.candles[0].low").value(47500))
                .andExpect(jsonPath("$.data.candles[0].close").value(49000))
                .andExpect(jsonPath("$.data.candles[0].volume").value(100000))
                .andExpect(jsonPath("$.data.candles[0].closed").value(true))
                .andExpect(jsonPath("$.data.candles[2].tradeAt").value("2026-08-12"))
                .andExpect(jsonPath("$.data.candles[2].close").value(50000))
                .andExpect(jsonPath("$.data.candles[2].volume").value(1200))
                .andExpect(jsonPath("$.data.candles[2].closed").value(false));
    }

    @Test
    void 진행중_봉이_없으면_asOf가_null이다() throws Exception {
        // given
        given(stockChartService.read(STOCK_ID, null, null, "1D"))
                .willReturn(detail(ChartInterval.DAY, Optional.empty()));

        // when & then 키는 남고 값만 null이다
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID).param("interval", "1D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hasKey("asOf")))
                .andExpect(jsonPath("$.data.asOf").value(nullValue()))
                .andExpect(jsonPath("$.data.to").value("2026-08-11"))
                .andExpect(jsonPath("$.data.candles.length()").value(2))
                .andExpect(jsonPath("$.data.candles[1].closed").value(true));
    }

    @Test
    void 봉이_없으면_빈_배열과_null_요약값을_반환한다() throws Exception {
        // given
        StockChartDetail empty = new StockChartDetail(STOCK_ID, ChartInterval.DAY, Currency.KRW,
                Chart.of(DailyPrices.of(List.of()), Optional.empty(), ChartInterval.DAY));
        given(stockChartService.read(STOCK_ID, null, null, null)).willReturn(empty);

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candles.length()").value(0))
                .andExpect(jsonPath("$.data.from").value(nullValue()))
                .andExpect(jsonPath("$.data.to").value(nullValue()))
                .andExpect(jsonPath("$.data.averageVolume").value(nullValue()));
    }

    @Test
    void 잘못된_봉_단위면_400과_P001을_반환한다() throws Exception {
        // given
        given(stockChartService.read(STOCK_ID, null, null, "2W"))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID).param("interval", "2W"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 잘못된_날짜_형식이면_400과_P001을_반환한다() throws Exception {
        // given 서비스에 닿기 전에 바인딩에서 막힌다
        String badDate = "notadate";

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID).param("from", badDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 구간이_5년을_넘으면_400과_P001을_반환한다() throws Exception {
        // given
        LocalDate from = LocalDate.of(2015, 1, 1);
        given(stockChartService.read(STOCK_ID, from, null, null))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID).param("from", "2015-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 없는_종목이면_404와_P002를_반환한다() throws Exception {
        // given
        given(stockChartService.read(STOCK_ID, null, null, null))
                .willThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void 시세_조회에_실패하면_502와_P007을_반환한다() throws Exception {
        // given
        given(stockChartService.read(STOCK_ID, null, null, null))
                .willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/chart", STOCK_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P007"));
    }

    @Test
    void 종목_ID가_정수가_아니면_400과_P001을_반환한다() throws Exception {
        // given
        String path = "/api/v1/stocks/abc/chart";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    private static StockChartDetail detail(ChartInterval interval, Optional<LiveCandle> live) {
        DailyPrices closed = DailyPrices.of(List.of(price(TODAY.minusDays(2)), price(TODAY.minusDays(1))));
        return new StockChartDetail(STOCK_ID, interval, Currency.KRW,
                Chart.of(closed, live, ChartInterval.DAY));
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return new DailyPrice(
                tradeAt,
                new BigDecimal("48000"),
                new BigDecimal("49500"),
                new BigDecimal("47500"),
                new BigDecimal("49000"),
                100_000L
        );
    }

    private static LiveCandle live() {
        DailyPrice today = new DailyPrice(
                TODAY,
                new BigDecimal("48000"),
                new BigDecimal("50500"),
                new BigDecimal("47800"),
                new BigDecimal("50000"),
                1_200L
        );
        return new LiveCandle(today, OffsetDateTime.parse("2026-08-12T14:31:05+09:00"));
    }
}
