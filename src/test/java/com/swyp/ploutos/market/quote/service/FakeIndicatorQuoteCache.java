package com.swyp.ploutos.market.quote.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/** 메모리 캐시. 다른 요청이 락을 쥐고 있거나, 조회 몇 번 뒤에 값이 채워지거나, 저장소가 죽은 상황을 흉내 낸다. */
class FakeIndicatorQuoteCache implements IndicatorQuoteCache {

    final Map<MarketIndicator, IndicatorQuote> values = new EnumMap<>(MarketIndicator.class);
    final Set<MarketIndicator> locked = EnumSet.noneOf(MarketIndicator.class);
    final List<MarketIndicator> unlocked = new ArrayList<>();
    int finds;
    boolean broken;
    private IndicatorQuote fillValue;
    private int fillAfterFinds = -1;

    /** 다른 요청이 락을 잡고 있다가 {@code finds}번째 조회 뒤에 값을 채운다. */
    void fillAfter(int finds, MarketIndicator indicator, IndicatorQuote quote) {
        locked.add(indicator);
        this.fillAfterFinds = finds;
        this.fillValue = quote;
    }

    @Override
    public Optional<IndicatorQuote> find(MarketIndicator indicator) {
        failIfBroken();
        finds++;
        if (finds == fillAfterFinds) {
            values.put(indicator, fillValue);
        }
        return Optional.ofNullable(values.get(indicator));
    }

    @Override
    public void put(MarketIndicator indicator, IndicatorQuote quote) {
        failIfBroken();
        values.put(indicator, quote);
    }

    @Override
    public boolean tryLock(MarketIndicator indicator) {
        failIfBroken();
        return locked.add(indicator);
    }

    @Override
    public void unlock(MarketIndicator indicator) {
        locked.remove(indicator);
        unlocked.add(indicator);
    }

    private void failIfBroken() {
        if (broken) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
