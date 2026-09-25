package com.swyp.ploutos.external.kis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.kis.KisProperties;

class KisAccessTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String BASE_URL = "https://kis.test";
    private static final String TOKEN_URL = BASE_URL + CachedKisAccessTokenProvider.TOKEN_PATH;
    private static final String TOKEN_BODY = """
            {"access_token": "token-1", "token_type": "Bearer", "expires_in": 86400}
            """;

    private MockRestServiceServer server;
    private MutableClock clock;
    private CachedKisAccessTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(NOW);
        provider = new CachedKisAccessTokenProvider(
                builder.build(),
                new KisProperties(BASE_URL, "my-key", "my-secret"),
                clock
        );
    }

    @Test
    void 앱키와_시크릿으로_토큰을_발급받는다() {
        // given
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("my-key"))
                .andExpect(jsonPath("$.appsecret").value("my-secret"))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));

        // when
        String token = provider.accessToken();

        // then
        assertThat(token).isEqualTo("token-1");
        server.verify();
    }

    @Test
    void 토큰이_유효하면_재발급하지_않는다() {
        // given
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        provider.accessToken();
        clock.advance(Duration.ofHours(23));

        // when
        String token = provider.accessToken();

        // then
        assertThat(token).isEqualTo("token-1");
        server.verify();
    }

    @Test
    void 만료_5분_전이면_재발급한다() {
        // given
        server.expect(times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        provider.accessToken();
        clock.advance(Duration.ofHours(24).minusMinutes(5));

        // when
        provider.accessToken();

        // then
        server.verify();
    }

    @Test
    void 무효화하면_다음_요청에서_재발급한다() {
        // given
        server.expect(times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        provider.accessToken();

        // when
        provider.invalidate();
        provider.accessToken();

        // then
        server.verify();
    }

    @Test
    void 동시에_만료를_감지해도_발급은_한_번이다() throws Exception {
        // given
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        int threads = 10;
        List<Callable<String>> calls = Collections.nCopies(threads, provider::accessToken);

        // when
        List<String> tokens;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> futures = executor.invokeAll(calls);
            tokens = futures.stream().map(KisAccessTokenProviderTest::get).toList();
        }

        // then
        assertThat(tokens).hasSize(threads).allMatch("token-1"::equals);
        server.verify();
    }

    @Test
    void 발급에_실패하면_시세조회_실패_예외를_던진다() {
        // given
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> provider.accessToken())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    @Test
    void 응답에_토큰이_없으면_시세조회_실패_예외를_던진다() {
        // given
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> provider.accessToken())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private static String get(Future<String> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // 테스트가 시각을 앞으로 옮길 수 있는 시계
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
