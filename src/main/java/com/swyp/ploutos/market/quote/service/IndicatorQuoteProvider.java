package com.swyp.ploutos.market.quote.service;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/** 외부 시세 제공자에서 지표의 현재값 스냅샷을 받아 온다. */
public interface IndicatorQuoteProvider {

    IndicatorQuote fetch(MarketIndicator indicator);
}
