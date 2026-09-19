package com.swyp.ploutos.common.exception;

public final class Precondition {

    private Precondition() {
    }

    public static void require(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }
}
