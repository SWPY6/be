package com.swyp.ploutos.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.errorCode();

        return ResponseEntity
                .status(errorCode.status())
                .body(errorCode.message());
    }
}
