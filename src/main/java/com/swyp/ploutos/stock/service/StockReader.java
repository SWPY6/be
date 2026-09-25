package com.swyp.ploutos.stock.service;

import com.swyp.ploutos.stock.StockWithMarket;

public interface StockReader {

    StockWithMarket read(Long stockId);
}
