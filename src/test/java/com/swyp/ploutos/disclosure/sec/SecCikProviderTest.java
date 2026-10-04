package com.swyp.ploutos.disclosure.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.sec.SecApiProperties;

import tools.jackson.databind.json.JsonMapper;

class SecCikProviderTest {

    private static final String WWW_URL = "https://www.sec.test";
    private static final String FILE_URL = WWW_URL + SecCikProvider.PATH;

    // 2026-10-02 실제 파일의 형식(열 이름 배열 + 값 배열)을 줄인 것
    private static final String TICKERS = """
            {"fields":["cik","name","ticker","exchange"],"data":[
              [320193,"Apple Inc.","AAPL","Nasdaq"],
              [1067983,"BERKSHIRE HATHAWAY INC","BRK-B","NYSE"],
              [1234567,"Some OTC Co","SOTC","OTC"],
              [7654321,"No Exchange Co","NOEX",null]
            ]}
            """;

    private MockRestServiceServer server;
    private CountingBudget budget;
    private SecCikProvider provider;

    @BeforeEach
    void setUp() {
        budget = new CountingBudget(Integer.MAX_VALUE);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SecFetcher fetcher = new SecFetcher(builder.build(), budget);
        provider = new SecCikProvider(
                fetcher, new SecApiProperties("Ploutos dev@example.com", "https://data.sec.test", WWW_URL),
                JsonMapper.builder().build()
        );
    }

    @Test
    void Nasdaq과_NYSE만_거래소_티커_키와_10자리_CIK로_읽는다() {
        // given
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        // when
        Map<String, String> codes = provider.fetchAll();

        // then
        assertThat(codes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "NASDAQ:AAPL", "0000320193",
                "NYSE:BRK-B", "0001067983"
        ));
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 같은_거래소_티커에_CIK가_둘_이상이면_매핑하지_않는다() {
        // given
        String duplicated = TICKERS.replace("[1234567,", "[999,\"Other\",\"AAPL\",\"Nasdaq\"],[1234567,");
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess(duplicated, MediaType.APPLICATION_JSON));

        // when
        Map<String, String> codes = provider.fetchAll();

        // then
        assertThat(codes).doesNotContainKey("NASDAQ:AAPL").containsKey("NYSE:BRK-B");
    }

    @Test
    void 필요한_열이_없으면_공급_실패다() {
        // given
        server.expect(once(), requestTo(FILE_URL))
                .andRespond(withSuccess("{\"fields\":[\"cik\",\"name\"],\"data\":[]}", MediaType.APPLICATION_JSON));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 쓸_수_있는_행이_없으면_공급_실패다() {
        // given
        server.expect(once(), requestTo(FILE_URL)).andRespond(withSuccess(
                "{\"fields\":[\"cik\",\"name\",\"ticker\",\"exchange\"],\"data\":[[1,\"A\",\"A\",\"OTC\"]]}",
                MediaType.APPLICATION_JSON));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 접근이_거부되면_재시도하지_않고_공급_실패다() {
        // given
        server.expect(once(), requestTo(FILE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
        assertThat(budget.consumed).isEqualTo(1);
    }

    @Test
    void 호출이_제한되면_한도_초과다() {
        // given
        server.expect(once(), requestTo(FILE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertError(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
    }

    @Test
    void 서버_오류는_한_번_재시도한다() {
        // given
        server.expect(times(2), requestTo(FILE_URL)).andRespond(withServerError());

        // when & then
        assertError(ErrorCode.DISCLOSURE_UNAVAILABLE);
        server.verify();
        assertThat(budget.consumed).isEqualTo(2);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(provider::fetchAll)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }
}
