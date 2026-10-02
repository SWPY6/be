package com.swyp.ploutos.industry.flow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.MajorStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.service.IndustryNewsDetail;
import com.swyp.ploutos.industry.flow.service.IndustryNewsService;
import com.swyp.ploutos.news.RelatedNews;
import com.swyp.ploutos.stock.quote.service.QuoteReader;

@WebMvcTest(IndustryNewsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IndustryNewsControllerTest {

    private static final String PATH = "/api/v1/industries/news";
    private static final OffsetDateTime CALCULATED_AT =
            OffsetDateTime.of(2026, 9, 4, 15, 30, 0, 0, ZoneOffset.ofHours(9));

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private IndustryNewsService industryNewsService;

    /** 조회가 외부 시세를 부르지 않는지 확인하려고 주입한다. 한 번도 호출되지 않아야 한다. */
    @MockitoBean
    private QuoteReader quoteReader;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 상승과_하락_카드를_순서대로_응답한다() throws Exception {
        // given
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of(news())),
                detail(IndustryCode.CHEMICAL, 9, "-0.35", Direction.FALLING,
                        SelectedBy.MATCHED, List.of())));

        // when & then
        mockMvc.perform(get(PATH).param("country", "KR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("AUTOMOBILE"))
                .andExpect(jsonPath("$.data[0].displayName").value("자동차"))
                .andExpect(jsonPath("$.data[0].direction").value("RISING"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].avgChangeRate").value(1.61))
                .andExpect(jsonPath("$.data[0].stockCount").value(4))
                .andExpect(jsonPath("$.data[0].risingCount").value(3))
                .andExpect(jsonPath("$.data[0].fallingCount").value(1))
                .andExpect(jsonPath("$.data[1].direction").value("FALLING"))
                .andExpect(jsonPath("$.data[1].rank").value(9))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 국가를_지정하지_않으면_국내를_돌려준다() throws Exception {
        // given
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of())));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("AUTOMOBILE"));
        then(industryNewsService).should().read(Country.KR);
    }

    @Test
    void 뉴스는_제목_출처_발표시각_링크를_내려준다() throws Exception {
        // given
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of(news()))));

        // when & then 저장된 발표 시각에 시장 오프셋을 붙여 내려준다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].news.length()").value(1))
                .andExpect(jsonPath("$.data[0].news[0].title").value("자동차 수출 증가 발표"))
                .andExpect(jsonPath("$.data[0].news[0].publisher").value("산업통상자원부"))
                .andExpect(jsonPath("$.data[0].news[0].publishedAt").value("2026-09-04T09:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].news[0].url").value("https://example.com/news/1"));
    }

    @Test
    void 해외는_발표_시각에_미국_오프셋을_붙인다() throws Exception {
        // given
        given(industryNewsService.read(Country.US)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of(news()))));

        // when & then 2026-09-04 는 서머타임 기간이라 -04:00 이다
        mockMvc.perform(get(PATH).param("country", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].news[0].publishedAt").value("2026-09-04T09:00:00-04:00"));
    }

    @Test
    void 뉴스가_없으면_빈_배열을_응답한다() throws Exception {
        // given
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of())));

        // when & then null 이 아니라 빈 배열이다. 프론트는 길이만 확인하면 된다
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].news").isArray())
                .andExpect(jsonPath("$.data[0].news.length()").value(0));
    }

    @Test
    void 거래대금_변화율은_응답에_넣지_않는다() throws Exception {
        // given 선정에는 쓰지만 화면에는 표시하지 않는 값이다
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of())));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradingValueChangeRate").doesNotExist());
    }

    @Test
    void 선정_근거는_응답에_넣지_않는다() throws Exception {
        // given 대체 선정된 카드다. 화면이 구분해 표시하지 않으므로 내려주지 않는다
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                new IndustryNewsDetail(
                        new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal("1.61"),
                                4, 3, 1, null, List.of(), CALCULATED_AT),
                        Direction.RISING, SelectedBy.CHANGE_RATE_ONLY, List.of())));

        // when & then
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].selectedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].direction").value("RISING"));
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
        given(industryNewsService.read(Country.KR)).willReturn(List.of(
                detail(IndustryCode.AUTOMOBILE, 1, "1.61", Direction.RISING,
                        SelectedBy.MATCHED, List.of())));

        // when
        mockMvc.perform(get(PATH)).andExpect(status().isOk());

        // then
        then(quoteReader).should(never()).read(any());
        then(quoteReader).should(never()).readWithoutTracking(any());
    }

    private static IndustryNewsDetail detail(IndustryCode code, int rank, String avgChangeRate,
            Direction direction, SelectedBy selectedBy, List<RelatedNews> news) {
        RankedIndustryFlow flow = new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate),
                4, 3, 1, new IndustryTradingValue(new BigDecimal("120"), new BigDecimal("100")),
                List.of(new MajorStock(10L, "005380", "현대차", new BigDecimal("3.24"))),
                CALCULATED_AT);
        return new IndustryNewsDetail(flow, direction, selectedBy, news);
    }

    private static RelatedNews news() {
        return new RelatedNews(1L, "자동차 수출 증가 발표", "산업통상자원부",
                LocalDateTime.of(2026, 9, 4, 9, 0), "https://example.com/news/1");
    }
}
