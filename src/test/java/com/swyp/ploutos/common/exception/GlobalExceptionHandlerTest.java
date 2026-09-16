package com.swyp.ploutos.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.swyp.ploutos.common.api.ErrorResponse;

class GlobalExceptionHandlerTest {

    @Test
    void 비즈니스예외가_발생하면_에러코드에_맞는_응답을_반환한다() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException exception =
                new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);

        // when
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(exception);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody())
                .extracting(ErrorResponse::code, ErrorResponse::message)
                .containsExactly("INVALID_INPUT_VALUE", "잘못된 입력값입니다.");
    }

    @Test
    void 처리되지_않은_예외가_발생하면_500_응답을_반환한다() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException exception = new RuntimeException("boom");

        // when
        ResponseEntity<ErrorResponse> response = handler.handleException(exception);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
