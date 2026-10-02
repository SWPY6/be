package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.IndustryCode;

/**
 * 탭마다 거르는 조건과 정렬이 다르다. 특히 하락 탭은 <b>등락률만</b> 방향이 뒤집히고
 * 거래대금·산업명은 상승 탭과 같은 방향이다 — 순서를 통째로 뒤집으면 안 된다.
 */
class IndustryTrendFilterTest {

    @Test
    void 전체는_산업명_가나다순이다() {
        // given 등락률 순서와 가나다순이 다르다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 2, "1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 3, "-1.00", "1.0"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.ALL.apply(flows);

        // then
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "자동차", "화학");
    }

    @Test
    void 전체는_아홉_개를_모두_돌려준다() {
        // given 보합인 산업도 들어 있다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 2, "0.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 3, "-1.00", "1.0"));

        // when & then
        assertThat(IndustryTrendFilter.ALL.apply(flows)).hasSize(3);
    }

    @Test
    void 상승은_오른_산업만_많이_오른_순으로_돌려준다() {
        // given
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 2, "3.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 3, "-1.00", "1.0"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.RISING.apply(flows);

        // then
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("화학", "자동차");
    }

    @Test
    void 하락은_내린_산업만_많이_내린_순으로_돌려준다() {
        // given
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 2, "-1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 3, "-5.00", "1.0"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.FALLING.apply(flows);

        // then 가장 많이 내린 건설이 먼저다
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "화학");
    }

    @Test
    void 보합인_산업은_상승과_하락_어디에도_없다() {
        // given 등락률이 정확히 0 이다
        List<RankedIndustryFlow> flows = List.of(flow(IndustryCode.AUTOMOBILE, 1, "0.00", "1.0"));

        // when & then 오른 것도 내린 것도 아니다
        assertThat(IndustryTrendFilter.RISING.apply(flows)).isEmpty();
        assertThat(IndustryTrendFilter.FALLING.apply(flows)).isEmpty();
        assertThat(IndustryTrendFilter.ALL.apply(flows)).hasSize(1);
    }

    @Test
    void 전_산업이_오른_날_하락_탭은_빈_목록이다() {
        // given
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 2, "0.05", "1.0"));

        // when & then 프론트가 "해당 산업이 없습니다"를 표시해야 한다
        assertThat(IndustryTrendFilter.FALLING.apply(flows)).isEmpty();
    }

    @Test
    void 하락_탭에서도_거래가_활발한_산업이_위다() {
        // given 둘 다 -1.00 이고 거래대금만 다르다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "-1.00", "0.50"),
                flow(IndustryCode.CHEMICAL, 2, "-1.00", "2.50"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.FALLING.apply(flows);

        // then 순서를 통째로 뒤집었다면 한산한 자동차가 위로 왔을 것이다
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("화학", "자동차");
    }

    @Test
    void 하락_탭에서도_산업명은_가나다순이다() {
        // given 등락률도 거래대금도 같다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.CHEMICAL, 1, "-1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 2, "-1.00", "1.0"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.FALLING.apply(flows);

        // then 뒤집었다면 화학이 먼저였을 것이다
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "화학");
    }

    @Test
    void 거래대금을_측정하지_못한_산업은_뒤로_간다() {
        // given 등락률이 같고 한쪽만 측정됐다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.00", null),
                flow(IndustryCode.CHEMICAL, 2, "1.00", "0.10"));

        // when & then 거래가 한산한 산업보다도 뒤다
        assertThat(IndustryTrendFilter.RISING.apply(flows))
                .extracting(RankedIndustryFlow::displayName)
                .containsExactly("화학", "자동차");
    }

    @Test
    void 상승_탭의_순서는_rank_오름차순과_같다() {
        // given 동률 규칙이 IndustryFlowService 의 순위 부여와 같아야 한다.
        // 둘이 어긋나면 화면의 "N위"와 카드 순서가 따로 논다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 3, "1.000000", "0.50"),
                flow(IndustryCode.CHEMICAL, 1, "1.000000", "2.50"),
                flow(IndustryCode.CONSTRUCTION, 2, "1.000000", "1.50"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.RISING.apply(flows);

        // then
        assertThat(result).extracting(RankedIndustryFlow::rank).containsExactly(1, 2, 3);
        assertThat(result).isSortedAccordingTo(Comparator.comparingInt(RankedIndustryFlow::rank));
    }

    @Test
    void 거른_뒤에도_rank_는_그대로다() {
        // given 9개 중 하락만 뽑는다
        List<RankedIndustryFlow> flows = List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "3.00", "1.0"),
                flow(IndustryCode.CHEMICAL, 8, "-1.00", "1.0"),
                flow(IndustryCode.CONSTRUCTION, 9, "-5.00", "1.0"));

        // when
        List<RankedIndustryFlow> result = IndustryTrendFilter.FALLING.apply(flows);

        // then 배열 인덱스로 순위를 세면 안 된다
        assertThat(result).extracting(RankedIndustryFlow::rank).containsExactly(9, 8);
    }

    /** @param tradingRatio 오늘 거래대금 ÷ 20거래일 평균. {@code null}이면 측정하지 못한 산업이다 */
    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate,
            String tradingRatio) {
        IndustryTradingValue tradingValue = tradingRatio == null
                ? null
                : new IndustryTradingValue(
                        new BigDecimal(tradingRatio).multiply(new BigDecimal("100")),
                        new BigDecimal("100"));
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                tradingValue, List.of(), null);
    }
}
