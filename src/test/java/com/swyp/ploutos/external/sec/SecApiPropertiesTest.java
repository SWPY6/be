package com.swyp.ploutos.external.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SecApiPropertiesTest {

    private static final String USER_AGENT = "Ploutos dev@example.com";
    private static final String DATA_URL = "https://data.sec.test";
    private static final String WWW_URL = "https://www.sec.test";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecClientConfig.class);

    @Test
    void User_Agent가_없거나_연락_이메일이_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new SecApiProperties(" ", DATA_URL, WWW_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.sec.user-agent");
        assertThatThrownBy(() -> new SecApiProperties("Ploutos", DATA_URL, WWW_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.sec.user-agent");
    }

    @Test
    void 주소가_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new SecApiProperties(USER_AGENT, "", WWW_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.sec.data-base-url");
        assertThatThrownBy(() -> new SecApiProperties(USER_AGENT, DATA_URL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.sec.www-base-url");
    }

    @Test
    void User_Agent_없이_컨텍스트를_띄우면_실패한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "ploutos.sec.data-base-url=" + DATA_URL,
                "ploutos.sec.www-base-url=" + WWW_URL
        );

        // when & then
        configured.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void SEC_클라이언트는_모든_요청에_User_Agent를_보낸다() {
        // given
        SecApiProperties properties = new SecApiProperties(USER_AGENT, DATA_URL, WWW_URL);
        RestClient.Builder builder = new SecClientConfig(properties).secRestClient().mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DATA_URL + "/submissions/CIK0000320193.json"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess());

        // when
        builder.build().get().uri(DATA_URL + "/submissions/CIK0000320193.json").retrieve().toBodilessEntity();

        // then
        server.verify();
    }
}
