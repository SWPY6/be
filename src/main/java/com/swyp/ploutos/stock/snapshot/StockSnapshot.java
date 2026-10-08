package com.swyp.ploutos.stock.snapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 한 종목의 시세 스냅샷. 목록 화면이 정렬·필터에 쓰는 값만 담는다.
 *
 * <p>이력을 쌓지 않는다 — 화면이 보여주는 것은 "지금"이고 과거 값은 일봉이 담당한다.
 * 그래서 종목당 한 건이며 갱신될 때마다 덮어쓴다.
 *
 * @param calculatedAt 그 종목이 속한 시장의 현지 시각. 국가가 섞인 목록에서 어느 시점의
 *                     값인지 알려면 국가별 시각이어야 한다
 */
public record StockSnapshot(
        Long stockId,
        BigDecimal price,
        BigDecimal changeRate,
        long volume,
        BigDecimal tradingValue,
        BigDecimal marketCap,
        LocalDateTime calculatedAt
) {

}
