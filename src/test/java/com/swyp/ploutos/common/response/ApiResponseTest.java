package com.swyp.ploutos.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void 데이터를_전달하면_응답에_담긴다() {
        // given
        String data = "삼성전자";

        // when
        ApiResponse<String> response = ApiResponse.of(data);

        // then
        assertEquals(data, response.data());
    }
}
