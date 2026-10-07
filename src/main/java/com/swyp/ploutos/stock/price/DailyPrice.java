package com.swyp.ploutos.stock.price;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPrice(
        LocalDate tradeAt,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {

    public boolean tradedOn(LocalDate date) {
        return tradeAt.equals(date);
    }

    public boolean tradedBetween(LocalDate from, LocalDate to) {
        return !tradeAt.isBefore(from) && !tradeAt.isAfter(to);
    }

    /**
     * 거래일을 빼고 시가·고가·저가·종가·거래량이 모두 같은가.
     * 금액은 {@code compareTo}로 견준다. 저장 정밀도(소수 넷째 자리)와 KIS 값의 자릿수가 달라도 같은 값이다.
     */
    public boolean sameValuesAs(DailyPrice other) {
        return open.compareTo(other.open) == 0
                && high.compareTo(other.high) == 0
                && low.compareTo(other.low) == 0
                && close.compareTo(other.close) == 0
                && volume == other.volume;
    }
}
