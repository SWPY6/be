package com.swyp.ploutos.stock.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
class JpaStockReader implements StockReader, StockListReader {

    private final StockRepository stockRepository;
    private final MarketReader marketReader;

    @Override
    public StockWithMarket read(Long stockId) {
        Stocks stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        Markets market = marketReader.read(stock.marketId());
        return new StockWithMarket(stock, market);
    }

    @Override
    public List<StockWithMarket> readAll(List<Long> stockIds) {
        if (stockIds.isEmpty()) {
            return List.of();
        }
        return withMarkets(stockRepository.findAllById(stockIds));
    }

    @Override
    public List<StockWithMarket> readAllByTickers(List<String> tickers) {
        if (tickers.isEmpty()) {
            return List.of();
        }
        return withMarkets(stockRepository.findByTickerIn(tickers));
    }

    /** 시장을 한 번에 읽어 붙인다. 종목마다 읽으면 조회가 종목 수만큼 나간다. */
    private List<StockWithMarket> withMarkets(List<Stocks> stocks) {
        Map<Long, Markets> markets = marketReader.readAll().stream()
                .collect(Collectors.toMap(Markets::marketId, Function.identity()));
        return stocks.stream()
                .filter(stock -> markets.containsKey(stock.marketId()))
                .map(stock -> new StockWithMarket(stock, markets.get(stock.marketId())))
                .toList();
    }
}
