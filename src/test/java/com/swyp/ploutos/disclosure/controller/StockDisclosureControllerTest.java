package com.swyp.ploutos.disclosure.controller;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;
import com.swyp.ploutos.disclosure.service.StockDisclosureService;
import com.swyp.ploutos.disclosure.service.StockDisclosures;

@WebMvcTest(StockDisclosureController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockDisclosureControllerTest {

    private static final Long STOCK_ID = 1L;
    private static final String URL = "/api/v1/stocks/{stockId}/disclosures";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-02T14:00:00+09:00");
    private static final DisclosureWindow WINDOW = DisclosureWindow.of(null, null, NOW);
    private static final FiledDateRange RANGE = new FiledDateRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private StockDisclosureService stockDisclosureService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 명세의_응답_필드를_공통_봉투에_담는다() throws Exception {
        // given
        Disclosure disclosure = Disclosure.dart(
                "20260930000123", "[기재정정]주요사항보고서(자기주식취득결정)", "삼성전자", "삼성전자", "유", "20260930"
        ).orElseThrow();
        given(stockDisclosureService.read(STOCK_ID, null, null))
                .willReturn(fetched(StockDisclosureFeed.of(List.of(disclosure), true, WINDOW, SEOUL)));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(1))
                .andExpect(jsonPath("$.data.market").value("KR"))
                .andExpect(jsonPath("$.data.window.from").value("2026-09-02T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.window.to").value("2026-10-02T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.windowPrecision").value("DATE_EXPANDED"))
                .andExpect(jsonPath("$.data.filedDateRange.from").value("2026-09-02"))
                .andExpect(jsonPath("$.data.filedDateRange.to").value("2026-10-02"))
                .andExpect(jsonPath("$.data.source").value("DART"))
                .andExpect(jsonPath("$.data.fetchedAt").value("2026-10-02T13:55:12+09:00"))
                .andExpect(jsonPath("$.data.coverage").value("COMPLETE"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].provider").value("DART"))
                .andExpect(jsonPath("$.data.items[0].providerDocumentId").value("20260930000123"))
                .andExpect(jsonPath("$.data.items[0].type").value("DISCLOSURE"))
                .andExpect(jsonPath("$.data.items[0].title").value("[기재정정]주요사항보고서(자기주식취득결정)"))
                .andExpect(jsonPath("$.data.items[0].issuerName").value("삼성전자"))
                .andExpect(jsonPath("$.data.items[0].filerName").value("삼성전자"))
                .andExpect(jsonPath("$.data.items[0].remark").value("유"))
                .andExpect(jsonPath("$.data.items[0].filedDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data.items[0].publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].datePrecision").value("DATE"))
                .andExpect(jsonPath("$.data.items[0].timeBasis").value("RECEIPT_DATE"))
                .andExpect(jsonPath("$.data.items[0].summary").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].summaryStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.items[0].url")
                        .value("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260930000123"))
                .andExpect(jsonPath("$.data.items[0].linkKind").value("DART_VIEWER"));
    }

    @Test
    void 공시가_없으면_빈_목록과_정상_0건이다() throws Exception {
        // given
        given(stockDisclosureService.read(STOCK_ID, null, null))
                .willReturn(fetched(StockDisclosureFeed.of(List.of(), true, WINDOW, SEOUL)));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.coverage").value("COMPLETE"));
    }

    @Test
    void 미국_종목은_SEC_접수_시각을_뉴욕_시각으로_주고_Form_원문과_한글_라벨을_함께_준다() throws Exception {
        // given
        OffsetDateTime nyNow = OffsetDateTime.parse("2026-10-01T20:00:00-04:00");
        DisclosureWindow usWindow = DisclosureWindow.of(null, null, nyNow);
        Disclosure disclosure = Disclosure.sec(
                "0000320193", "0001140361-26-038028", "4", "FORM 4", "Apple Inc.", "2026-09-29",
                "2026-09-29T22:44:50.000Z", "xslF345X06/form4.xml"
        ).orElseThrow();
        given(stockDisclosureService.read(STOCK_ID, null, null)).willReturn(new StockDisclosures(
                STOCK_ID, Country.US, DisclosureSource.SEC, usWindow,
                new FiledDateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), nyNow,
                StockDisclosureFeed.of(List.of(disclosure), true, usWindow, ZoneId.of("America/New_York"))
        ));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.market").value("US"))
                .andExpect(jsonPath("$.data.source").value("SEC"))
                .andExpect(jsonPath("$.data.windowPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.items[0].provider").value("SEC"))
                .andExpect(jsonPath("$.data.items[0].providerDocumentId").value("0001140361-26-038028"))
                .andExpect(jsonPath("$.data.items[0].title").value("Apple Inc. 4"))
                .andExpect(jsonPath("$.data.items[0].formType").value("4"))
                .andExpect(jsonPath("$.data.items[0].formLabel").value("내부자 지분 변동"))
                .andExpect(jsonPath("$.data.items[0].filerName").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].filedDate").value("2026-09-29"))
                .andExpect(jsonPath("$.data.items[0].publishedAt").value("2026-09-29T18:44:50-04:00"))
                .andExpect(jsonPath("$.data.items[0].datePrecision").value("SECOND"))
                .andExpect(jsonPath("$.data.items[0].timeBasis").value("ACCEPTANCE_TIME"))
                .andExpect(jsonPath("$.data.items[0].url").value(
                        "https://www.sec.gov/Archives/edgar/data/320193/000114036126038028/xslF345X06/form4.xml"))
                .andExpect(jsonPath("$.data.items[0].linkKind").value("SEC_DOCUMENT"));
    }

    @Test
    void 미매핑이면_빈_목록이지만_0건과_구분되고_조회_정보는_null이다() throws Exception {
        // given
        given(stockDisclosureService.read(STOCK_ID, null, null)).willReturn(new StockDisclosures(
                STOCK_ID, Country.KR, DisclosureSource.DART, WINDOW, null, null, StockDisclosureFeed.unmapped()
        ));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage").value("UNMAPPED"))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.source").value("DART"))
                .andExpect(jsonPath("$.data.windowPrecision").value(nullValue()))
                .andExpect(jsonPath("$.data.filedDateRange").value(nullValue()))
                .andExpect(jsonPath("$.data.fetchedAt").value(nullValue()));
    }

    @Test
    void 기간을_오프셋_포함_시각으로_받아_서비스에_넘긴다() throws Exception {
        // given
        OffsetDateTime from = OffsetDateTime.parse("2026-10-01T15:30:00+09:00");
        given(stockDisclosureService.read(STOCK_ID, from, NOW))
                .willReturn(fetched(StockDisclosureFeed.of(List.of(), true, WINDOW, SEOUL)));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID)
                        .param("from", "2026-10-01T15:30:00+09:00")
                        .param("to", "2026-10-02T14:00:00+09:00"))
                .andExpect(status().isOk());
        then(stockDisclosureService).should().read(STOCK_ID, from, NOW);
    }

    @Test
    void 오프셋_없는_시각이면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(URL, STOCK_ID)
                        .param("from", "2026-10-01T00:00:00")
                        .param("to", "2026-10-02T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
        then(stockDisclosureService).should(never()).read(any(), any(), any());
    }

    @Test
    void 종목_ID가_양의_정수가_아니면_서비스를_부르지_않고_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get(URL, "abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("P001"));
        mockMvc.perform(get(URL, 0)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("P001"));
        mockMvc.perform(get(URL, -1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("P001"));
        then(stockDisclosureService).should(never()).read(any(), any(), any());
    }

    @Test
    void 잘못된_기간이면_400과_P001을_반환한다() throws Exception {
        // given
        given(stockDisclosureService.read(eq(STOCK_ID), any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID).param("from", "2026-10-01T00:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    void 없는_종목이면_404와_P002를_반환한다() throws Exception {
        // given
        given(stockDisclosureService.read(STOCK_ID, null, null))
                .willThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("P002"));
    }

    @Test
    void 공급자_오류면_502와_P010을_반환한다() throws Exception {
        // given
        given(stockDisclosureService.read(STOCK_ID, null, null))
                .willThrow(new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("P010"))
                .andExpect(jsonPath("$.error.name").value("DisclosureUnavailableException"))
                .andExpect(jsonPath("$.error.message").value("공시를 불러올 수 없습니다."));
    }

    @Test
    void 호출이_제한되면_503과_P011을_반환한다() throws Exception {
        // given
        given(stockDisclosureService.read(STOCK_ID, null, null))
                .willThrow(new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED));

        // when & then
        mockMvc.perform(get(URL, STOCK_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("P011"))
                .andExpect(jsonPath("$.error.name").value("DisclosureQuotaExceededException"))
                .andExpect(jsonPath("$.error.message").value("공시 조회가 일시적으로 제한되었습니다."));
    }

    private static StockDisclosures fetched(StockDisclosureFeed feed) {
        return new StockDisclosures(
                STOCK_ID, Country.KR, DisclosureSource.DART, WINDOW, RANGE, OffsetDateTime.parse("2026-10-02T13:55:12+09:00"), feed
        );
    }
}
