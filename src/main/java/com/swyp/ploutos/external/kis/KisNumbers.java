package com.swyp.ploutos.external.kis;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * KIS는 숫자를 문자열로 주고, 개장 전이나 거래가 없는 종목에는 빈 값을 준다.
 * 빈 값은 0으로 읽어 응답 하나 때문에 요청 전체가 실패하지 않게 한다.
 * 다만 그 값 없이는 시세를 만들 수 없는 항목은 {@link #requiredAmount}로 읽어 실패를 드러낸다.
 */
public final class KisNumbers {

    private static final Logger log = LoggerFactory.getLogger(KisNumbers.class);

    private KisNumbers() {
    }

    /**
     * 비어 있으면 시세를 만들 수 없는 값. 0으로 읽으면 등락률이 조용히 0이 되거나
     * 값이 0인 지표가 화면에 나가므로, 시세 조회 실패로 알린다.
     */
    public static BigDecimal requiredAmount(String value, String field) {
        if (value == null || value.isBlank()) {
            log.error("KIS 응답에 필수 값이 비어 있습니다: {}", field);
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
        return new BigDecimal(value.trim());
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
