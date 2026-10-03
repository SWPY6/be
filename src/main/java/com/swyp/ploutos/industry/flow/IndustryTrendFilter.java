package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 산업별 동향 탭. 거르는 조건과 정렬 기준을 각자가 안다 — 서비스에 {@code switch}를 두지 않고,
 * 탭이 하나 늘면 값을 더하는 것으로 끝난다.
 *
 * <p><b>정렬을 서버가 하는 이유</b>는 응답의 {@code avgChangeRate}가 소수 둘째 자리로 반올림된
 * 값이라 클라이언트가 동률을 풀 수 없기 때문이다. 반올림 전 값과 거래대금을 가진 쪽이 정렬해야 한다.
 */
public enum IndustryTrendFilter {

    /** 전체. 등락률과 무관하게 산업명 가나다순으로 놓는다. */
    ALL(flow -> true, Comparator.comparing(RankedIndustryFlow::displayName)),

    /** 상승 산업. 많이 오른 순. */
    RISING(flow -> flow.avgChangeRate().signum() > 0,
            Comparator.comparing(RankedIndustryFlow::avgChangeRate, Comparator.reverseOrder())
                    .thenComparing(IndustryTrendFilter.tieBreak())),

    /** 하락 산업. 많이 내린 순 — 등락률만 방향이 뒤집힌다. */
    FALLING(flow -> flow.avgChangeRate().signum() < 0,
            Comparator.comparing(RankedIndustryFlow::avgChangeRate)
                    .thenComparing(IndustryTrendFilter.tieBreak()));

    private final Predicate<RankedIndustryFlow> includes;
    private final Comparator<RankedIndustryFlow> order;

    IndustryTrendFilter(Predicate<RankedIndustryFlow> includes,
            Comparator<RankedIndustryFlow> order) {
        this.includes = includes;
        this.order = order;
    }

    /** 이 탭에 속하는 산업만 골라 정렬한다. 보합(등락률 0)은 {@link #ALL}에만 들어간다. */
    public List<RankedIndustryFlow> apply(List<RankedIndustryFlow> flows) {
        return flows.stream()
                .filter(includes)
                .sorted(order)
                .toList();
    }

    /**
     * 등락률이 같을 때의 기준 (RQ-0603). 상승·하락 어느 쪽에서도 <b>방향이 같다</b> —
     * 하락 탭에서도 "거래가 활발한 쪽이 위", "이름은 가나다순"이 자연스럽다.
     * 뒤집히는 것은 "많이 내린 것이 위"를 만드는 등락률뿐이다.
     *
     * <p>거래대금을 측정하지 못한 산업은 뒤로 간다. 모르는 산업을 "활발했다"고 볼 수 없다.
     */
    private static Comparator<RankedIndustryFlow> tieBreak() {
        return Comparator
                .comparing(IndustryTrendFilter::tradingRatioOf,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedIndustryFlow::displayName);
    }

    private static BigDecimal tradingRatioOf(RankedIndustryFlow flow) {
        if (flow.tradingValue() == null) {
            return null;
        }
        return flow.tradingValue().ratio();
    }
}
