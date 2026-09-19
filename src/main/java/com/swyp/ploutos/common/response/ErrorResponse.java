package com.swyp.ploutos.common.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.validation.BindingResult;

import com.swyp.ploutos.common.exception.ErrorCode;

public record ErrorResponse(ErrorDetail error) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(new ErrorDetail(
                errorCode.errorName(),
                errorCode.code(),
                errorCode.message(),
                List.of()
        ));
    }
    
    public static ErrorResponse of(
            ErrorCode errorCode,
            BindingResult bindingResult
    ) {
        List<FieldError> errors = bindingResult
                .getFieldErrors()
                .stream()
                .map(error -> new FieldError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return new ErrorResponse(new ErrorDetail(
                errorCode.errorName(),
                errorCode.code(),
                errorCode.message(),
                errors
        ));
    }

    public record ErrorDetail(
            @Schema(description = "오류 이름", example = "StockNotFoundException") String name,
            @Schema(description = "필수 오류 코드", example = "P002",
                    requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(description = "오류 메시지", example = "주식을 찾을 수 없습니다.") String message,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> errors
    ) {
    }
    
	public record FieldError(
			
	        String field,
	        String reason
	        
	) {
		
	}
	
}
