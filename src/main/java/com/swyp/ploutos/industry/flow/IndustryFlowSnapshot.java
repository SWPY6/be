package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 산업·국가의 계산 결과. 순위는 나머지 산업을 알아야 매길 수 있으므로 여기 없다.
 * {@link IndustryFlows}가 이 값을 받아 저장한다.
 *
 * @param risingCount  반영된 종목 중 오른 종목 수
 * @param fallingCount 반영된 종목 중 내린 종목 수. 보합은 어느 쪽에도 세지 않으므로
 *                     {@code stockCount - risingCount}로 역산할 수 없다
 */
public record IndustryFlowSnapshot(
        BigDecimal avgChangeRate,
        int stockCount,
        int risingCount,
        int fallingCount,
        List<MajorStock> majorStocks
) {

    public static IndustryFlowSnapshot empty() {
        return new IndustryFlowSnapshot(BigDecimal.ZERO.setScale(2), 0, 0, 0, List.of());
    }

    /** 시세를 한 종목도 구하지 못했다. 이 경우 직전 값을 남겨야 하므로 저장하지 않는다. */
    public boolean hasNoStock() {
        return stockCount == 0;
    }
}
