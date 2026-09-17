package com.swyp.ploutos.common.response;

import java.util.List;

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
    
	public record FieldError(
			
	        String field,
	        String reason
	) {
		
	}
	
}
