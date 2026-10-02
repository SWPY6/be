package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.IndustryTrendFilter;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;

@ExtendWith(MockitoExtension.class)
class IndustryTrendServiceTest {

    @Mock
    private IndustryFlowService industryFlowService;

    @InjectMocks
    private IndustryTrendService industryTrendService;

    @Test
    void 전체는_아홉_개를_산업명_가나다순으로_돌려준다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());

        // when
        List<RankedIndustryFlow> result = industryTrendService.read(Country.KR, IndustryTrendFilter.ALL);

        // then
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "자동차", "화학");
    }

    @Test
    void 상승은_오른_산업만_돌려준다() {
        // given 화학만 내렸다
        given(industryFlowService.read(Country.KR)).willReturn(flows());

        // when
        List<RankedIndustryFlow> result =
                industryTrendService.read(Country.KR, IndustryTrendFilter.RISING);

        // then
        assertThat(result).extracting(RankedIndustryFlow::displayName)
                .containsExactly("자동차", "건설");
    }

    @Test
    void 하락은_내린_산업만_돌려준다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());

        // when
        List<RankedIndustryFlow> result =
                industryTrendService.read(Country.KR, IndustryTrendFilter.FALLING);

        // then
        assertThat(result).extracting(RankedIndustryFlow::displayName).containsExactly("화학");
    }

    @Test
    void 필터가_달라도_같은_산업의_rank_는_같다() {
        // given
        given(industryFlowService.read(Country.KR)).willReturn(flows());

        // when
        int inAll = rankOf(industryTrendService.read(Country.KR, IndustryTrendFilter.ALL));
        int inRising = rankOf(industryTrendService.read(Country.KR, IndustryTrendFilter.RISING));

        // then rank 는 9개 전체 기준이라 거르는 것과 무관하다
        assertThat(inAll).isEqualTo(inRising);
    }

    @Test
    void 평균_등락률과_순위를_다시_계산하지_않는다() {
        // given 저장된 값을 그대로 쓴다. 다시 계산하면 시장 요약과 순위가 어긋난다
        given(industryFlowService.read(Country.US)).willReturn(flows());

        // when
        List<RankedIndustryFlow> result = industryTrendService.read(Country.US, IndustryTrendFilter.ALL);

        // then
        assertThat(result).extracting(RankedIndustryFlow::avgChangeRate)
                .containsExactlyInAnyOrder(new BigDecimal("1.610000"), new BigDecimal("0.450000"),
                        new BigDecimal("-0.350000"));
    }

    @Test
    void 국내는_원화_해외는_달러다() {
        // when & then 금액의 표시 단위는 나라가 정한다
        assertThat(Country.KR.currency()).isEqualTo(Currency.KRW);
        assertThat(Country.US.currency()).isEqualTo(Currency.USD);
    }

    /** 자동차 1위 · 건설 2위 · 화학 9위. 거래대금은 모두 같아 동률 기준이 끼어들지 않는다. */
    private static List<RankedIndustryFlow> flows() {
        return List.of(
                flow(IndustryCode.AUTOMOBILE, 1, "1.610000"),
                flow(IndustryCode.CONSTRUCTION, 2, "0.450000"),
                flow(IndustryCode.CHEMICAL, 9, "-0.350000"));
    }

    private static int rankOf(List<RankedIndustryFlow> flows) {
        return flows.stream()
                .filter(flow -> flow.code() == IndustryCode.AUTOMOBILE)
                .findFirst()
                .orElseThrow()
                .rank();
    }

    private static RankedIndustryFlow flow(IndustryCode code, int rank, String avgChangeRate) {
        return new RankedIndustryFlow(code, rank, new BigDecimal(avgChangeRate), 4, 3, 1,
                new IndustryTradingValue(new BigDecimal("120"), new BigDecimal("100")),
                List.of(), null);
    }
}
