package com.swyp.ploutos.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void 에러코드를_전달하면_비즈니스예외가_생성된다() {
        // given
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        // when
        BusinessException exception = new BusinessException(errorCode);

        // then
        assertEquals(errorCode, exception.errorCode());
        assertEquals(errorCode.message(), exception.getMessage());
    }
}

