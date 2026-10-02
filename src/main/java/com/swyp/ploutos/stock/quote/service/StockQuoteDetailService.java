package com.swyp.ploutos.stock.quote.service;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 종목 정보·현재가·20거래일 대비 거래량 배수를 모은다. 종목을 먼저 확인해 없는 종목이면 시세를 조회하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StockQuoteDetailService {

    private final StockReader stockReader;
    private final QuoteReader quoteReader;
    private final DailyPriceReader dailyPriceReader;

    public StockQuoteDetail read(Long stockId) {
        StockWithMarket stock = stockReader.read(stockId);
        Quote quote = quoteReader.read(stockId);
        return new StockQuoteDetail(
                stock.stockId(),
                stock.ticker(),
                stock.name(),
                quote,
                dailyPriceReader.averageVolume20d(stockId).flatMap(quote::volumeRatioTo).orElse(null)
        );
    }
}
