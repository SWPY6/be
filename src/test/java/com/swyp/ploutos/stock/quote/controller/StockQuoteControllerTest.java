package com.swyp.ploutos.stock.quote.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.StockQuoteDetail;
import com.swyp.ploutos.stock.quote.service.StockQuoteDetailService;

@WebMvcTest(StockQuoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockQuoteControllerTest {

    private static final Long STOCK_ID = 1L;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockQuoteDetailService stockQuoteDetailService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 주요_지표_8종을_한_응답에_담는다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID)).willReturn(domestic(new BigDecimal("0.95")));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(1))
                .andExpect(jsonPath("$.data.ticker").value("005380"))
                .andExpect(jsonPath("$.data.name").value("현대차"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.price").value(248000))
                .andExpect(jsonPath("$.data.change").value(7783))
                .andExpect(jsonPath("$.data.changeRate").value(3.24))
                .andExpect(jsonPath("$.data.priceAt").value("2026-08-12T14:31:05+09:00"))
                .andExpect(jsonPath("$.data.indicators.previousClose").value(240217))
                .andExpect(jsonPath("$.data.indicators.open").value(244280))
                .andExpect(jsonPath("$.data.indicators.high").value(251224))
                .andExpect(jsonPath("$.data.indicators.low").value(241056))
                .andExpect(jsonPath("$.data.indicators.volume").value(245000))
                .andExpect(jsonPath("$.data.indicators.volumeRatio20d").value(0.95))
                .andExpect(jsonPath("$.data.indicators.marketCap").value(86600000000000L))
                .andExpect(jsonPath("$.data.indicators.tradingValue").value(60800000000L));
    }

    @Test
    void 국내_종목은_실시간으로_표시한다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID)).willReturn(domestic(new BigDecimal("0.95")));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceTiming").value("REALTIME"));
    }

    @Test
    void 미국_종목은_실시간으로_표시한다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID)).willReturn(overseas());

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticker").value("AAPL"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.priceAt").value("2026-08-12T10:31:05-04:00"))
                .andExpect(jsonPath("$.data.priceTiming").value("REALTIME"));
    }

    @Test
    void 평균_거래량이_없으면_배수는_null이다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID)).willReturn(domestic(null));

        // when & then 키는 남고 값만 null이다
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicators").value(hasKey("volumeRatio20d")))
                .andExpect(jsonPath("$.data.indicators.volumeRatio20d").value(nullValue()));
    }

    @Test
    void 없는_종목이면_404와_P002를_반환한다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID)).willThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void 시세_조회에_실패하면_502와_P007을_반환한다() throws Exception {
        // given
        given(stockQuoteDetailService.read(STOCK_ID))
                .willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}/quote", STOCK_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P007"));
    }

    @Test
    void 종목_ID가_정수가_아니면_400과_P001을_반환한다() throws Exception {
        // given
        String path = "/api/v1/stocks/abc/quote";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    private static StockQuoteDetail domestic(BigDecimal volumeRatio20d) {
        Quote quote = new Quote(
                new BigDecimal("248000"),
                new BigDecimal("240217"),
                new BigDecimal("244280"),
                new BigDecimal("251224"),
                new BigDecimal("241056"),
                245_000,
                new BigDecimal("60800000000"),
                new BigDecimal("86600000000000"),
                Currency.KRW,
                OffsetDateTime.parse("2026-08-12T14:31:05+09:00"),
                PriceTiming.REALTIME
        );
        return new StockQuoteDetail(STOCK_ID, "005380", "현대차", quote, volumeRatio20d);
    }

    private static StockQuoteDetail overseas() {
        Quote quote = new Quote(
                new BigDecimal("185.70"),
                new BigDecimal("183.20"),
                new BigDecimal("184.00"),
                new BigDecimal("186.10"),
                new BigDecimal("183.50"),
                52_000_000,
                new BigDecimal("9650000000"),
                new BigDecimal("2850000000000"),
                Currency.USD,
                OffsetDateTime.parse("2026-08-12T10:31:05-04:00"),
                PriceTiming.REALTIME
        );
        return new StockQuoteDetail(STOCK_ID, "AAPL", "애플", quote, new BigDecimal("1.12"));
    }
}
