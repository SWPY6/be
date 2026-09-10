package com.swyp.ploutos.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void 비즈니스예외가_발생하면_에러코드에_맞는_응답을_반환한다() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException exception =
                new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        // when
        ResponseEntity<String> response =
                handler.handleBusinessException(exception);

        // then
        assertEquals(400, response.getStatusCode().value());
        assertEquals("잘못된 입력값입니다.", response.getBody());
    }
}
