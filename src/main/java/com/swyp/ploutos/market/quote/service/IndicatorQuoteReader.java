package com.swyp.ploutos.market.quote.service;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/**
 * 지표의 현재값을 읽는다. 캐시에 없으면 외부에서 받아 채운 뒤 돌려준다.
 * 호출자는 캐시와 외부 호출의 존재를 알 필요가 없다.
 */
public interface IndicatorQuoteReader {

    IndicatorQuote read(MarketIndicator indicator);
}
