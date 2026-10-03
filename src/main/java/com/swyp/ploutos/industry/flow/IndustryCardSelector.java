package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;

/**
 * 오늘의 핵심 뉴스 카드 2장을 고른다. 상승 1건·하락 1건이며 어떤 입력에서도 2장이 나온다.
 *
 * <p>세 신호를 하나의 점수로 합치지 않고 차례로 걸러 낸다. 등락률 순위는 순서값, 거래대금은
 * 연속값, 뉴스는 불리언이라 한 축에 놓으려면 가중치를 임의로 정해야 하고 그 가중치에는
 * 근거를 댈 수 없다. RQ-0402가 사용자에게 선정 기준을 설명해 주는 기능이므로, 조건으로 두어야
 * 설명할 수 있다.
 *
 * <p>거래대금 관문은 <b>같은 시각의 시장 전체</b>와 견준다. 당일 누적 금액에는 "하루 중 얼마나
 * 지났는가"가 곱해져 있어 20거래일 하루 전체 평균과 직접 비교하면 장중 내내 미달로 나온다 —
 * 2026-10-02 12:02 실측에서 자동차가 자기 평균의 0.49배였는데, 그 시각의 경과율이 0.47이었다.
 * 그 경과율은 모든 산업에 똑같이 걸리므로 시장 전체로 나누면 약분되어 사라진다.
 *
 * <p>뉴스는 선정에 관여하지 않는다. 요구사항이 "관련 뉴스가 없다면 생략이 가능함"이라고
 * 명시하므로 표시 항목이다 — 덕분에 선정된 2개 산업의 뉴스만 조회하면 된다.
 *
 * <p>DB도 시각도 모른다. 산업 목록만 받아 고르므로 순수 단위 테스트로 규칙을 전부 검증한다.
 */
@Component
public class IndustryCardSelector {

    /** 시장과 같은 속도. 이보다 크면 평소보다 활발하다. */
    private static final BigDecimal MARKET_PACE = BigDecimal.ONE;

    /**
     * 평균 등락률 내림차순으로 정렬된 산업 목록에서 카드 2장을 고른다.
     * 결과는 언제나 {@code [RISING, FALLING]} 순서다.
     */
    public List<IndustryCard> select(List<RankedIndustryFlow> flows) {
        Optional<BigDecimal> market = marketRatio(flows);
        return List.of(
                rising(flows, market),
                falling(flows, market));
    }

    /**
     * 같은 시각 시장 전체의 비율. 측정된 산업의 금액을 모두 합쳐 구한다.
     *
     * <p>측정된 산업이 너무 적으면 비어 있다. 그때는 거래대금 관문을 적용하지 않는다 —
     * 한 산업만 있으면 그 산업이 곧 시장이 되어 자기 자신과 비교하게 된다.
     */
    private static Optional<BigDecimal> marketRatio(List<RankedIndustryFlow> flows) {
        return IndustryTradingValue.marketRatio(flows.stream()
                .map(RankedIndustryFlow::tradingValue)
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * 상승 카드. 오른 산업 중 시장보다 활발한 첫 산업을 고른다 — 목록이 등락률
     * 내림차순이라 "앞에서부터 처음"이 곧 "가장 많이 오른 것"이다.
     */
    private static IndustryCard rising(List<RankedIndustryFlow> flows, Optional<BigDecimal> market) {
        List<RankedIndustryFlow> rose = filter(flows, flow -> flow.avgChangeRate().signum() > 0);
        return pick(rose, flows, Direction.RISING, market);
    }

    /** 하락 카드. 내린 산업을 뒤에서부터 훑는다 — 가장 많이 내린 것이 뒤에 있다. */
    private static IndustryCard falling(List<RankedIndustryFlow> flows, Optional<BigDecimal> market) {
        List<RankedIndustryFlow> fell = filter(flows, flow -> flow.avgChangeRate().signum() < 0)
                .reversed();
        return pick(fell, flows.reversed(), Direction.FALLING, market);
    }

    /**
     * 후보를 앞에서부터 훑어 거래대금 관문을 통과한 첫 산업을 고른다.
     *
     * <p>통과한 산업이 없으면 조건을 풀고 등락률만으로 고른다. 거래대금을 아무도 측정하지
     * 못한 날에도 화면을 비우지 않는다. 군 자체가 비어 있으면(전 산업이 한쪽으로 쏠린 날)
     * 전체에서 고른다.
     */
    private static IndustryCard pick(List<RankedIndustryFlow> candidates,
            List<RankedIndustryFlow> fallback, Direction direction, Optional<BigDecimal> market) {
        return candidates.stream()
                .filter(flow -> tradedMoreThanMarket(flow, market))
                .findFirst()
                .map(flow -> new IndustryCard(flow, direction, SelectedBy.MATCHED))
                .orElseGet(() -> new IndustryCard(
                        candidates.isEmpty() ? fallback.getFirst() : candidates.getFirst(),
                        direction, SelectedBy.CHANGE_RATE_ONLY));
    }

    /**
     * 시장보다 활발한 산업인지. 거래대금을 구하지 못했거나 시장 기준을 세우지 못했으면
     * 통과하지 못한다 — 확인할 수 없는 산업을 "조건을 만족했다"고 말할 수 없다.
     */
    private static boolean tradedMoreThanMarket(RankedIndustryFlow flow, Optional<BigDecimal> market) {
        if (flow.tradingValue() == null) {
            return false;
        }
        return market
                .map(ratio -> flow.tradingValue().relativeTo(ratio).compareTo(MARKET_PACE) >= 0)
                .orElse(false);
    }

    /** 보합(등락률 0.00)인 산업은 상승군·하락군 어디에도 들어가지 않는다. */
    private static List<RankedIndustryFlow> filter(List<RankedIndustryFlow> flows,
            Predicate<RankedIndustryFlow> moved) {
        return flows.stream().filter(moved).toList();
    }
}
