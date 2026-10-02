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
 * @param tradingValue 오늘 누적 금액과 20거래일 평균 금액. 견줄 수 있는 종목이 하나도 없으면
 *                     {@code null}이다 — 0으로 채우면 "계산 실패"가 "거래 없음"으로 위장한다.
 *                     비율이 아니라 금액을 담는 이유는 조회 시점에 시장 전체와 합쳐야 하기 때문이다
 */
public record IndustryFlowSnapshot(
        BigDecimal avgChangeRate,
        int stockCount,
        int risingCount,
        int fallingCount,
        IndustryTradingValue tradingValue,
        List<MajorStock> majorStocks
) {

    /** 한 산업이 담을 수 있는 대표 종목 수. {@link IndustryFlows}의 컬럼 수가 정하는 상한이다. */
    public static final int MAJOR_STOCK_LIMIT = 2;

    public static IndustryFlowSnapshot empty() {
        return new IndustryFlowSnapshot(BigDecimal.ZERO.setScale(2), 0, 0, 0, null, List.of());
    }

    /** 시세를 한 종목도 구하지 못했다. 이 경우 직전 값을 남겨야 하므로 저장하지 않는다. */
    public boolean hasNoStock() {
        return stockCount == 0;
    }
}
