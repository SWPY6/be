package com.swyp.ploutos.common.response;

import java.util.List;

import org.springframework.validation.BindingResult;

import com.swyp.ploutos.common.exception.ErrorCode;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.message(),
                List.of()
        );
    }
    
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(
                code,
                message,
                List.of()
        );
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

        return new ErrorResponse(
                errorCode.name(),
                errorCode.message(),
                errors
        );
    }
    
	public record FieldError(
			
	        String field,
	        String reason
	        
	) {
		
	}
	
}
