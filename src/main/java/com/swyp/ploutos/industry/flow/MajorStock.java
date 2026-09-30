package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

/**
 * 산업 카드에 표시하는 대표 종목. 계산 시점의 값을 그대로 담는다 —
 * 조회할 때 다시 구하면 KIS를 호출해야 한다.
 */
public record MajorStock(
        String ticker,
        String name,
        BigDecimal changeRate
) {
}
