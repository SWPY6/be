package com.swyp.ploutos.industry.flow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.swyp.ploutos.industry.flow.MajorStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.service.IndustryFlowReader;
import com.swyp.ploutos.stock.quote.service.QuoteReader;

@WebMvcTest(IndustryFlowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IndustryFlowControllerTest {

    private static final String PATH = "/api/v1/industries/flows";
    private static final OffsetDateTime CALCULATED_AT =
            OffsetDateTime.of(2026, 9, 28, 10, 0, 7, 0, ZoneOffset.ofHours(9));

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private IndustryFlowReader industryFlowReader;

    /** 조회가 외부 시세를 부르지 않는지 확인하려고 주입한다. 이 테스트에서는 한 번도 호출되지 않아야 한다. */
    @MockitoBean
    private QuoteReader quoteReader;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 산업을_받은_순서_그대로_응답한다() throws Exception {
        // given
        given(industryFlowReader.read(Country.KR)).willReturn(List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", 87),
                flow(IndustryCode.CONSTRUCTION, 2, "0.45", 94),
                flow(IndustryCode.CHEMICAL, 3, "-0.35", 41)));

        // when & then 순서를 정하는 책임은 리더에 있고 컨트롤러는 다시 정렬하지 않는다
        mockMvc.perform(get(PATH).param("country", "KR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("AUTOMOBILE"))
                .andExpect(jsonPath("$.data[0].displayName").value("자동차"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].avgChangeRate").value(1.61))
                .andExpect(jsonPath("$.data[0].stockCount").value(87))
                .andExpect(jsonPath("$.data[2].rank").value(3))
                .andExpect(jsonPath("$.data[2].avgChangeRate").value(-0.35))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 국가를_지정하지_않으면_국내를_돌려준다() throws Exception {
        // given
        given(industryFlowReader.read(Country.KR)).willReturn(List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", 87)));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("AUTOMOBILE"));
        then(industryFlowReader).should().read(Country.KR);
    }

    @Test
    void 산업마다_계산_시각을_시장_오프셋으로_내려준다() throws Exception {
        // given
        given(industryFlowReader.read(Country.KR)).willReturn(List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", 87)));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].calculatedAt").value("2026-09-28T10:00:07+09:00"))
                .andExpect(jsonPath("$.data[0].majorStocks[0].ticker").value("005380"))
                .andExpect(jsonPath("$.data[0].majorStocks[0].name").value("현대차"))
                .andExpect(jsonPath("$.data[0].majorStocks[0].changeRate").value(3.24));
    }

    @Test
    void 대표_종목이_없으면_빈_배열을_응답한다() throws Exception {
        // given 계산된 적 없는 산업
        given(industryFlowReader.read(Country.KR)).willReturn(List.of(
                new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal("0.00"), 0,
                        List.of(), null)));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].majorStocks").isArray())
                .andExpect(jsonPath("$.data[0].majorStocks.length()").value(0))
                .andExpect(jsonPath("$.data[0].calculatedAt").doesNotExist());
    }

    @Test
    void 국가가_잘못되면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(PATH).param("country", "JP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 조회는_외부_시세를_호출하지_않는다() throws Exception {
        // given
        given(industryFlowReader.read(Country.KR)).willReturn(List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", 87)));

        // when
        mockMvc.perform(get(PATH)).andExpect(status().isOk());

        // then
        then(quoteReader).should(never()).read(any());
    }

    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate, int stockCount) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), stockCount,
                List.of(new MajorStock("005380", "현대차", new BigDecimal("3.24"))),
                CALCULATED_AT);
    }

}
