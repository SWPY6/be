package com.swyp.ploutos.external.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KisPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KisClientConfig.class);

    @Test
    void 앱키가_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new KisProperties("", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.kis.app-key");
    }

    @Test
    void 시크릿이_없으면_기동에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new KisProperties("key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ploutos.kis.app-secret");
    }

    @Test
    void 환경변수로_앱키와_시크릿을_바인딩한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "ploutos.kis.app-key=my-key",
                "ploutos.kis.app-secret=my-secret"
        );

        // when & then
        configured.run(context -> {
            KisProperties properties = context.getBean(KisProperties.class);
            assertThat(properties.appKey()).isEqualTo("my-key");
            assertThat(properties.appSecret()).isEqualTo("my-secret");
        });
    }

    @Test
    void 앱키_없이_컨텍스트를_띄우면_실패한다() {
        // given
        ApplicationContextRunner configured = runner.withPropertyValues(
                "ploutos.kis.app-secret=my-secret"
        );

        // when & then
        configured.run(context -> assertThat(context).hasFailed());
    }
}
