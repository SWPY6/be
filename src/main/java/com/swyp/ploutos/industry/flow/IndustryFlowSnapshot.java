package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 산업·국가의 계산 결과. 순위는 나머지 산업을 알아야 매길 수 있으므로 여기 없다.
 * {@link IndustryFlows}가 이 값을 받아 저장한다.
 */
public record IndustryFlowSnapshot(
        BigDecimal avgChangeRate,
        int stockCount,
        List<MajorStock> majorStocks
) {

    /** 한 산업이 담을 수 있는 대표 종목 수. {@link IndustryFlows}의 컬럼 수가 정하는 상한이다. */
    public static final int MAJOR_STOCK_LIMIT = 2;

    public static IndustryFlowSnapshot empty() {
        return new IndustryFlowSnapshot(BigDecimal.ZERO.setScale(2), 0, List.of());
    }

    /** 시세를 한 종목도 구하지 못했다. 이 경우 직전 값을 남겨야 하므로 저장하지 않는다. */
    public boolean hasNoStock() {
        return stockCount == 0;
    }
}
