package com.swyp.ploutos.disclosure.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.Disclosure.LinkKind;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureSearchResult;
import com.swyp.ploutos.external.sec.SecApiProperties;

import tools.jackson.databind.json.JsonMapper;

class SecDisclosureProviderTest {

    private static final String DATA_URL = "https://data.sec.test";
    private static final String CIK = "0000320193";
    private static final String SUBMISSIONS_URL = DATA_URL + "/submissions/CIK0000320193.json";
    private static final FiledDateRange RANGE = new FiledDateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

    private MockRestServiceServer server;
    private CountingBudget budget;
    private SecDisclosureProvider provider;

    @BeforeEach
    void setUp() {
        budget = new CountingBudget(Integer.MAX_VALUE);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new SecDisclosureProvider(
                new SecFetcher(builder.build(), budget),
                new SecApiProperties("Ploutos dev@example.com", DATA_URL, "https://www.sec.test"),
                JsonMapper.builder().build()
        );
    }

    @Test
    void 법인_CIK로_submissions를_받아_범위에_든_공시를_시각과_법인_CIK_경로로_정제한다() {
        // given 2026-10-02 실제 응답과 같은 열 배열 구조
        respond(submissions(List.of(
                row("0001140361-26-038028", "2026-09-29", "2026-09-29T22:44:50.000Z", "4", "xslF345X06/form4.xml", "FORM 4"),
                row("0000320193-25-000079", "2025-10-31", "2025-10-31T10:01:26.000Z", "10-K", "aapl-20250927.htm", "10-K")
        ), false));

        // when
        DisclosureSearchResult result = provider.search(CIK, RANGE);

        // then
        assertThat(result.disclosures()).hasSize(1);
        Disclosure disclosure = result.disclosures().getFirst();
        assertThat(disclosure.documentId()).isEqualTo("0001140361-26-038028");
        assertThat(disclosure.acceptedAt()).isEqualTo(Instant.parse("2026-09-29T22:44:50Z"));
        assertThat(disclosure.issuerName()).isEqualTo("Apple Inc.");
        assertThat(disclosure.formLabel()).isEqualTo("내부자 지분 변동");
        assertThat(disclosure.url())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/320193/000114036126038028/xslF345X06/form4.xml");
        assertThat(disclosure.linkKind()).isEqualTo(LinkKind.SEC_DOCUMENT);
        assertThat(result.exhausted()).isTrue();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 범위는_접수_시각의_뉴욕_날짜로_고른다() {
        // given 2026-10-02 01:00 UTC = 2026-10-01 21:00 EDT → 범위 마지막 날
        respond(submissions(List.of(
                row("0000320193-26-000002", "2026-10-02", "2026-10-02T01:00:00.000Z", "8-K", "a.htm", "8-K"),
                row("0000320193-26-000001", "2026-08-31", "2026-08-31T12:00:00.000Z", "8-K", "a.htm", "8-K")
        ), false));

        // when
        DisclosureSearchResult result = provider.search(CIK, RANGE);

        // then
        assertThat(result.disclosures()).extracting(Disclosure::documentId).containsExactly("0000320193-26-000002");
    }

    @Test
    void 과거_이력_파일이_있고_recent가_조회_시작일에_닿지_못하면_끝까지_받지_못한_것이다() {
        // given
        respond(submissions(List.of(
                row("0000320193-26-000001", "2026-09-15", "2026-09-15T12:00:00.000Z", "8-K", "a.htm", "8-K")
        ), true));

        // when
        DisclosureSearchResult result = provider.search(CIK, RANGE);

        // then
        assertThat(result.exhausted()).isFalse();
    }

    @Test
    void 범위에_든_공시가_100건을_넘으면_최신_100건만_주고_끝까지_받지_못한_것이다() {
        // given
        List<String> rows = new ArrayList<>(IntStream.range(0, 101)
                .mapToObj(i -> row("0000320193-26-%06d".formatted(i), "2026-09-20",
                        "2026-09-20T12:%02d:%02d.000Z".formatted(i / 60, i % 60), "4", "a.xml", "FORM 4"))
                .toList());
        rows.add(row("0000320193-26-999999", "2026-08-01", "2026-08-01T12:00:00.000Z", "4", "a.xml", "FORM 4"));
        respond(submissions(rows, false));

        // when
        DisclosureSearchResult result = provider.search(CIK, RANGE);

        // then
        assertThat(result.disclosures()).hasSize(SecDisclosureProvider.MAX_ITEMS);
        assertThat(result.disclosures().getFirst().documentId()).isEqualTo("0000320193-26-000100");
        assertThat(result.exhausted()).isFalse();
    }

    @Test
    void 열_배열_길이가_다르면_공급_실패다() {
        // given
        String body = submissions(List.of(
                row("0000320193-26-000001", "2026-09-15", "2026-09-15T12:00:00.000Z", "8-K", "a.htm", "8-K")
        ), false).replace("\"form\":[\"8-K\"]", "\"form\":[\"8-K\",\"10-K\"]");
        respond(body);

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 레코드가_있는데_모두_쓸_수_없으면_공급_실패다() {
        // given
        respond(submissions(List.of(
                row("bad", "2026-09-15", "2026-09-15T12:00:00.000Z", "8-K", "a.htm", "8-K")
        ), false));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 제출_이력이_없으면_정상_0건이다() {
        // given
        respond(submissions(List.of(), false));

        // when
        DisclosureSearchResult result = provider.search(CIK, RANGE);

        // then
        assertThat(result.disclosures()).isEmpty();
        assertThat(result.exhausted()).isTrue();
    }

    @Test
    void JSON이_아니면_공급_실패다() {
        // given
        respond("<html>blocked</html>");

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    private void respond(String body) {
        server.expect(once(), requestTo(SUBMISSIONS_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> provider.search(CIK, RANGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    /** 한 행 = accessionNumber, filingDate, acceptanceDateTime, form, primaryDocument, primaryDocDescription */
    private static String row(String... values) {
        return String.join("\u0001", values);
    }

    private static String submissions(List<String> rows, boolean hasOlderFiles) {
        String[] names = {"accessionNumber", "filingDate", "acceptanceDateTime", "form", "primaryDocument",
                "primaryDocDescription"};
        String recent = IntStream.range(0, names.length)
                .mapToObj(column -> "\"" + names[column] + "\":[" + rows.stream()
                        .map(row -> "\"" + row.split("\u0001")[column] + "\"")
                        .collect(Collectors.joining(",")) + "]")
                .collect(Collectors.joining(","));
        String files = hasOlderFiles
                ? "[{\"name\":\"CIK0000320193-submissions-001.json\",\"filingCount\":1256}]"
                : "[]";
        return "{\"cik\":\"320193\",\"name\":\"Apple Inc.\",\"filings\":{\"recent\":{" + recent + "},\"files\":" + files + "}}";
    }
}
