package com.swyp.ploutos.industry.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.flow.IndustryFlowSnapshot;
import com.swyp.ploutos.industry.flow.IndustryFlows;
import com.swyp.ploutos.industry.flow.IndustryTradingValue;
import com.swyp.ploutos.industry.flow.IndustryFlowStock;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowRepository;
import com.swyp.ploutos.industry.flow.repository.IndustryFlowStockRepository;
import com.swyp.ploutos.industry.service.IndustryReader;

@ExtendWith(MockitoExtension.class)
class IndustryFlowServiceTest {

    private static final java.math.BigDecimal PRICE = new java.math.BigDecimal("248000");

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 9, 28, 10, 0, 0);
    private static final int SEOUL_OFFSET_SECONDS = 9 * 3600;

    @Mock
    private IndustryReader industryReader;

    @Mock
    private IndustryFlowRepository industryFlowRepository;

    @Mock
    private IndustryFlowStockRepository industryFlowStockRepository;

    @InjectMocks
    private IndustryFlowService industryFlowService;

    @Test
    void 평균_등락률이_높은_순으로_순위를_매긴다() {
        // given 가나다순으로 받은 목록의 등락률은 자동차 > 건설 > 화학 이다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(2L, "0.45"),
                flow(3L, "-0.35"),
                flow(1L, "1.61")));

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 가나다순을 버리고 등락률 순으로 다시 놓는다
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("자동차", "건설", "화학");
        assertThat(flows).extracting(RankedIndustryFlow::rank).containsExactly(1, 2, 3);
    }

    @Test
    void 등락률이_동점이면_산업명_가나다순으로_순위를_매긴다() {
        // given 셋 다 평균 1.00 이라 등락률만으로는 순위가 정해지지 않는다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(1L, "1.00"), flow(2L, "1.00"), flow(3L, "1.00")));

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 건설 1위 · 자동차 2위 · 화학 3위
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "자동차", "화학");
        assertThat(flows).extracting(RankedIndustryFlow::rank).containsExactly(1, 2, 3);
    }

    @Test
    void 저장된_값이_없어도_모든_산업을_응답한다() {
        // given
        given(industryReader.readAll()).willReturn(List.of(
                new Industries(1L, IndustryCode.AUTOMOBILE),
                new Industries(2L, IndustryCode.CONSTRUCTION)));
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of());

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then
        assertThat(flows).hasSize(2);
        assertThat(flows).allSatisfy(flow -> {
            assertThat(flow.avgChangeRate()).isEqualByComparingTo("0.00");
            assertThat(flow.stockCount()).isZero();
            assertThat(flow.stocks()).isEmpty();
        });
    }

    @Test
    void 계산되지_않은_산업은_계산_시각이_없다() {
        // given 가나다순으로 건설 · 자동차 이고, 자동차만 계산돼 있다
        given(industryReader.readAll()).willReturn(List.of(
                new Industries(2L, IndustryCode.CONSTRUCTION),
                new Industries(1L, IndustryCode.AUTOMOBILE)));
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(flow(1L, "1.61")));

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 계산된 자동차가 1.61 로 앞서고, 계산 안 된 건설은 0.00 으로 뒤에 온다
        assertThat(flows.get(0).code()).isEqualTo(IndustryCode.AUTOMOBILE);
        assertThat(flows.get(0).calculatedAt()).isNotNull();
        assertThat(flows.get(0).calculatedAt().getOffset().getTotalSeconds()).isEqualTo(SEOUL_OFFSET_SECONDS);
        assertThat(flows.get(1).code()).isEqualTo(IndustryCode.CONSTRUCTION);
        assertThat(flows.get(1).calculatedAt()).isNull();
    }

    /** {@code IndustryReader.readAll()} 은 한글 표시명 가나다순을 보장한다. 그 계약을 흉내 낸다. */
    private static List<Industries> 가나다순() {
        return List.of(
                new Industries(2L, IndustryCode.CONSTRUCTION),
                new Industries(1L, IndustryCode.AUTOMOBILE),
                new Industries(3L, IndustryCode.CHEMICAL));
    }

    @Test
    void 둘째_자리가_같으면_반올림_전_등락률로_순위를_매긴다() {
        // given 셋 다 표기하면 1.33 이지만 반올림 전 값이 다르다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(1L, "1.330000"),     // 자동차
                flow(2L, "1.334000"),     // 건설   ← 가장 크다
                flow(3L, "1.332000")));   // 화학

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 가나다순(건설·자동차·화학)이 아니라 반올림 전 값 순이다
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "화학", "자동차");
        assertThat(flows).extracting(RankedIndustryFlow::displayAvgChangeRate)
                .allSatisfy(rate -> assertThat(rate).isEqualByComparingTo("1.33"));
    }

    @Test
    void 반올림_전까지_같으면_거래대금이_활발한_산업이_앞선다() {
        // given 등락률이 완전히 같고 거래대금만 다르다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(1L, "1.000000", "0.80"),     // 자동차 — 한산
                flow(2L, "1.000000", "1.10"),     // 건설
                flow(3L, "1.000000", "2.50")));   // 화학   ← 가장 활발

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("화학", "건설", "자동차");
    }

    @Test
    void 거래대금을_측정하지_못한_산업은_뒤로_간다() {
        // given 일봉이 없어 거래대금을 구하지 못한 산업이 있다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(1L, "1.000000", null),       // 자동차 — 측정 못 함
                flow(2L, "1.000000", "0.50"),     // 건설   — 한산하지만 측정됨
                flow(3L, "1.000000", "0.60")));   // 화학

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 거래가 한산한 산업보다도 뒤다 — 모르는 것을 "활발했다"고 볼 수 없다
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("화학", "건설", "자동차");
    }

    @Test
    void 등락률과_거래대금이_모두_같으면_산업명_가나다순이다() {
        // given 앞의 두 기준으로 갈리지 않는다
        given(industryReader.readAll()).willReturn(가나다순());
        given(industryFlowRepository.findByCountry(Country.KR)).willReturn(List.of(
                flow(1L, "1.000000", "1.20"),
                flow(2L, "1.000000", "1.20"),
                flow(3L, "1.000000", "1.20")));

        // when
        List<RankedIndustryFlow> flows = industryFlowService.read(Country.KR);

        // then 순위가 매 요청 흔들리지 않게 마지막 기준이 있다
        assertThat(flows).extracting(RankedIndustryFlow::displayName)
                .containsExactly("건설", "자동차", "화학");
    }

    private static IndustryFlows flow(Long industryId, String avgChangeRate) {
        return flow(industryId, avgChangeRate, "1.20");
    }

    /** @param tradingRatio 오늘 거래대금 ÷ 20거래일 평균. {@code null}이면 측정하지 못한 산업이다 */
    private static IndustryFlows flow(Long industryId, String avgChangeRate, String tradingRatio) {
        IndustryTradingValue tradingValue = tradingRatio == null
                ? null
                : new IndustryTradingValue(
                        new BigDecimal(tradingRatio).multiply(new BigDecimal("100")),
                        new BigDecimal("100"));
        return new IndustryFlows(industryId, Country.KR,
                new IndustryFlowSnapshot(new BigDecimal(avgChangeRate), 4, 3, 1, tradingValue,
                        List.of(new IndustryFlowStock(10L, "005380", "현대차", PRICE, new BigDecimal("3.24")))),
                CALCULATED_AT);
    }

}
