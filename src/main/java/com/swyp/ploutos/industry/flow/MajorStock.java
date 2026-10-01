package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

/**
 * 산업 카드에 표시하는 대표 종목. 계산 시점의 값을 그대로 담는다 —
 * 조회할 때 다시 구하면 KIS를 호출해야 한다.
 *
 * @param stockId 관련 뉴스를 찾을 때 쓴다. 응답에는 나가지 않는다 —
 *                {@code MajorStockResponse}가 ticker·name·changeRate만 골라 담는다
 */
public record MajorStock(
        Long stockId,
        String ticker,
        String name,
        BigDecimal changeRate
) {
}
