package com.swyp.ploutos.news.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsWindow;
import com.swyp.ploutos.news.StockNewsFeed;
import com.swyp.ploutos.news.service.StockNews;
import com.swyp.ploutos.news.service.StockNewsService;

@WebMvcTest(StockNewsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockNewsControllerTest {

    private static final Long STOCK_ID = 1L;
    private static final String URL = "/api/v1/stocks/{stockId}/news";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-30T14:00:00+09:00");
    private static final NewsWindow WINDOW = NewsWindow.of(null, null, NOW);

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockNewsService stockNewsService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 명세의_응답_필드를_공통_봉투에_담는다() throws Exception {
        // given
        NewsArticle article = NewsArticle.from(
                "<b>삼성전자</b>, HBM 공급 확대", "", "https://news.mt.co.kr/mtview.php?no=1", null,
                OffsetDateTime.parse("2026-09-30T09:12:00+09:00")
        ).orElseThrow();
        given(stockNewsService.read(STOCK_ID, null, null)).willReturn(news(List.of(article), true));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(1))
                .andExpect(jsonPath("$.data.market").value("KR"))
                .andExpect(jsonPath("$.data.window.from").value("2026-09-23T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.window.to").value("2026-09-30T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.fetchedAt").value("2026-09-30T13:55:12+09:00"))
                .andExpect(jsonPath("$.data.windowCovered").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].documentId").value(article.documentId()))
                .andExpect(jsonPath("$.data.items[0].title").value("삼성전자, HBM 공급 확대"))
                .andExpect(jsonPath("$.data.items[0].summary").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].source").value("news.mt.co.kr"))
                .andExpect(jsonPath("$.data.items[0].publisherName").value("머니투데이"))
                .andExpect(jsonPath("$.data.items[0].publishedAt").value("2026-09-30T09:12:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].timestampBasis").value("NAVER_PROVIDED"))
                .andExpect(jsonPath("$.data.items[0].url").value("https://news.mt.co.kr/mtview.php?no=1"))
                .andExpect(jsonPath("$.data.items[0].linkKind").value("ORIGINAL"));
    }

    @Test
    void 관련_기사가_없으면_빈_목록과_0건이다() throws Exception {
        // given
        given(stockNewsService.read(STOCK_ID, null, null)).willReturn(news(List.of(), true));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void 기간을_오프셋_포함_시각으로_받아_서비스에_넘긴다() throws Exception {
        // given
        OffsetDateTime from = OffsetDateTime.parse("2026-09-29T00:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-09-30T00:00:00+09:00");
        given(stockNewsService.read(STOCK_ID, from, to)).willReturn(news(List.of(), true));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID)
                        .param("from", "2026-09-29T00:00:00+09:00")
                        .param("to", "2026-09-30T00:00:00+09:00"))
                .andExpect(status().isOk());
        then(stockNewsService).should().read(STOCK_ID, from, to);
    }

    @Test
    void 오프셋_없는_시각이면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(URL, STOCK_ID)
                        .param("from", "2026-09-29T00:00:00")
                        .param("to", "2026-09-30T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
        then(stockNewsService).should(never()).read(any(), any(), any());
    }

    @Test
    void 종목_ID가_정수가_아니면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 잘못된_기간이면_400과_P001을_반환한다() throws Exception {
        // given
        given(stockNewsService.read(eq(STOCK_ID), any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID).param("from", "2026-09-29T00:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 없는_종목이면_404와_P002를_반환한다() throws Exception {
        // given
        given(stockNewsService.read(STOCK_ID, null, null)).willThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void 공급자_오류면_502와_P008을_반환한다() throws Exception {
        // given
        given(stockNewsService.read(STOCK_ID, null, null)).willThrow(new BusinessException(ErrorCode.NEWS_UNAVAILABLE));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P008"))
                .andExpect(jsonPath("$.error.name").value("NewsUnavailableException"));
    }

    @Test
    void 호출_한도에_걸리면_503과_P009를_반환한다() throws Exception {
        // given
        given(stockNewsService.read(STOCK_ID, null, null))
                .willThrow(new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("P009"))
                .andExpect(jsonPath("$.error.name").value("NewsQuotaExceededException"));
    }

    private static StockNews news(List<NewsArticle> candidates, boolean exhausted) {
        return new StockNews(
                STOCK_ID,
                Country.KR,
                WINDOW,
                OffsetDateTime.parse("2026-09-30T13:55:12+09:00"),
                StockNewsFeed.of("삼성전자", candidates, exhausted, WINDOW)
        );
    }
}
