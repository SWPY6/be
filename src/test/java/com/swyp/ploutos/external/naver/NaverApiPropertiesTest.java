package com.swyp.ploutos.external.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NaverClientConfig.class);

    private static final String BASE_URL = "https://naver.test";

    @Test
    void base_url이_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new NaverApiProperties(" ", "id", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.naver.base-url");
    }

    @Test
    void 클라이언트_ID가_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new NaverApiProperties(BASE_URL, "", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.naver.client-id");
    }

    @Test
    void 클라이언트_시크릿이_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new NaverApiProperties(BASE_URL, "id", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.naver.client-secret");
    }

    @Test
    void 설정을_문자열로_찍어도_인증값이_드러나지_않는다() {
        // given
        NaverApiProperties properties = new NaverApiProperties(BASE_URL, "my-client-id", "my-client-secret");

        // when
        String printed = properties.toString();

        // then
        assertThat(printed)
                .contains(BASE_URL)
                .doesNotContain("my-client-id")
                .doesNotContain("my-client-secret");
    }

    @Test
    void 환경변수로_주소와_인증값을_바인딩한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "ploutos.naver.base-url=" + BASE_URL,
                "ploutos.naver.client-id=my-client-id",
                "ploutos.naver.client-secret=my-client-secret"
        );

        // when & then
        configured.run(context -> {
            NaverApiProperties properties = context.getBean(NaverApiProperties.class);
            assertThat(properties.baseUrl()).isEqualTo(BASE_URL);
            assertThat(properties.clientId()).isEqualTo("my-client-id");
            assertThat(properties.clientSecret()).isEqualTo("my-client-secret");
        });
    }

    @Test
    void 시크릿_없이_컨텍스트를_띄우면_실패한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "ploutos.naver.base-url=" + BASE_URL,
                "ploutos.naver.client-id=my-client-id"
        );

        // when & then
        configured.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 네이버_클라이언트는_모든_요청에_API_HUB_인증_헤더를_붙인다() {
        // given
        NaverApiProperties properties = new NaverApiProperties(BASE_URL, "my-client-id", "my-client-secret");
        RestClient.Builder builder = new NaverClientConfig(properties).naverRestClient().mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/search/v1/news"))
                .andExpect(header(NaverClientConfig.CLIENT_ID_HEADER, "my-client-id"))
                .andExpect(header(NaverClientConfig.CLIENT_SECRET_HEADER, "my-client-secret"))
                .andRespond(withSuccess());

        // when
        builder.build().get().uri("/search/v1/news").retrieve().toBodilessEntity();

        // then
        server.verify();
    }
}
