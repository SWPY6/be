package com.swyp.ploutos.disclosure.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureSearchResult;

import tools.jackson.databind.json.JsonMapper;

class DartDisclosureProviderTest {

    private static final String BASE_URL = "https://dart.test";
    private static final String LIST_URL = BASE_URL + DartDisclosureProvider.PATH;
    private static final String CORP_CODE = "00126380";
    private static final FiledDateRange RANGE =
            new FiledDateRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 2));

    private static final String ONE_ITEM = """
            {
              "status": "000", "message": "정상",
              "page_no": 1, "page_count": 100, "total_count": 1, "total_page": 1,
              "list": [{
                "corp_cls": "Y", "corp_name": "삼성전자", "corp_code": "00126380", "stock_code": "005930",
                "report_nm": "[기재정정]주요사항보고서(자기주식취득결정)  ",
                "rcept_no": "20260930000123", "flr_nm": "삼성전자", "rcept_dt": "20260930", "rm": "유"
              }]
            }
            """;

    private MockRestServiceServer server;
    private CountingBudget budget;
    private DartDisclosureProvider provider;

    @BeforeEach
    void setUp() {
        budget = new CountingBudget(Integer.MAX_VALUE);
        bindProvider();
    }

    @Test
    void 법인_코드와_접수일_범위로_정정_전_보고서까지_최신순_1페이지를_요청한다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("corp_code", CORP_CODE))
                .andExpect(queryParam("bgn_de", "20260902"))
                .andExpect(queryParam("end_de", "20261002"))
                .andExpect(queryParam("last_reprt_at", "N"))
                .andExpect(queryParam("sort", "date"))
                .andExpect(queryParam("sort_mth", "desc"))
                .andExpect(queryParam("page_no", "1"))
                .andExpect(queryParam("page_count", "100"))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        provider.search(CORP_CODE, RANGE);

        // then
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 응답_공시를_정제하고_접수일만_날짜로_읽는다() {
        // given
        respond(ONE_ITEM);

        // when
        DisclosureSearchResult result = provider.search(CORP_CODE, RANGE);

        // then
        Disclosure disclosure = result.disclosures().getFirst();
        assertThat(result.disclosures()).hasSize(1);
        assertThat(disclosure.documentId()).isEqualTo("20260930000123");
        assertThat(disclosure.title()).isEqualTo("[기재정정]주요사항보고서(자기주식취득결정)");
        assertThat(disclosure.remark()).isEqualTo("유");
        assertThat(disclosure.filedDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(disclosure.acceptedAt()).isNull();
        assertThat(result.exhausted()).isTrue();
    }

    @Test
    void 전체_건수가_받은_건수보다_많으면_결과가_더_남은_것이다() {
        // given
        respond(ONE_ITEM.replace("\"total_count\": 1", "\"total_count\": 250"));

        // when
        DisclosureSearchResult result = provider.search(CORP_CODE, RANGE);

        // then
        assertThat(result.exhausted()).isFalse();
    }

    @Test
    void 조회된_데이터가_없음_013이면_정상_0건이다() {
        // given
        respond("{\"status\": \"013\", \"message\": \"조회된 데이타가 없습니다.\"}");

        // when
        DisclosureSearchResult result = provider.search(CORP_CODE, RANGE);

        // then
        assertThat(result.disclosures()).isEmpty();
        assertThat(result.exhausted()).isTrue();
    }

    @Test
    void 요청_제한_020과_점검_800은_한도_초과다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL)))
                .andRespond(withSuccess("{\"status\": \"020\", \"message\": \"요청 제한\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL)))
                .andRespond(withSuccess("{\"status\": \"800\", \"message\": \"점검\"}", MediaType.APPLICATION_JSON));

        // when & then
        assertError(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        assertError(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    @Test
    void 인증키_오류_010은_HTTP_200이어도_공급_실패다() {
        // given
        respond("{\"status\": \"010\", \"message\": \"등록되지 않은 키입니다.\"}");

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 쓸_수_없는_레코드는_빼고_나머지를_돌려준다() {
        // given
        String body = ONE_ITEM.replace("\"list\": [{", """
                "list": [{"rcept_no": "bad", "report_nm": "분기보고서", "rcept_dt": "20260930"}, {""");
        respond(body);

        // when
        DisclosureSearchResult result = provider.search(CORP_CODE, RANGE);

        // then
        assertThat(result.disclosures()).hasSize(1);
    }

    @Test
    void 레코드가_있는데_모두_쓸_수_없으면_공급_실패다() {
        // given
        respond(ONE_ITEM.replace("\"rcept_dt\": \"20260930\"", "\"rcept_dt\": \"\""));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 서버_오류_후_재시도에_성공하면_결과를_돌려주고_예산은_두_번_쓴다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL))).andRespond(withServerError());
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL)))
                .andRespond(withSuccess(ONE_ITEM, MediaType.APPLICATION_JSON));

        // when
        DisclosureSearchResult result = provider.search(CORP_CODE, RANGE);

        // then
        assertThat(result.disclosures()).hasSize(1);
        assertThat(budget.consumed).isEqualTo(2);
        server.verify();
    }

    @Test
    void 타임아웃이_두_번_나면_공급_실패다() {
        // given
        server.expect(times(2), requestTo(Matchers.startsWith(LIST_URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void HTTP_4xx는_재시도하지_않고_공급_실패다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void JSON이_아닌_응답이면_공급_실패다() {
        // given
        respond("<html>gateway</html>");

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 예산이_없으면_DART를_호출하지_않는다() {
        // given
        budget = new CountingBudget(0);
        bindProvider();

        // when & then
        assertError(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        server.verify();
    }

    private void bindProvider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new DartDisclosureProvider(builder.build(), budget, JsonMapper.builder().build());
    }

    private void respond(String body) {
        server.expect(once(), requestTo(Matchers.startsWith(LIST_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> provider.search(CORP_CODE, RANGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }
}
