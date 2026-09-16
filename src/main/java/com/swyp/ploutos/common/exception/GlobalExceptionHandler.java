package com.swyp.ploutos.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.swyp.ploutos.common.api.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.errorCode();

        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;

        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode, ex.getBindingResult()));
    }

    // 스프링이 직접 던지는 예외(없는 경로, 잘못된 JSON, 지원하지 않는 메서드 등)도 같은 포맷으로 맞춘다
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        String code = HttpStatus.valueOf(statusCode.value()).name();
        String message = body instanceof ProblemDetail problemDetail
                ? problemDetail.getDetail()
                : code;

        return ResponseEntity
                .status(statusCode)
                .headers(headers)
                .body(ErrorResponse.of(code, message));
    }
}
