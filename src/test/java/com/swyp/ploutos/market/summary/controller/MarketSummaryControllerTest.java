package com.swyp.ploutos.market.summary.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.summary.service.MarketSummary;
import com.swyp.ploutos.market.summary.service.MarketSummaryService;

@WebMvcTest(MarketSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class MarketSummaryControllerTest {

    private static final String PATH = "/api/v1/markets/summary";
    private static final OffsetDateTime SEOUL = OffsetDateTime.parse("2026-09-30T10:15:03+09:00");
    private static final OffsetDateTime NEW_YORK = OffsetDateTime.parse("2026-09-29T21:15:03-04:00");

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private MarketSummaryService marketSummaryService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 국내_탭을_요청하면_지표_카드를_순서대로_반환한다() throws Exception {
        // given
        given(marketSummaryService.read(MarketRegion.DOMESTIC)).willReturn(domestic());

        // when & then
        mockMvc.perform(get(PATH).param("region", "DOMESTIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value("DOMESTIC"))
                .andExpect(jsonPath("$.data.indicators.length()").value(3))
                .andExpect(jsonPath("$.data.indicators[0].indicator").value("KOSPI"))
                .andExpect(jsonPath("$.data.indicators[0].name").value("코스피"))
                .andExpect(jsonPath("$.data.indicators[0].unit").value("POINT"))
                .andExpect(jsonPath("$.data.indicators[0].value").value(6870.81))
                .andExpect(jsonPath("$.data.indicators[0].change").value(-18.93))
                .andExpect(jsonPath("$.data.indicators[0].changeRate").value(-0.27))
                .andExpect(jsonPath("$.data.indicators[0].valueAt").value("2026-09-30T10:15:03+09:00"))
                .andExpect(jsonPath("$.data.indicators[1].indicator").value("KOSDAQ"))
                .andExpect(jsonPath("$.data.indicators[1].name").value("코스닥"))
                .andExpect(jsonPath("$.data.indicators[1].value").value(849.80))
                .andExpect(jsonPath("$.data.indicators[1].change").value(3.22))
                .andExpect(jsonPath("$.data.indicators[2].indicator").value("USD_KRW"))
                .andExpect(jsonPath("$.data.indicators[2].name").value("원/달러 환율"))
                .andExpect(jsonPath("$.data.indicators[2].unit").value("KRW"))
                .andExpect(jsonPath("$.data.indicators[2].value").value(1354.00))
                .andExpect(jsonPath("$.data.indicators[2].change").value(-5.90));
    }

    @Test
    void 해외_탭을_요청하면_뉴욕_시각으로_반환한다() throws Exception {
        // given
        given(marketSummaryService.read(MarketRegion.OVERSEAS)).willReturn(overseas());

        // when & then 해외 지수는 뉴욕 오프셋, 환율은 서울 오프셋이다
        mockMvc.perform(get(PATH).param("region", "OVERSEAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value("OVERSEAS"))
                .andExpect(jsonPath("$.data.indicators[0].indicator").value("NASDAQ"))
                .andExpect(jsonPath("$.data.indicators[0].valueAt").value("2026-09-29T21:15:03-04:00"))
                .andExpect(jsonPath("$.data.indicators[1].indicator").value("SP500"))
                .andExpect(jsonPath("$.data.indicators[1].name").value("S&P 500"))
                .andExpect(jsonPath("$.data.indicators[2].indicator").value("USD_KRW"))
                .andExpect(jsonPath("$.data.indicators[2].valueAt").value("2026-09-30T10:15:03+09:00"));
    }

    @Test
    void 탭이_없으면_400과_P001을_반환한다() throws Exception {
        // given region 파라미터를 빼고 호출한다

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 탭_값이_잘못되면_400과_P001을_반환한다() throws Exception {
        // given 허용되지 않은 값

        // when & then
        mockMvc.perform(get(PATH).param("region", "KOREA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 탭_값이_소문자면_400과_P001을_반환한다() throws Exception {
        // given 대소문자를 구분한다

        // when & then
        mockMvc.perform(get(PATH).param("region", "domestic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 시세_조회에_실패하면_502와_P007을_반환한다() throws Exception {
        // given 지표 하나라도 실패하면 전체가 실패한다
        given(marketSummaryService.read(any(MarketRegion.class)))
                .willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        mockMvc.perform(get(PATH).param("region", "DOMESTIC"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P007"));
    }

    private static MarketSummary domestic() {
        return new MarketSummary(MarketRegion.DOMESTIC, List.of(
                quote(MarketIndicator.KOSPI, "6870.81", "6889.74", SEOUL),
                quote(MarketIndicator.KOSDAQ, "849.80", "846.58", SEOUL),
                quote(MarketIndicator.USD_KRW, "1354.0000", "1359.9000", SEOUL)
        ));
    }

    private static MarketSummary overseas() {
        return new MarketSummary(MarketRegion.OVERSEAS, List.of(
                quote(MarketIndicator.NASDAQ, "26817.30", "26820.38", NEW_YORK),
                quote(MarketIndicator.SP500, "7675.05", "7683.69", NEW_YORK),
                quote(MarketIndicator.USD_KRW, "1354.0000", "1359.9000", SEOUL)
        ));
    }

    private static IndicatorQuote quote(
            MarketIndicator indicator, String value, String previousClose, OffsetDateTime valueAt) {
        BigDecimal amount = new BigDecimal(value);
        return new IndicatorQuote(
                indicator, amount, new BigDecimal(previousClose), amount, amount, amount, valueAt);
    }
}
