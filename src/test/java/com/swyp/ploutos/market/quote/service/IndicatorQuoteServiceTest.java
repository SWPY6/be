package com.swyp.ploutos.market.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

class IndicatorQuoteServiceTest {

    private static final MarketIndicator INDICATOR = MarketIndicator.KOSPI;

    private FakeIndicatorQuoteCache cache;
    private FakeIndicatorQuoteProvider provider;
    private IndicatorQuoteService service;

    @BeforeEach
    void setUp() {
        cache = new FakeIndicatorQuoteCache();
        provider = new FakeIndicatorQuoteProvider();
        service = new IndicatorQuoteService(cache, provider);
    }

    @Test
    void 캐시가_있으면_외부를_호출하지_않고_같은_값을_반환한다() {
        // given
        IndicatorQuote cached = FakeIndicatorQuoteProvider.quote(INDICATOR, new BigDecimal("6900.00"));
        cache.values.put(INDICATOR, cached);

        // when
        IndicatorQuote quote = service.read(INDICATOR);

        // then
        assertThat(quote).isEqualTo(cached);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 캐시_미스_시_락을_잡은_요청만_외부를_호출하고_저장한다() {
        // given 캐시가 비어 있고 락도 비어 있다

        // when
        IndicatorQuote quote = service.read(INDICATOR);

        // then 외부를 한 번 호출해 저장하고 락을 푼다
        assertThat(provider.calls).containsExactly(INDICATOR);
        assertThat(cache.values).containsEntry(INDICATOR, quote);
        assertThat(cache.unlocked).containsExactly(INDICATOR);
    }

    @Test
    void 락을_못_잡은_요청은_캐시가_채워지면_그_값을_반환한다() {
        // given 다른 요청이 락을 잡고 있다가, 세 번째 조회 때 값을 채운다
        IndicatorQuote filled = FakeIndicatorQuoteProvider.quote(INDICATOR, new BigDecimal("6871.00"));
        cache.fillAfter(3, INDICATOR, filled);

        // when
        IndicatorQuote quote = service.read(INDICATOR);

        // then
        assertThat(quote).isEqualTo(filled);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 락_대기가_끝나도_캐시가_비어_있으면_외부를_호출하지_않고_예외를_던진다() {
        // given 다른 요청이 락을 잡고 끝내 값을 채우지 않는다
        cache.locked.add(INDICATOR);

        // when & then
        assertThatThrownBy(() -> service.read(INDICATOR))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(provider.calls).isEmpty();
        assertThat(cache.finds).isEqualTo(1 + IndicatorQuoteService.MAX_POLLS);
    }

    @Test
    void 캐시_저장소_장애면_외부를_호출하지_않고_예외를_던진다() {
        // given
        cache.broken = true;

        // when & then 캐시 없이 KIS를 부르면 장애 중 요청이 모두 KIS로 몰린다
        assertThatThrownBy(() -> service.read(INDICATOR))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 외부_호출이_실패하면_예외를_던지고_락을_해제한다() {
        // given
        provider.failing.add(INDICATOR);

        // when & then 만료된 캐시로 폴백하지 않는다
        assertThatThrownBy(() -> service.read(INDICATOR)).isInstanceOf(BusinessException.class);
        assertThat(cache.values).doesNotContainKey(INDICATOR);
        assertThat(cache.unlocked).containsExactly(INDICATOR);
    }

    @Test
    void 지표마다_따로_캐시된다() {
        // given 코스피만 캐시에 있다
        IndicatorQuote cached = FakeIndicatorQuoteProvider.quote(INDICATOR, new BigDecimal("6900.00"));
        cache.values.put(INDICATOR, cached);

        // when
        service.read(INDICATOR);
        service.read(MarketIndicator.KOSDAQ);

        // then 코스닥만 외부를 호출한다
        assertThat(provider.calls).containsExactly(MarketIndicator.KOSDAQ);
        assertThat(cache.values).containsOnlyKeys(INDICATOR, MarketIndicator.KOSDAQ);
    }
}
