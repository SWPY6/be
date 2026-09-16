package com.swyp.ploutos.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void data를_넘기면_그대로_감싼_응답을_반환한다() {
        // given
        String data = "삼성전자";

        // when
        ApiResponse<String> response = ApiResponse.of(data);

        // then
        assertThat(response.data()).isEqualTo(data);
    }
}
