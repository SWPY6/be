package com.swyp.ploutos.common.util;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * 서비스 조건 검사 유틸. 조건이 거짓이면 BusinessException을 던진다.
 *
 * @author Aleexender
 */
public final class Precondition {

    private Precondition() {
    }

    public static void validate(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }
}
