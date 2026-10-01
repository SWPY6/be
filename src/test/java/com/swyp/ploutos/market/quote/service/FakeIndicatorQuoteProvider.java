package com.swyp.ploutos.market.quote.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/** 지표별로 고정 시세를 준다. 실패하도록 지정한 지표는 KIS 오류를 던진다. */
class FakeIndicatorQuoteProvider implements IndicatorQuoteProvider {

    static final OffsetDateTime VALUE_AT = OffsetDateTime.parse("2026-09-30T10:15:03+09:00");

    final List<MarketIndicator> calls = new ArrayList<>();
    final Set<MarketIndicator> failing = EnumSet.noneOf(MarketIndicator.class);

    @Override
    public IndicatorQuote fetch(MarketIndicator indicator) {
        calls.add(indicator);
        if (failing.contains(indicator)) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
        return quote(indicator, new BigDecimal("6870.81"));
    }

    static IndicatorQuote quote(MarketIndicator indicator, BigDecimal value) {
        return new IndicatorQuote(
                indicator,
                value,
                new BigDecimal("6889.74"),
                new BigDecimal("6844.41"),
                new BigDecimal("6898.36"),
                new BigDecimal("6782.99"),
                VALUE_AT
        );
    }
}
