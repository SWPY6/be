package com.swyp.ploutos.market.quote.service;

import java.util.Optional;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/**
 * 지표 현재값 캐시. 캐시가 빈 지표의 첫 조회가 겹칠 때 외부 호출을 한 건으로 묶는 락도 함께 제공한다.
 */
public interface IndicatorQuoteCache {

    Optional<IndicatorQuote> find(MarketIndicator indicator);

    void put(MarketIndicator indicator, IndicatorQuote quote);

    boolean tryLock(MarketIndicator indicator);

    void unlock(MarketIndicator indicator);
}
