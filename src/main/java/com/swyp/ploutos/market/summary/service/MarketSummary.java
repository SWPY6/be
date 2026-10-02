package com.swyp.ploutos.market.summary.service;

import java.util.List;

import com.swyp.ploutos.market.MarketRegion;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/** 한 시장 탭의 지표 시세 묶음. 순서는 탭의 표시 순서다. */
public record MarketSummary(
        MarketRegion region,
        List<IndicatorQuote> quotes
) {
}
