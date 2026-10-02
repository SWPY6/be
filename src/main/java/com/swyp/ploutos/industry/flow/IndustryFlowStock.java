package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

/**
 * 산업 카드에 표시하는 종목. 계산 시점의 값을 그대로 담는다 —
 * 조회할 때 다시 구하면 KIS를 호출해야 하고, 등락률과 다른 시점의 현재가가 나란히 놓인다.
 *
 * @param stockId 관련 뉴스를 찾을 때 쓴다. 응답에는 나가지 않는다 — 환경마다 auto_increment
 *                값이 달라 외부 식별자로 쓸 수 없다
 * @param price   현재가. 시장 요약 카드는 쓰지 않고 산업별 동향 카드가 쓴다
 */
public record IndustryFlowStock(
        Long stockId,
        String ticker,
        String name,
        BigDecimal price,
        BigDecimal changeRate
) {
}
