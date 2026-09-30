package com.swyp.ploutos.external.kis;

import java.math.BigDecimal;

/**
 * KIS는 숫자를 문자열로 주고, 개장 전이나 거래가 없는 종목에는 빈 값을 준다.
 * 빈 값은 0으로 읽어 응답 하나 때문에 요청 전체가 실패하지 않게 한다.
 */
public final class KisNumbers {

    private KisNumbers() {
    }

    public static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    /** 거래량처럼 정수로 쓰는 값. KIS가 소수점을 붙여 주는 경우가 있어 BigDecimal을 거친다. */
    public static long count(String value) {
        return amount(value).longValue();
    }
}
