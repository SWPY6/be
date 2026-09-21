package com.swyp.ploutos.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    private final CorsConfiguration configuration = corsConfiguration();

    private static CorsConfiguration corsConfiguration() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/any");

        return new SecurityConfig().corsConfigurationSource().getCorsConfiguration(request);
    }

    @Test
    void 프론트엔드_운영_주소는_허용된다() {
        // given
        String origin = "https://ploutos-2vw.pages.dev";

        // when
        String allowed = configuration.checkOrigin(origin);

        // then
        assertThat(allowed).isEqualTo(origin);
    }

    @Test
    void 브랜치별_프리뷰_주소는_허용된다() {
        // given
        String origin = "https://feature-login.ploutos-2vw.pages.dev";

        // when
        String allowed = configuration.checkOrigin(origin);

        // then
        assertThat(allowed).isEqualTo(origin);
    }

    @Test
    void 프리뷰_도메인을_흉내낸_주소는_거부된다() {
        // given
        String origin = "https://ploutos-2vw.pages.dev.attacker.com";

        // when
        String allowed = configuration.checkOrigin(origin);

        // then
        assertThat(allowed).isNull();
    }

    @Test
    void 같은_도메인이라도_http면_거부된다() {
        // given
        String origin = "http://ploutos-2vw.pages.dev";

        // when
        String allowed = configuration.checkOrigin(origin);

        // then
        assertThat(allowed).isNull();
    }
}
