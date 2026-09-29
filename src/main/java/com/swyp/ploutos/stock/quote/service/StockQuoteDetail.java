package com.swyp.ploutos.stock.quote.service;

import java.math.BigDecimal;

import com.swyp.ploutos.stock.quote.Quote;

/**
 * 종목 상세 화면에 필요한 값의 묶음. {@code volumeRatio20d}는 20거래일 평균을 낼 수 없으면 null이다.
 */
public record StockQuoteDetail(
        Long stockId,
        String ticker,
        String name,
        Quote quote,
        BigDecimal volumeRatio20d
) {
}
