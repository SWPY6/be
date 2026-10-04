package com.swyp.ploutos.external.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DartApiPropertiesTest {

    private static final String BASE_URL = "https://dart.test";
    private static final String API_KEY = "my-dart-api-key";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DartClientConfig.class);

    @Test
    void base_url이_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new DartApiProperties(" ", API_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.dart.base-url");
    }

    @Test
    void 인증키가_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new DartApiProperties(BASE_URL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.dart.api-key");
    }

    @Test
    void 설정을_문자열로_찍어도_인증키가_드러나지_않는다() {
        // given
        DartApiProperties properties = new DartApiProperties(BASE_URL, API_KEY);

        // when
        String printed = properties.toString();

        // then
        assertThat(printed).contains(BASE_URL).doesNotContain(API_KEY);
    }

    @Test
    void 인증키_없이_컨텍스트를_띄우면_실패한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues("ploutos.dart.base-url=" + BASE_URL);

        // when & then
        configured.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void DART_클라이언트는_기존_쿼리를_유지하고_모든_요청에_인증키를_붙인다() {
        // given
        DartApiProperties properties = new DartApiProperties(BASE_URL, API_KEY);
        RestClient.Builder builder = new DartClientConfig(properties).dartRestClient().mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/api/list.json")))
                .andExpect(queryParam("corp_code", "00126380"))
                .andExpect(queryParam(DartClientConfig.API_KEY_PARAM, API_KEY))
                .andRespond(withSuccess());

        // when
        builder.build().get()
                .uri(uri -> uri.path("/api/list.json").queryParam("corp_code", "00126380").build())
                .retrieve()
                .toBodilessEntity();

        // then
        server.verify();
    }
}
