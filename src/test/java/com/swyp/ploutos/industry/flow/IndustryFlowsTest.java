package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;

/**
 * 대표 종목 상한을 지키던 테스트가 여기 있었다. 종목이 자식 테이블
 * {@code industry_flow_stocks}로 옮겨가면서 "저장 자리 수"라는 개념이 사라졌고, 같은 자리에
 * 두 종목이 들어가는 것은 이제 DB의 유일 제약이 막는다
 * ({@code IndustryFlowStockRepositoryTest}).
 *
 * <p>이 엔티티에 남은 규칙은 거래대금 두 금액의 짝 맞춤과 덮어쓰기다.
 */
class IndustryFlowsTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 8, 12, 14, 31);

    @Test
    void 거래대금을_측정했으면_두_금액을_함께_돌려준다() {
        // given
        IndustryFlows flow = new IndustryFlows(1L, Country.KR,
                snapshot("1.23", new IndustryTradingValue(
                        new BigDecimal("600"), new BigDecimal("400"))),
                CALCULATED_AT);

        // when & then
        assertThat(flow.tradingValue()).isPresent();
        assertThat(flow.tradingValue().orElseThrow().ratio()).isEqualByComparingTo("1.5");
    }

    @Test
    void 거래대금을_측정하지_못했으면_비어_있다() {
        // given 일봉이 모자라 견줄 수 있는 종목이 하나도 없었다
        IndustryFlows flow = new IndustryFlows(1L, Country.KR, snapshot("1.23", null), CALCULATED_AT);

        // when & then 꺼내 쓰는 쪽이 null 두 개를 맞춰 보지 않게 한다
        assertThat(flow.tradingValue()).isEmpty();
    }

    @Test
    void 갱신하면_거래대금도_함께_비워진다() {
        // given 어제는 측정됐던 산업이다
        IndustryFlows flow = new IndustryFlows(1L, Country.KR,
                snapshot("1.23", new IndustryTradingValue(
                        new BigDecimal("600"), new BigDecimal("400"))),
                CALCULATED_AT);

        // when 오늘은 측정하지 못했다
        flow.refresh(snapshot("2.34", null), CALCULATED_AT.plusMinutes(1));

        // then 옛 값이 남아 "측정됐다"고 보이면 안 된다
        assertThat(flow.tradingValue()).isEmpty();
        assertThat(flow.avgChangeRate()).isEqualByComparingTo("2.34");
    }

    @Test
    void 평균_등락률을_반올림하지_않고_담는다() {
        // given 종목 수로 나눠 자리수가 길어진 값. 순위 동률을 풀려면 그대로 있어야 한다
        IndustryFlows flow = new IndustryFlows(1L, Country.KR,
                snapshot("1.333333", null), CALCULATED_AT);

        // when & then
        assertThat(flow.avgChangeRate()).isEqualByComparingTo("1.333333");
    }

    private static IndustryFlowSnapshot snapshot(String avgChangeRate,
            IndustryTradingValue tradingValue) {
        return new IndustryFlowSnapshot(new BigDecimal(avgChangeRate), 4, 3, 1, tradingValue,
                List.of());
    }
}
