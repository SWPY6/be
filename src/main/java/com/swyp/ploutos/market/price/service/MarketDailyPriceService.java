package com.swyp.ploutos.market.price.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.IndicatorDailyPriceSyncPolicy;
import com.swyp.ploutos.market.price.MarketDailyPrices;
import com.swyp.ploutos.market.price.repository.MarketDailyPriceRepository;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.StoredRange;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
class MarketDailyPriceService implements IndicatorDailyPriceReader {

    private final MarketDailyPriceRepository repository;
    private final IndicatorDailyPriceProvider provider;
    private final IndicatorDailyPriceSyncPolicy policy;

    @Override
    public DailyPrices findBetween(MarketIndicator indicator, LocalDate from, LocalDate to) {
        syncIfNeeded(indicator, from);
        return DailyPrices.of(
                repository.findByIndicatorAndTradeAtBetweenOrderByTradeAtAsc(indicator, from, to).stream()
                        .map(MarketDailyPrices::toDailyPrice)
                        .toList());
    }

    private void syncIfNeeded(MarketIndicator indicator, LocalDate from) {
        LocalDate today = policy.today(indicator);
        StoredRange stored = new StoredRange(
                repository.findEarliestTradeAt(indicator),
                repository.findLatestTradeAt(indicator)
        );
        if (policy.covers(stored, from, today)) {
            return;
        }
        // 시도권을 얻지 못한 요청은 기다리지 않는다. 그 시점에 저장된 행만 돌려준다.
        if (!policy.tryStartSync(indicator, from, today)) {
            return;
        }
        List<DailyPrice> fetched = provider.fetch(indicator, fetchFrom(stored, from), today.minusDays(1));
        saveNew(indicator, DailyPrices.of(fetched).without(today));
    }

    /**
     * 외부에서 받아 올 시작일. 앞이 비어 있으면 요청 시작일부터, 끝만 낡았으면 저장된 마지막 거래일부터 받는다.
     * 구간이 넓어도 매일 전체를 다시 받지 않게 한다.
     *
     * <p>종목과 달리 여유 기간을 두지 않는다. 주식의 30일 여유는 20거래일 평균 거래량을 위한 것이고,
     * 지표는 거래량을 다루지 않는다.
     */
    private LocalDate fetchFrom(StoredRange stored, LocalDate from) {
        if (!stored.startsOnOrBefore(from)) {
            return from;
        }
        return stored.latest().orElse(from);
    }

    private void saveNew(MarketIndicator indicator, DailyPrices prices) {
        if (prices.isEmpty()) {
            return;
        }
        Set<LocalDate> existing = Set.copyOf(repository.findTradeAtsBetween(
                indicator, prices.firstTradeAt().orElseThrow(), prices.lastTradeAt().orElseThrow()));
        List<MarketDailyPrices> entities = prices.excluding(existing).values().stream()
                .map(price -> new MarketDailyPrices(indicator, price))
                .toList();
        repository.saveAll(entities);
    }
}
