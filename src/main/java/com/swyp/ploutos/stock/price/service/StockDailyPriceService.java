package com.swyp.ploutos.stock.price.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.StockDailyPriceSyncPolicy;
import com.swyp.ploutos.stock.price.StockDailyPrices;
import com.swyp.ploutos.stock.price.StoredRange;
import com.swyp.ploutos.stock.price.repository.StockDailyPriceRepository;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
class StockDailyPriceService implements DailyPriceReader {

    // 20거래일 평균을 위해 요청 시작일보다 앞선 구간까지 받아 둔다.
    static final int FETCH_MARGIN_DAYS = 30;

    private final StockReader stockReader;
    private final StockDailyPriceRepository repository;
    private final DailyPriceProvider provider;
    private final StockDailyPriceSyncPolicy policy;

    @Override
    public DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to) {
        StockWithMarket stock = stockReader.read(stockId);
        syncIfNeeded(stock, from);
        return DailyPrices.of(repository.findByStockIdAndTradeAtBetweenOrderByTradeAtAsc(stockId, from, to).stream()
                .map(StockDailyPrices::toDailyPrice)
                .toList());
    }

    @Override
    public Optional<Long> averageVolume20d(Long stockId) {
        StockWithMarket stock = stockReader.read(stockId);
        LocalDate today = policy.today(stock.country());
        syncIfNeeded(stock, today.minusDays(FETCH_MARGIN_DAYS));
        List<DailyPrice> latest = repository
                .findByStockIdOrderByTradeAtDesc(stockId, Limit.of(DailyPrices.AVERAGE_DAYS)).stream()
                .map(StockDailyPrices::toDailyPrice)
                .toList();
        return DailyPrices.of(latest).averageVolume20d();
    }

    private void syncIfNeeded(StockWithMarket stock, LocalDate from) {
        Long stockId = stock.stockId();
        LocalDate today = policy.today(stock.country());
        StoredRange stored = new StoredRange(
                repository.findFirstByStockIdOrderByTradeAtAsc(stockId).map(p -> p.toDailyPrice().tradeAt()),
                repository.findFirstByStockIdOrderByTradeAtDesc(stockId).map(p -> p.toDailyPrice().tradeAt())
        );
        if (policy.covers(stored, from, stock, today)) {
            return;
        }
        if (!policy.tryStartSync(stockId, today)) {
            return;
        }
        List<DailyPrice> fetched = provider.fetch(stock, from.minusDays(FETCH_MARGIN_DAYS), today.minusDays(1));
        saveNew(stockId, DailyPrices.of(fetched).without(today));
    }

    private void saveNew(Long stockId, DailyPrices prices) {
        if (prices.isEmpty()) {
            return;
        }
        Set<LocalDate> existing = Set.copyOf(repository.findTradeAtsBetween(
                stockId, prices.firstTradeAt().orElseThrow(), prices.lastTradeAt().orElseThrow()));
        List<StockDailyPrices> entities = prices.excluding(existing).values().stream()
                .map(price -> new StockDailyPrices(stockId, price))
                .toList();
        repository.saveAll(entities);
    }
}
