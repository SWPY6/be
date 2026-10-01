package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;

/**
 * 오늘의 핵심 뉴스 카드 2장을 고른다. 상승 1건·하락 1건이며 어떤 입력에서도 2장이 나온다.
 *
 * <p>세 신호를 하나의 점수로 합치지 않고 차례로 걸러 낸다. 등락률 순위는 순서값, 거래대금
 * 변화율은 연속값, 뉴스는 불리언이라 한 축에 놓으려면 가중치를 임의로 정해야 하고 그 가중치에는
 * 근거를 댈 수 없다. RQ-0402가 사용자에게 선정 기준을 설명해 주는 기능이므로, 조건으로 두어야
 * 설명할 수 있다.
 *
 * <p>뉴스는 선정에 관여하지 않는다. 요구사항이 "관련 뉴스가 없다면 생략이 가능함"이라고
 * 명시하므로 표시 항목이다 — 덕분에 선정된 2개 산업의 뉴스만 조회하면 된다.
 *
 * <p>DB도 시각도 모른다. 산업 목록만 받아 고르므로 순수 단위 테스트로 규칙을 전부 검증한다.
 */
@Component
public class IndustryCardSelector {

    /**
     * 평균 등락률 내림차순으로 정렬된 산업 목록에서 카드 2장을 고른다.
     * 결과는 언제나 {@code [RISING, FALLING]} 순서다.
     */
    public List<IndustryCard> select(List<RankedIndustryFlow> flows) {
        return List.of(
                rising(flows),
                falling(flows));
    }

    /**
     * 상승 카드. 오른 산업 중 거래대금이 평소 이상인 첫 산업을 고른다 — 목록이 등락률
     * 내림차순이라 "앞에서부터 처음"이 곧 "가장 많이 오른 것"이다.
     */
    private static IndustryCard rising(List<RankedIndustryFlow> flows) {
        List<RankedIndustryFlow> rose = filter(flows, flow -> flow.avgChangeRate().signum() > 0);
        return pick(rose, flows, Direction.RISING);
    }

    /** 하락 카드. 내린 산업을 뒤에서부터 훑는다 — 가장 많이 내린 것이 뒤에 있다. */
    private static IndustryCard falling(List<RankedIndustryFlow> flows) {
        List<RankedIndustryFlow> fell = filter(flows, flow -> flow.avgChangeRate().signum() < 0)
                .reversed();
        return pick(fell, flows.reversed(), Direction.FALLING);
    }

    /**
     * 후보를 앞에서부터 훑어 거래대금 관문을 통과한 첫 산업을 고른다.
     *
     * <p>통과한 산업이 없으면 조건을 풀고 등락률만으로 고른다. 한산한 장에서는 전 산업의
     * 거래대금이 평소보다 적어 관문을 아무도 통과하지 못하는데, 그때 화면을 비우지 않는다.
     * 군 자체가 비어 있으면(전 산업이 한쪽으로 쏠린 날) 전체에서 고른다.
     */
    private static IndustryCard pick(List<RankedIndustryFlow> candidates,
            List<RankedIndustryFlow> fallback, Direction direction) {
        return candidates.stream()
                .filter(IndustryCardSelector::tradedMoreThanUsual)
                .findFirst()
                .map(flow -> new IndustryCard(flow, direction, SelectedBy.MATCHED))
                .orElseGet(() -> new IndustryCard(
                        candidates.isEmpty() ? fallback.getFirst() : candidates.getFirst(),
                        direction, SelectedBy.CHANGE_RATE_ONLY));
    }

    /**
     * 거래대금이 그 산업의 20거래일 평균 이상인지. 구하지 못했으면({@code null}) 통과하지 못한다 —
     * 평소 이상인지 확인할 수 없는 산업을 "조건을 만족했다"고 말할 수 없다.
     */
    private static boolean tradedMoreThanUsual(RankedIndustryFlow flow) {
        BigDecimal changeRate = flow.tradingValueChangeRate();
        return changeRate != null && changeRate.signum() >= 0;
    }

    /** 보합(등락률 0.00)인 산업은 상승군·하락군 어디에도 들어가지 않는다. */
    private static List<RankedIndustryFlow> filter(List<RankedIndustryFlow> flows,
            Predicate<RankedIndustryFlow> moved) {
        return flows.stream().filter(moved).toList();
    }
}
