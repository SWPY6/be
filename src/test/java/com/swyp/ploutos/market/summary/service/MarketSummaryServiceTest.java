package com.swyp.ploutos.market.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.quote.IndicatorQuote;

class MarketSummaryServiceTest {

    private static final OffsetDateTime VALUE_AT = OffsetDateTime.parse("2026-09-30T10:15:03+09:00");

    private final List<MarketIndicator> calls = new ArrayList<>();
    private final Set<MarketIndicator> failing = EnumSet.noneOf(MarketIndicator.class);

    private MarketSummaryService service;

    @BeforeEach
    void setUp() {
        service = new MarketSummaryService(indicator -> {
            calls.add(indicator);
            if (failing.contains(indicator)) {
                throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
            }
            return quote(indicator);
        });
    }

    @Test
    void 국내_탭은_코스피_코스닥_환율_순서로_돌려준다() {
        // given
        MarketRegion region = MarketRegion.DOMESTIC;

        // when
        MarketSummary summary = service.read(region);

        // then
        assertThat(summary.region()).isEqualTo(region);
        assertThat(summary.quotes()).extracting(IndicatorQuote::indicator)
                .containsExactly(MarketIndicator.KOSPI, MarketIndicator.KOSDAQ, MarketIndicator.USD_KRW);
    }

    @Test
    void 해외_탭은_나스닥_SP500_환율_순서로_돌려준다() {
        // given
        MarketRegion region = MarketRegion.OVERSEAS;

        // when
        MarketSummary summary = service.read(region);

        // then
        assertThat(summary.region()).isEqualTo(region);
        assertThat(summary.quotes()).extracting(IndicatorQuote::indicator)
                .containsExactly(MarketIndicator.NASDAQ, MarketIndicator.SP500, MarketIndicator.USD_KRW);
    }

    @Test
    void 지표를_표시_순서대로_하나씩_읽는다() {
        // given 캐시가 비었을 때 KIS를 동시에 부르지 않아야 한다

        // when
        service.read(MarketRegion.DOMESTIC);

        // then
        assertThat(calls)
                .containsExactly(MarketIndicator.KOSPI, MarketIndicator.KOSDAQ, MarketIndicator.USD_KRW);
    }

    @Test
    void 한_지표라도_실패하면_예외를_던진다() {
        // given 두 번째 지표가 실패한다
        failing.add(MarketIndicator.KOSDAQ);

        // when & then 일부 카드만 내려보내지 않는다
        assertThatThrownBy(() -> service.read(MarketRegion.DOMESTIC))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        // 실패한 지점에서 멈추므로 뒤의 환율은 읽지 않는다
        assertThat(calls).containsExactly(MarketIndicator.KOSPI, MarketIndicator.KOSDAQ);
    }

    private static IndicatorQuote quote(MarketIndicator indicator) {
        BigDecimal value = new BigDecimal("6870.81");
        return new IndicatorQuote(indicator, value, new BigDecimal("6889.74"), value, value, value, VALUE_AT);
    }
}
