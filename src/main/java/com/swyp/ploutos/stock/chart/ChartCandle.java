package com.swyp.ploutos.stock.chart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.swyp.ploutos.stock.price.DailyPrice;

/**
 * 차트의 봉 하나. 확정 봉과 진행 중 봉이 같은 형태이며 {@code closed}로만 구분한다.
 */
public record ChartCandle(
        LocalDate tradeAt,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        boolean closed
) {

    /** 한 버킷에 묶인 일봉들을 봉 하나로 접는다. 거래일 오름차순으로 들어온다. */
    static ChartCandle of(List<DailyPrice> bucket, boolean closed) {
        DailyPrice first = bucket.getFirst();
        DailyPrice last = bucket.getLast();
        return new ChartCandle(
                first.tradeAt(),
                first.open(),
                bucket.stream().map(DailyPrice::high).max(Comparator.naturalOrder()).orElseThrow(),
                bucket.stream().map(DailyPrice::low).min(Comparator.naturalOrder()).orElseThrow(),
                last.close(),
                bucket.stream().mapToLong(DailyPrice::volume).sum(),
                closed
        );
    }
}
