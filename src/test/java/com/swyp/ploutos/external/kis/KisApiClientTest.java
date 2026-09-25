package com.swyp.ploutos.external.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.kis.auth.KisAccessTokenProvider;

class KisApiClientTest {

    private static final String BASE_URL = "https://kis.test";
    private static final String PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String TR_ID = "FHKST01010100";
    private static final String SUCCESS_BODY = """
            {"rt_cd": "0", "msg_cd": "MCA00000", "msg1": "정상처리 되었습니다.", "output": {"stck_prpr": "72000"}}
            """;
    private static final String TOKEN_EXPIRED_BODY = """
            {"rt_cd": "1", "msg_cd": "EGW00123", "msg1": "기간이 만료된 token 입니다."}
            """;

    private MockRestServiceServer server;
    private FakeTokenProvider tokenProvider;
    private KisApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        tokenProvider = new FakeTokenProvider();
        client = new RestClientKisApiClient(
                builder.build(),
                tokenProvider,
                new KisProperties(BASE_URL, "my-key", "my-secret")
        );
    }

    @Test
    void 요청에_공통_헤더를_붙인다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("FID_INPUT_ISCD", "005930"))
                .andExpect(header("content-type", "application/json; charset=utf-8"))
                .andExpect(header("authorization", "Bearer token-1"))
                .andExpect(header("appkey", "my-key"))
                .andExpect(header("appsecret", "my-secret"))
                .andExpect(header("tr_id", TR_ID))
                .andExpect(header("custtype", "P"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        // when
        PriceResponse response = client.get(PATH, TR_ID, Map.of("FID_INPUT_ISCD", "005930"), PriceResponse.class);

        // then
        assertThat(response.output().price()).isEqualTo("72000");
        server.verify();
    }

    @Test
    void 응답코드가_0이_아니면_시세조회_실패_예외를_던진다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess("""
                        {"rt_cd": "1", "msg_cd": "OPSQ0002", "msg1": "종목코드 오류"}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.get(PATH, TR_ID, Map.of(), PriceResponse.class))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        server.verify();
    }

    @Test
    void HTTP_오류이면_시세조회_실패_예외를_던진다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andRespond(withServerError().body("<html>gateway error</html>"));

        // when & then
        assertThatThrownBy(() -> client.get(PATH, TR_ID, Map.of(), PriceResponse.class))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        server.verify();
    }

    @Test
    void 토큰_만료_오류를_받으면_재발급_후_한_번_재시도한다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andExpect(header("authorization", "Bearer token-1"))
                .andRespond(withServerError().body(TOKEN_EXPIRED_BODY).contentType(MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andExpect(header("authorization", "Bearer token-2"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        // when
        PriceResponse response = client.get(PATH, TR_ID, Map.of(), PriceResponse.class);

        // then
        assertThat(response.output().price()).isEqualTo("72000");
        assertThat(tokenProvider.invalidatedCount).isEqualTo(1);
        server.verify();
    }

    @Test
    void 재시도도_실패하면_예외를_던진다() {
        // given
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andRespond(withServerError().body(TOKEN_EXPIRED_BODY).contentType(MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + PATH)))
                .andRespond(withServerError().body(TOKEN_EXPIRED_BODY).contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.get(PATH, TR_ID, Map.of(), PriceResponse.class))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(tokenProvider.invalidatedCount).isEqualTo(1);
        server.verify();
    }

    // 무효화될 때마다 다음 토큰을 내어 주는 가짜 제공자
    private static final class FakeTokenProvider implements KisAccessTokenProvider {

        private int invalidatedCount = 0;

        @Override
        public String accessToken() {
            return "token-" + (invalidatedCount + 1);
        }

        @Override
        public void invalidate() {
            invalidatedCount++;
        }
    }

    record PriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output") Output output
    ) implements KisResponse {

        record Output(
                @JsonProperty("stck_prpr") String price
        ) {
        }
    }
}
