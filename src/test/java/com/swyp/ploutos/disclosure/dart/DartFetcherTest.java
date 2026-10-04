package com.swyp.ploutos.disclosure.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class DartFetcherTest {

    private static final String BASE_URL = "https://dart.test";
    private static final String PATH = "/api/file";
    private static final byte[] BODY = {1, 2, 3, 4, 5};

    private MockRestServiceServer server;
    private DartFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new DartFetcher(builder.build(), new CountingBudget(Integer.MAX_VALUE));
    }

    @Test
    void 받은_파일이_상한_이하면_그대로_돌려준다() {
        // given
        server.expect(once(), requestTo(BASE_URL + PATH))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_OCTET_STREAM));

        // when
        byte[] bytes = fetcher.getBytes(PATH, "테스트", BODY.length);

        // then
        assertThat(bytes).isEqualTo(BODY);
    }

    @Test
    void 받은_파일이_상한을_넘으면_공급_실패다() {
        // given
        server.expect(once(), requestTo(BASE_URL + PATH))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_OCTET_STREAM));

        // when & then
        assertThatThrownBy(() -> fetcher.getBytes(PATH, "테스트", BODY.length - 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }
}
