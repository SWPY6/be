package com.swyp.ploutos.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "잘못된 입력값입니다."
    ),

    STOCK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "종목을 찾을 수 없습니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    //getter
    public HttpStatus status() {
        return status;
    }

    //getter
    public String message() {
        return message;
    }
}
