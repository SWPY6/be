package com.swyp.ploutos.stock.quote.service;

import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.Quote;

public interface QuoteProvider {

    Quote fetch(StockWithMarket stock);
}
