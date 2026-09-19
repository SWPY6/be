package com.swyp.ploutos.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PostmanErrorProfileTest {

    @Test
    void 프로필이_없으면_테스트컨트롤러를_등록하지_않는다() {
        // given
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("default");
            context.register(PostmanErrorController.class);
            // when
            context.refresh();
            // then
            assertThat(context.getBeansOfType(PostmanErrorController.class)).isEmpty();
        }
    }
}
