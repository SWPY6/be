package com.swyp.ploutos.stock.price.service;

import java.time.LocalDate;
import java.util.List;

import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrice;

/** 외부 시세 제공자에서 확정 일봉을 받아 온다. 구간은 양끝 포함, 순서는 보장하지 않는다. */
public interface DailyPriceProvider {

    List<DailyPrice> fetch(StockWithMarket stock, LocalDate from, LocalDate to);
}
