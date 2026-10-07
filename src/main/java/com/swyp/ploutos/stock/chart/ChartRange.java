package com.swyp.ploutos.stock.chart;

import java.time.LocalDate;
import java.time.Period;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

/**
 * 차트가 읽을 조회 구간. 생략된 값을 채우고 스스로 검증한다.
 */
public record ChartRange(LocalDate from, LocalDate to) {

    /**
     * 구간 길이 상한. 저장된 일봉이 없으면 KIS를 100건씩 페이징해 채우므로,
     * 한 요청이 일으키는 외부 호출 수를 여기서 묶는다.
     */
    static final Period MAX_LENGTH = Period.ofYears(5);

    /**
     * 구간을 생략했을 때의 길이. 짧게 잡는 이유는 첫 조회의 KIS 페이징을 1회로 묶기 위해서다.
     * KIS 호출은 1건당 5초에서 끊기므로(`KisClientConfig.READ_TIMEOUT`), 호출이 늘수록 한 번은 걸릴 확률이 커진다.
     * 2개월이면 여유 30일을 더해도 거래일 약 62개라 100건짜리 한 페이지로 끝난다.
     */
    private static final Period DEFAULT_LENGTH = Period.ofMonths(2);

    /** 요청의 from·to. 생략하면 [오늘 − 2개월, 오늘]이고, 역전되거나 상한을 넘으면 400이다. */
    public static ChartRange of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minus(DEFAULT_LENGTH) : from;
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (start.isBefore(end.minus(MAX_LENGTH))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new ChartRange(start, end);
    }

    /** 그 날짜가 구간 안에 있는가. 양 끝을 포함한다. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
