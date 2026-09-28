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
}
