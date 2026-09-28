package com.swyp.ploutos.stock.quote.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

/** 종목 코드별로 고정 시세를 준다. 실패하도록 지정한 종목 코드는 KIS 오류를 던진다. */
class FakeQuoteProvider implements QuoteProvider {

    static final OffsetDateTime PRICE_AT = OffsetDateTime.parse("2026-08-12T14:31:05+09:00");

    final List<String> calls = new ArrayList<>();
    final Set<String> failing = new HashSet<>();

    @Override
    public Quote fetch(StockWithMarket stock) {
        calls.add(stock.ticker());
        if (failing.contains(stock.ticker())) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
        return quote(new BigDecimal("248000"));
    }

    static Quote quote(BigDecimal price) {
        return new Quote(
                price,
                new BigDecimal("240217"),
                new BigDecimal("244280"),
                new BigDecimal("251224"),
                new BigDecimal("241056"),
                245_000,
                new BigDecimal("60800000000"),
                new BigDecimal("86600000000000"),
                Currency.KRW,
                PRICE_AT,
                PriceTiming.REALTIME
        );
    }
}
