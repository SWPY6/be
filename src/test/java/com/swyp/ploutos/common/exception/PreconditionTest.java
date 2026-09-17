package com.swyp.ploutos.common.exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PreconditionTest {

    @Test
    void 조건이_참이면_예외가_발생하지_않는다() {
        // given
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertDoesNotThrow(() -> Precondition.require(true, errorCode));
    }

    @Test
    void 조건이_거짓이면_비즈니스예외가_발생한다() {
        // given
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Precondition.require(false, errorCode)
        );

        // then
        assertEquals(errorCode, exception.errorCode());
    }
}
