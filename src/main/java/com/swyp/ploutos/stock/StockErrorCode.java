package com.swyp.ploutos.stock;

import org.springframework.http.HttpStatus;

import com.swyp.ploutos.common.exception.ErrorCode;

public enum StockErrorCode implements ErrorCode {

    NOT_FOUND_STOCK(
            HttpStatus.NOT_FOUND,
            "종목을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String message;

    StockErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
