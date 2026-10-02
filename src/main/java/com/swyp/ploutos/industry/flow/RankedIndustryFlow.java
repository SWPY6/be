package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 표기용 자리수. 저장된 값은 반올림돼 있지 않다. */
    private static final int DISPLAY_SCALE = 2;

    public String displayName() {
        return code.displayName();
    }

    /**
     * 화면에 내보낼 평균 등락률. 소수 둘째 자리로 반올림한다.
     *
     * <p>{@link #avgChangeRate()}는 반올림하지 않은 값이라 순위를 매길 때 쓰고, 응답에는
     * 이것을 싣는다. 자르는 규칙을 값 옆에 두어 DTO 마다 같은 코드를 쓰지 않게 한다.
     */
    public BigDecimal displayAvgChangeRate() {
        return avgChangeRate.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}
