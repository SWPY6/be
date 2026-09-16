package com.swyp.ploutos.common.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.CommonErrorCode;

class PreconditionTest {

    @Test
    void 조건이_거짓이면_비즈니스예외가_발생한다() {
        // given
        boolean condition = false;
        CommonErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatThrownBy(() -> Precondition.validate(condition, errorCode))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(errorCode);
    }

    @Test
    void 조건이_참이면_아무_일도_일어나지_않는다() {
        // given
        boolean condition = true;
        CommonErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatCode(() -> Precondition.validate(condition, errorCode))
                .doesNotThrowAnyException();
    }
}
