package com.swyp.ploutos.stock.movers.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.common.exception.GlobalExceptionHandler;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.service.StockMoverDetail;
import com.swyp.ploutos.stock.movers.service.StockMoverService;

@WebMvcTest(StockMoverController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StockMoverControllerTest {

    private static final String PATH = "/api/v1/stocks/movers";

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockMoverService stockMoverService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 조건과_결과_수를_목록과_함께_돌려준다() throws Exception {
        // given 두 종목이 조건에 맞는다
        given(stockMoverService.read(any(), any(), any(), any())).willReturn(
                new StockMoverDetail(MoverCondition.RISING,
                        List.of(mover(10L, "005380", "현대차", "3.42"),
                                mover(20L, "000270", "기아", "1.10"))));

        // when, then 결과 수는 목록 길이와 같다 — 서버가 페이지를 나누지 않는다
        mockMvc.perform(get(PATH).param("condition", "RISING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.condition").value("RISING"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.stocks[0].ticker").value("005380"))
                .andExpect(jsonPath("$.data.stocks[1].ticker").value("000270"));
    }

    @Test
    void 거래량_배수는_응답에_넣지_않는다() throws Exception {
        // given 서비스는 배수를 들고 있다
        given(stockMoverService.read(any(), any(), any(), any())).willReturn(
                new StockMoverDetail(MoverCondition.VOLUME_SURGE,
                        List.of(new StockMover(10L, "005380", "현대차", IndustryCode.AUTOMOBILE,
                                new BigDecimal("324500"), new BigDecimal("3.42"), 685775L,
                                null, null, new BigDecimal("3.02")))));

        // when, then 줄을 세우는 데만 쓰고 화면에는 내보내지 않는다
        mockMvc.perform(get(PATH).param("condition", "VOLUME_SURGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocks[0].volumeRatio").doesNotExist());
    }

    @Test
    void 우리_종목_정보에_없는_줄은_식별자와_산업이_비어_있다() throws Exception {
        // given 외부 순위에만 있는 종목
        given(stockMoverService.read(any(), any(), any(), any())).willReturn(
                new StockMoverDetail(MoverCondition.RISING,
                        List.of(new StockMover(null, "069500", "KODEX 200", null,
                                new BigDecimal("41000"), new BigDecimal("1.20"), 1000L,
                                null, null, null))));

        // when, then 빼지 않고 그대로 싣는다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocks[0].ticker").value("069500"))
                .andExpect(jsonPath("$.data.stocks[0].stockId").doesNotExist())
                .andExpect(jsonPath("$.data.stocks[0].industry").doesNotExist());
    }

    @Test
    void 파라미터를_생략하면_국내_전체_종목이다() throws Exception {
        // given
        given(stockMoverService.read(any(), any(), any(), any()))
                .willReturn(new StockMoverDetail(MoverCondition.ALL, List.of()));

        // when
        mockMvc.perform(get(PATH)).andExpect(status().isOk());

        // then 산업과 검색어는 비어 있다
        then(stockMoverService).should()
                .read(eq(Country.KR), eq(MoverCondition.ALL), isNull(), isNull());
    }

    @Test
    void 산업과_검색어를_그대로_전달한다() throws Exception {
        // given
        given(stockMoverService.read(any(), any(), any(), any()))
                .willReturn(new StockMoverDetail(MoverCondition.RISING, List.of()));

        // when
        mockMvc.perform(get(PATH)
                        .param("country", "US")
                        .param("condition", "RISING")
                        .param("industry", "AUTOMOBILE")
                        .param("query", "현대"))
                .andExpect(status().isOk());

        // then
        then(stockMoverService).should()
                .read(Country.US, MoverCondition.RISING, IndustryCode.AUTOMOBILE, "현대");
    }

    @Test
    void 허용값이_아닌_조건은_잘못된_입력값이다() throws Exception {
        // given 오타가 섞인 조건

        // when, then 서비스까지 가지 않는다
        mockMvc.perform(get(PATH).param("condition", "RISNG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
        then(stockMoverService).should(never()).read(any(), any(), any(), any());
    }

    @Test
    void 허용값이_아닌_국가도_잘못된_입력값이다() throws Exception {
        // given

        // when, then
        mockMvc.perform(get(PATH).param("country", "JP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
        then(stockMoverService).should(never()).read(any(), any(), any(), any());
    }

    private static StockMover mover(Long stockId, String ticker, String name, String changeRate) {
        return new StockMover(stockId, ticker, name, IndustryCode.AUTOMOBILE,
                new BigDecimal("324500"), new BigDecimal(changeRate), 685775L,
                new BigDecimal("222000000000"), new BigDecimal("68000000000000"), null);
    }
}
