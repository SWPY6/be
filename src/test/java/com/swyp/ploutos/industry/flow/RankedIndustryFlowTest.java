package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.IndustryCode;

/**
 * 평균 등락률은 반올림하지 않은 채 저장된다. 자르는 일은 응답을 만들 때 여기서 한다 —
 * 순위를 매길 때는 반올림 전 값이 필요하기 때문이다(RQ-0603).
 */
class RankedIndustryFlowTest {

    @Test
    void 표기용_평균_등락률은_소수_둘째_자리다() {
        // given 종목 수로 나눠 자리수가 길어진 값
        RankedIndustryFlow flow = flow("1.333333");

        // when
        BigDecimal display = flow.displayAvgChangeRate();

        // then
        assertThat(display).isEqualByComparingTo("1.33");
        assertThat(display.scale()).isEqualTo(2);
    }

    @Test
    void 셋째_자리에서_반올림한다() {
        // given
        RankedIndustryFlow flow = flow("1.615000");

        // when & then HALF_UP 이므로 올림이다
        assertThat(flow.displayAvgChangeRate()).isEqualByComparingTo("1.62");
    }

    @Test
    void 음수도_절댓값이_큰_쪽으로_반올림한다() {
        // given
        RankedIndustryFlow flow = flow("-1.615000");

        // when & then
        assertThat(flow.displayAvgChangeRate()).isEqualByComparingTo("-1.62");
    }

    @Test
    void 저장된_값은_자르지_않는다() {
        // given
        RankedIndustryFlow flow = flow("1.333333");

        // when & then 순위를 매길 때 쓰는 값은 그대로 남는다
        assertThat(flow.avgChangeRate()).isEqualByComparingTo("1.333333");
    }

    private static RankedIndustryFlow flow(String avgChangeRate) {
        return new RankedIndustryFlow(IndustryCode.AUTOMOBILE, 1, new BigDecimal(avgChangeRate),
                4, 3, 1, null, List.of(), null);
    }
}
