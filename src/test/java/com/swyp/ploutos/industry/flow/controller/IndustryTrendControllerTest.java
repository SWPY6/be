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
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.IndustryTrendFilter;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.service.IndustryTrendService;
import com.swyp.ploutos.stock.quote.service.QuoteReader;

@WebMvcTest(IndustryTrendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IndustryTrendControllerTest {

    private static final String PATH = "/api/v1/industries/trends";
    private static final OffsetDateTime CALCULATED_AT =
            OffsetDateTime.of(2026, 9, 4, 15, 30, 0, 0, ZoneOffset.ofHours(9));

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private IndustryTrendService industryTrendService;

    /** 조회가 외부 시세를 부르지 않는지 확인하려고 주입한다. 한 번도 호출되지 않아야 한다. */
    @MockitoBean
    private QuoteReader quoteReader;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 산업_카드를_받은_순서대로_응답한다() throws Exception {
        // given 서버가 이미 정렬해 둔 목록이다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL)).willReturn(List.of(
                flow(IndustryCode.CONSTRUCTION, 2, "0.45"),
                flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then 프론트가 다시 정렬하지 않도록 순서를 그대로 내보낸다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("CONSTRUCTION"))
                .andExpect(jsonPath("$.data[1].code").value("AUTOMOBILE"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 국가와_탭을_지정하지_않으면_국내_전체를_돌려준다() throws Exception {
        // given
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then
        mockMvc.perform(get(PATH)).andExpect(status().isOk());
        then(industryTrendService).should().read(Country.KR, IndustryTrendFilter.ALL);
    }

    @Test
    void 탭을_지정하면_그대로_넘긴다() throws Exception {
        // given
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.FALLING))
                .willReturn(List.of(flow(IndustryCode.CHEMICAL, 9, "-0.35")));

        // when & then
        mockMvc.perform(get(PATH).param("filter", "FALLING")).andExpect(status().isOk());
        then(industryTrendService).should().read(Country.KR, IndustryTrendFilter.FALLING);
    }

    @Test
    void 저장된_종목_네_개를_모두_내려준다() throws Exception {
        // given 시장 요약(/flows)은 앞 2개만 쓰지만 산업별 동향은 넷을 다 보여준다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then 여기서 majorStocks() 를 쓰면 2개로 잘린다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stocks.length()").value(4))
                .andExpect(jsonPath("$.data[0].stocks[3].ticker").value("018880"));
    }

    @Test
    void 종목은_코드와_이름과_현재가와_등락률을_내려준다() throws Exception {
        // given
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stocks[0].ticker").value("005380"))
                .andExpect(jsonPath("$.data[0].stocks[0].name").value("현대차"))
                .andExpect(jsonPath("$.data[0].stocks[0].price").value(248000))
                .andExpect(jsonPath("$.data[0].stocks[0].changeRate").value(3.24));
    }

    @Test
    void 종목_식별자는_응답에_넣지_않는다() throws Exception {
        // given 환경마다 auto_increment 값이 달라 외부 식별자가 못 된다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then 종목 상세로 이동할 때는 ticker 를 쓴다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stocks[0].stockId").doesNotExist());
    }

    @Test
    void 국내는_원화_해외는_달러다() throws Exception {
        // given
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));
        given(industryTrendService.read(Country.US, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then
        mockMvc.perform(get(PATH)).andExpect(jsonPath("$.data[0].currency").value("KRW"));
        mockMvc.perform(get(PATH + "?country=US")).andExpect(jsonPath("$.data[0].currency").value("USD"));
    }

    @Test
    void 평균_등락률은_소수_둘째_자리로_내려준다() throws Exception {
        // given 저장된 값은 반올림돼 있지 않다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.333333")));

        // when & then
        mockMvc.perform(get(PATH)).andExpect(jsonPath("$.data[0].avgChangeRate").value(1.33));
    }

    @Test
    void 해당_산업이_없으면_빈_배열을_응답한다() throws Exception {
        // given 전 산업이 오른 날의 하락 탭
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.FALLING))
                .willReturn(List.of());

        // when & then null 이 아니라 빈 배열이다
        mockMvc.perform(get(PATH + "?filter=FALLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 거른_뒤에도_rank_는_전체_기준이다() throws Exception {
        // given 하락 탭으로 두 건만 받는다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.FALLING)).willReturn(List.of(
                flow(IndustryCode.CHEMICAL, 9, "-1.00"),
                flow(IndustryCode.CONSTRUCTION, 8, "-0.50")));

        // when & then 배열 인덱스로 순위를 세면 안 된다
        mockMvc.perform(get(PATH + "?filter=FALLING"))
                .andExpect(jsonPath("$.data[0].rank").value(9))
                .andExpect(jsonPath("$.data[1].rank").value(8));
    }

    @Test
    void 고정_관련_필드는_응답에_없다() throws Exception {
        // given 고정은 프론트가 받은 목록을 재배열하는 일이다
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when & then 산업 목록은 모든 사용자에게 같다
        mockMvc.perform(get(PATH))
                .andExpect(jsonPath("$.data[0].pinned").doesNotExist())
                .andExpect(jsonPath("$.data[0].pinnedOrder").doesNotExist());
    }

    @Test
    void 국가가_잘못되면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(PATH + "?country=JP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 탭이_잘못되면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(PATH + "?filter=UP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 조회는_외부_시세를_호출하지_않는다() throws Exception {
        // given
        given(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL))
                .willReturn(List.of(flow(IndustryCode.AUTOMOBILE, 1, "1.61")));

        // when
        mockMvc.perform(get(PATH));

        // then 값은 갱신이 미리 계산해 저장해 둔 것이다
        then(quoteReader).should(never()).read(any());
        then(quoteReader).should(never()).readWithoutTracking(any());
    }

    /** 시가총액 상위 4개를 담는다. 산업별 동향 카드는 넷을 다 보여준다(RQ-0601). */
    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1, null,
                List.of(
                        new IndustryFlowStock(10L, "005380", "현대차",
                                new BigDecimal("248000"), new BigDecimal("3.24")),
                        new IndustryFlowStock(20L, "012330", "현대모비스",
                                new BigDecimal("254500"), new BigDecimal("-0.78")),
                        new IndustryFlowStock(30L, "000270", "기아",
                                new BigDecimal("102000"), new BigDecimal("1.85")),
                        new IndustryFlowStock(40L, "018880", "한온시스템",
                                new BigDecimal("4320"), new BigDecimal("2.13"))),
                CALCULATED_AT);
    }
}
