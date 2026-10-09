package com.swyp.ploutos.stock.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.stock.Stocks;

public interface StockRepository extends JpaRepository<Stocks, Long> {

    /** {@code uk_stocks_ticker_market}이 ticker 를 앞에 두어 이 조회도 그 인덱스를 쓴다. */
    List<Stocks> findByTickerIn(List<String> tickers);
}
