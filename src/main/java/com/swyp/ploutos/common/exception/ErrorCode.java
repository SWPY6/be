package com.swyp.ploutos.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus status();

    String message();

    String name();
}
