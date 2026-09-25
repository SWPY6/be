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
}
