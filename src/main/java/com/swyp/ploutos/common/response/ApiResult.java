package com.swyp.ploutos.common.response;

public record ApiResult<T>(T data) {

    public static <T> ApiResult<T> of(T data) {
        return new ApiResult<>(data);
    }
}
