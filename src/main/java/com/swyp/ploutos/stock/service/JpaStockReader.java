package com.swyp.ploutos.stock.service;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.service.MarketReader;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class JpaStockReader implements StockReader {

    private final StockRepository stockRepository;
    private final MarketReader marketReader;

    @Override
    public StockWithMarket read(Long stockId) {
        Stocks stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        Markets market = marketReader.read(stock.marketId());
        return new StockWithMarket(stock, market);
    }
}
