package com.swyp.ploutos.stock.summary.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.summary.StockSummary;
import com.swyp.ploutos.stock.summary.service.StockSummaryService;

@WebMvcTest(StockSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockSummaryControllerTest {

    private static final Long STOCK_ID = 1L;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockSummaryService stockSummaryService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 국내_종목의_기본정보를_반환한다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willReturn(new StockSummary(
                STOCK_ID, "삼성전자", "005930", "https://logo/005930.png", Country.KR, Currency.KRW));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(1))
                .andExpect(jsonPath("$.data.profile.name").value("삼성전자"))
                .andExpect(jsonPath("$.data.profile.ticker").value("005930"))
                .andExpect(jsonPath("$.data.profile.logoUrl").value("https://logo/005930.png"))
                .andExpect(jsonPath("$.data.market").value("KR"))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
    }

    @Test
    void 미국_종목은_뉴욕_시간대로_반환한다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willReturn(new StockSummary(
                STOCK_ID, "애플", "AAPL", null, Country.US, Currency.USD));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.market").value("US"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"));
    }

    @Test
    void 로고가_없으면_키는_남고_값은_null이다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willReturn(new StockSummary(
                STOCK_ID, "SK하이닉스", "000660", null, Country.KR, Currency.KRW));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile").value(hasKey("logoUrl")))
                .andExpect(jsonPath("$.data.profile.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.profile.name").value("SK하이닉스"));
    }

    @Test
    void 가격_산업_변동배경은_응답에_없다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willReturn(new StockSummary(
                STOCK_ID, "삼성전자", "005930", null, Country.KR, Currency.KRW));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.movementContext").doesNotExist())
                .andExpect(jsonPath("$.data.profile.industries").doesNotExist());
    }

    @Test
    void 없는_종목이면_404와_P002를_반환한다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void 종목_ID가_정수가_아니면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/stocks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 종목_ID가_양수가_아니면_조회하지_않고_400과_P001을_반환한다(long stockId) throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", stockId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
        then(stockSummaryService).should(never()).read(anyLong());
    }

    @Test
    void 예상하지_못한_오류는_500과_P006으로_상세를_숨긴다() throws Exception {
        // given
        given(stockSummaryService.read(STOCK_ID)).willThrow(new IllegalStateException("시장 10 이 존재하지 않습니다"));

        // when & then
        mockMvc.perform(get("/api/v1/stocks/{stockId}", STOCK_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("P006"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."));
    }
}
