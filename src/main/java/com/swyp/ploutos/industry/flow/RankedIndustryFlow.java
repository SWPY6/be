package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.swyp.ploutos.common.enums.IndustryCode;

/**
 * 순위가 붙은 산업 흐름. 저장된 스냅샷({@link IndustryFlows})에 산업 코드와 조회 시점에 매긴
 * 순위를 더한 읽기 전용 표현이다. 순위와 산업 코드를 저장하지 않으므로 이 타입이 필요하다.
 *
 * @param risingCount  반영된 종목 중 오른 종목 수
 * @param fallingCount 반영된 종목 중 내린 종목 수. 보합은 어느 쪽에도 세지 않는다
 * @param tradingValueChangeRate 20거래일 평균 대비 거래대금 변화율 %. 계산할 수 없으면 {@code null}
 * @param calculatedAt 시장 현지 시각. 아직 한 번도 계산되지 않은 산업은 {@code null}이다
 */
public record RankedIndustryFlow(
        IndustryCode code,
        int rank,
        BigDecimal avgChangeRate,
        int stockCount,
        int risingCount,
        int fallingCount,
        BigDecimal tradingValueChangeRate,
        List<MajorStock> majorStocks,
        OffsetDateTime calculatedAt
) {

    public String displayName() {
        return code.displayName();
    }
}
