package com.swyp.ploutos.stock.chart;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Arrays;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

import lombok.Getter;

/**
 * 봉 하나가 덮는 기간. 요청 문자열과의 변환과 버킷 경계 계산을 스스로 한다.
 */
@Getter
public enum ChartInterval {

    DAY("1D"),
    WEEK("1W"),
    MONTH("1M"),
    QUARTER("3M"),
    YEAR("1Y");

    private final String code;

    ChartInterval(String code) {
        this.code = code;
    }

    /** 요청의 interval 값. 없으면 일봉이고, 허용되지 않은 값이면 400이다. */
    public static ChartInterval from(String code) {
        if (code == null || code.isBlank()) {
            return DAY;
        }
        return Arrays.stream(values())
                .filter(interval -> interval.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 이 거래일이 속한 버킷의 시작일. 같은 버킷에 묶일 거래일은 같은 값을 낸다. */
    LocalDate bucketStart(LocalDate tradeAt) {
        return switch (this) {
            case DAY -> tradeAt;
            case WEEK -> tradeAt.with(DayOfWeek.MONDAY);
            case MONTH -> tradeAt.withDayOfMonth(1);
            case QUARTER -> tradeAt.with(IsoFields.DAY_OF_QUARTER, 1);
            case YEAR -> tradeAt.withDayOfYear(1);
        };
    }
}
