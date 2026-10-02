package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;

/**
 * 거래대금 관문은 <b>같은 시각의 시장 전체</b>와 견준다. 아래 비율은 산업의 자기 20일 평균 대비
 * 배수이고, 시장 기준은 측정된 산업들의 금액을 합쳐 구하므로 모든 비율이 같으면 상대비율이
 * 정확히 {@code 1.000}이 되어 전부 통과한다.
 */
class IndustryCardSelectorTest {

    /** 모든 산업의 20일 평균을 같게 두어 비율만으로 시장 기준을 읽을 수 있게 한다. */
    private static final BigDecimal BASE = new BigDecimal("100");

    private final IndustryCardSelector selector = new IndustryCardSelector();

    @Test
    void 상승군과_하락군에서_한_장씩_고른다() {
        // given 등락률 내림차순. 셋 다 시장과 같은 속도다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.45", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "-0.35", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).direction()).isEqualTo(Direction.RISING);
        assertThat(cards.get(1).direction()).isEqualTo(Direction.FALLING);
        assertThat(cards.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards).allMatch(card -> card.selectedBy() == SelectedBy.MATCHED);
    }

    @Test
    void 시장보다_한산한_산업은_등락률_1위여도_고르지_않는다() {
        // given 시장 기준은 1.125 배다. 3위만 그보다 활발하다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "0.8"),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "0.9"),
                flow(IndustryCode.TRANSPORT, 3, "1.00", "1.6"),
                flow(IndustryCode.CHEMICAL, 4, "-1.00", "1.2"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then 순위는 다시 매기지 않는다. 3위가 뽑히면 rank 도 3이다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.TRANSPORT);
        assertThat(rising.flow().rank()).isEqualTo(3);
        assertThat(rising.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 조건을_통과한_산업이_여럿이면_등락률이_가장_큰_것을_고른다() {
        // given 시장 기준은 1.2 배다. 자동차와 건설이 모두 그보다 활발하다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.5"),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "1.6"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "0.5"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then 거래가 더 활발한 건설이 아니라 더 많이 오른 자동차다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
    }

    @Test
    void 상승군이_모두_시장보다_한산하면_등락률만으로_고른다() {
        // given 돈이 하락 산업으로 쏠린 날이다. 시장 기준 1.367 배를 상승군이 넘지 못한다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "0.5"),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "0.6"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "3.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 화면을 비우지 않는다. 대체했다는 사실만 알린다
        assertThat(cards.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(cards.get(0).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 하락_카드는_가장_많이_내린_산업부터_본다() {
        // given 셋 다 시장과 같은 속도다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "-1.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "-5.00", "1.0"));

        // when
        IndustryCard falling = selector.select(flows).get(1);

        // then 건설(-1.00)이 아니라 화학(-5.00)이다
        assertThat(falling.flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(falling.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 하락_카드도_시장보다_한산하면_차순위로_올라간다() {
        // given 시장 기준은 1.0 배다. 최하위 화학만 그보다 한산하다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.2"),
                flow(IndustryCode.CONSTRUCTION, 2, "-1.00", "1.5"),
                flow(IndustryCode.CHEMICAL, 3, "-5.00", "0.3"));

        // when
        IndustryCard falling = selector.select(flows).get(1);

        // then
        assertThat(falling.flow().code()).isEqualTo(IndustryCode.CONSTRUCTION);
        assertThat(falling.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 전_산업이_하락한_날에도_상승_카드가_나온다() {
        // given 오른 산업이 하나도 없다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "-0.20", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "-2.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "-4.10", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 전체 1위를 상승 카드로 쓴다. 부호가 음수이므로 대체로 표시한다
        assertThat(cards.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(cards.get(0).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 전_산업이_상승한_날에도_하락_카드가_나온다() {
        // given 내린 산업이 하나도 없다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "0.05", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 전체 최하위를 하락 카드로 쓴다
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 한쪽만_조건을_통과하면_카드마다_선정_근거가_다르다() {
        // given 시장 기준은 1.0 배다. 상승은 그보다 활발하고 하락은 한산하다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", "1.5"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.50", "1.3"),
                flow(IndustryCode.CHEMICAL, 3, "-0.35", "0.2"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then
        assertThat(cards.get(0).selectedBy()).isEqualTo(SelectedBy.MATCHED);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 거래대금을_구하지_못한_산업은_조건을_통과하지_못한다() {
        // given 1위는 일봉이 모자라 측정되지 않았다. 나머지 셋은 시장과 같은 속도다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", null),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "1.0"),
                flow(IndustryCode.TRANSPORT, 3, "0.50", "1.0"),
                flow(IndustryCode.CHEMICAL, 4, "-1.00", "1.0"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then 측정하지 못한 산업을 "조건을 만족했다"고 말할 수 없다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.CONSTRUCTION);
        assertThat(rising.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 시장과_같은_속도면_조건을_통과한다() {
        // given 상대비율이 정확히 1.000 이다. "시장 이상"이므로 통과한다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then
        assertThat(cards).allMatch(card -> card.selectedBy() == SelectedBy.MATCHED);
    }

    @Test
    void 측정된_산업이_셋보다_적으면_거래대금을_보지_않는다() {
        // given 일봉이 드물어 두 산업만 측정됐다. 둘 다 평소의 5배로 거래 중이다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "5.0"),
                flow(IndustryCode.CHEMICAL, 2, "-1.00", "5.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 둘뿐이면 한 산업이 곧 시장이 되어 자기 자신과 비교하게 된다
        assertThat(cards).allMatch(card -> card.selectedBy() == SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 장중_어느_시각에_보든_같은_산업이_뽑힌다() {
        // given 같은 날 같은 데이터인데, 장이 46.8% 지난 시점이라 오늘 금액이 그만큼만 쌓였다
        BigDecimal elapsed = new BigDecimal("0.468");
        List<RankedIndustryFlow> atClose = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "0.8"),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "0.9"),
                flow(IndustryCode.TRANSPORT, 3, "1.00", "1.6"),
                flow(IndustryCode.CHEMICAL, 4, "-1.00", "1.2"));
        List<RankedIndustryFlow> midSession = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "0.8", elapsed),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "0.9", elapsed),
                flow(IndustryCode.TRANSPORT, 3, "1.00", "1.6", elapsed),
                flow(IndustryCode.CHEMICAL, 4, "-1.00", "1.2", elapsed));

        // when
        List<IndustryCard> closed = selector.select(atClose);
        List<IndustryCard> during = selector.select(midSession);

        // then 경과율이 시장 기준에서 약분되므로 장중에도 같은 답이 나온다.
        // 자기 평균과 직접 견주면 장중에는 전 산업이 미달이라 둘 다 대체 선정이 됐을 것이다
        assertThat(during).extracting(card -> card.flow().code())
                .isEqualTo(closed.stream().map(card -> card.flow().code()).toList());
        assertThat(during).extracting(IndustryCard::selectedBy)
                .isEqualTo(closed.stream().map(IndustryCard::selectedBy).toList());
        assertThat(during.getFirst().selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 보합인_산업은_상승군과_하락군_어디에도_들어가지_않는다() {
        // given 자동차만 오르고, 건설은 보합, 화학은 내렸다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.00", "3.0"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 거래가 가장 활발해도 보합이면 뽑히지 않는다
        assertThat(cards).extracting(card -> card.flow().code())
                .containsExactly(IndustryCode.AUTOMOBILE, IndustryCode.CHEMICAL);
    }

    @Test
    void 같은_산업이_두_카드에_뽑히지_않는다() {
        // given
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.50", "1.0"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "1.0"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 상승군과 하락군은 부호로 배타적이다
        assertThat(cards.get(0).flow().code()).isNotEqualTo(cards.get(1).flow().code());
    }

    /** 선정에 쓰이는 값만 채운다. 종목 수·대표 종목·계산 시각은 이 규칙과 무관하다. */
    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate,
            String ratio) {
        return flow(code, rank, avgChangeRate, ratio, BigDecimal.ONE);
    }

    /**
     * @param ratio   자기 20거래일 평균 대비 배수. {@code null}이면 측정되지 않은 산업이다
     * @param elapsed 장이 얼마나 지났는지. 오늘 금액에만 곱한다 — 실제 장중 관측값의 모양이다
     */
    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate,
            String ratio, BigDecimal elapsed) {
        IndustryTradingValue tradingValue = ratio == null
                ? null
                : new IndustryTradingValue(new BigDecimal(ratio).multiply(BASE).multiply(elapsed), BASE);
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                tradingValue, List.of(), null);
    }
}
