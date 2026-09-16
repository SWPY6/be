package com.swyp.ploutos.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import com.swyp.ploutos.common.exception.CommonErrorCode;
import com.swyp.ploutos.common.exception.ErrorCode;

class ErrorResponseTest {

    @Test
    void 에러코드로_만들면_코드는_enum_이름이고_필드에러는_비어있다() {
        // given
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;

        // when
        ErrorResponse response = ErrorResponse.of(errorCode);

        // then
        assertThat(response.code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(response.message()).isEqualTo("잘못된 입력값입니다.");
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void 바인딩결과가_있으면_필드에러가_변환된다() {
        // given
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        // when
        ErrorResponse response = ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, bindingResult);

        // then
        assertThat(response.code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(response.errors())
                .extracting(ErrorResponse.FieldError::field, ErrorResponse.FieldError::reason)
                .containsExactly(tuple("name", "must not be blank"));
    }
}
