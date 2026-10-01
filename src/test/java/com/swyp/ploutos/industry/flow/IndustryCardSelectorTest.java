package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;

class IndustryCardSelectorTest {

    private final IndustryCardSelector selector = new IndustryCardSelector();

    @Test
    void 상승군과_하락군에서_한_장씩_고른다() {
        // given 등락률 내림차순. 거래대금은 모두 평소 이상이다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", "52.65"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.45", "10.00"),
                flow(IndustryCode.CHEMICAL, 3, "-0.35", "96.03"));

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
    void 거래대금이_줄어든_산업은_등락률_1위여도_고르지_않는다() {
        // given 1위는 거래대금이 줄고, 3위만 늘었다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "-20.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "-5.00"),
                flow(IndustryCode.TRANSPORT, 3, "1.00", "30.00"),
                flow(IndustryCode.CHEMICAL, 4, "-1.00", "10.00"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then 순위는 다시 매기지 않는다. 3위가 뽑히면 rank 도 3이다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.TRANSPORT);
        assertThat(rising.flow().rank()).isEqualTo(3);
        assertThat(rising.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 조건을_통과한_산업이_여럿이면_등락률이_가장_큰_것을_고른다() {
        // given 셋 다 거래대금이 늘었다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "5.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "2.00", "90.00"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "10.00"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then 거래대금이 더 많이 늘어난 건설이 아니라 더 많이 오른 자동차다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
    }

    @Test
    void 조건을_통과한_산업이_없으면_등락률만으로_고른다() {
        // given 한산한 장이라 전 산업의 거래대금이 평소보다 적다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "-20.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "-30.00"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "-40.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 화면을 비우지 않는다. 대체했다는 사실만 알린다
        assertThat(cards.get(0).flow().code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards).allMatch(card -> card.selectedBy() == SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 하락_카드는_가장_많이_내린_산업부터_본다() {
        // given 하락 셋 중 최하위만 거래대금이 늘었다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "10.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "-1.00", "20.00"),
                flow(IndustryCode.CHEMICAL, 3, "-5.00", "30.00"));

        // when
        IndustryCard falling = selector.select(flows).get(1);

        // then 건설(-1.00)이 아니라 화학(-5.00)이다
        assertThat(falling.flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(falling.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 하락_카드도_거래대금이_줄면_차순위로_올라간다() {
        // given 최하위는 거래대금이 줄고, 그 위가 늘었다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "10.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "-1.00", "20.00"),
                flow(IndustryCode.CHEMICAL, 3, "-5.00", "-30.00"));

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
                flow(IndustryCode.AUTOMOBILE, 1, "-0.20", "10.00"),
                flow(IndustryCode.CHEMICAL, 2, "-4.10", "20.00"));

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
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "10.00"),
                flow(IndustryCode.CHEMICAL, 2, "0.05", "20.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 전체 최하위를 하락 카드로 쓴다
        assertThat(cards.get(1).flow().code()).isEqualTo(IndustryCode.CHEMICAL);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 한쪽만_조건을_통과하면_카드마다_선정_근거가_다르다() {
        // given 상승은 거래대금이 늘고, 하락은 줄었다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.61", "52.65"),
                flow(IndustryCode.CHEMICAL, 2, "-0.35", "-10.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then
        assertThat(cards.get(0).selectedBy()).isEqualTo(SelectedBy.MATCHED);
        assertThat(cards.get(1).selectedBy()).isEqualTo(SelectedBy.CHANGE_RATE_ONLY);
    }

    @Test
    void 거래대금을_구하지_못한_산업은_조건을_통과하지_못한다() {
        // given 1위는 변화율이 null(일봉 부족), 2위는 평소 이상
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", null),
                flow(IndustryCode.CONSTRUCTION, 2, "1.00", "30.00"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "10.00"));

        // when
        IndustryCard rising = selector.select(flows).getFirst();

        // then null 을 "조건 만족"으로 볼 수 없다
        assertThat(rising.flow().code()).isEqualTo(IndustryCode.CONSTRUCTION);
        assertThat(rising.selectedBy()).isEqualTo(SelectedBy.MATCHED);
    }

    @Test
    void 거래대금이_평소와_같으면_조건을_통과한다() {
        // given 변화율이 정확히 0.00 이다. "평소 이상"이므로 통과한다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "0.00"),
                flow(IndustryCode.CHEMICAL, 2, "-1.00", "0.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then
        assertThat(cards).allMatch(card -> card.selectedBy() == SelectedBy.MATCHED);
    }

    @Test
    void 보합인_산업은_상승군과_하락군_어디에도_들어가지_않는다() {
        // given 자동차만 오르고, 건설은 보합, 화학은 내렸다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "10.00"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.00", "99.00"),
                flow(IndustryCode.CHEMICAL, 3, "-1.00", "10.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 거래대금이 가장 많이 늘었어도 보합이면 뽑히지 않는다
        assertThat(cards).extracting(card -> card.flow().code())
                .containsExactly(IndustryCode.AUTOMOBILE, IndustryCode.CHEMICAL);
    }

    @Test
    void 같은_산업이_두_카드에_뽑히지_않는다() {
        // given 산업이 둘뿐이고 하나는 상승, 하나는 하락이다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "10.00"),
                flow(IndustryCode.CHEMICAL, 2, "-1.00", "10.00"));

        // when
        List<IndustryCard> cards = selector.select(flows);

        // then 상승군과 하락군은 부호로 배타적이다
        assertThat(cards.get(0).flow().code()).isNotEqualTo(cards.get(1).flow().code());
    }

    /** 선정에 쓰이는 값만 채운다. 종목 수·대표 종목·계산 시각은 이 규칙과 무관하다. */
    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate,
            String tradingValueChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                tradingValueChangeRate == null ? null : new BigDecimal(tradingValueChangeRate),
                List.of(), null);
    }
}
