package com.swyp.ploutos.stock.quote.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.quote.Quote;

/** 메모리 캐시. 다른 요청이 락을 쥐고 있거나, 조회 몇 번 뒤에 값이 채워지거나, 저장소가 죽은 상황을 흉내 낸다. */
class FakeQuoteCache implements QuoteCache {

    final Map<Long, Quote> values = new HashMap<>();
    final Set<Long> locked = new HashSet<>();
    final List<Long> unlocked = new ArrayList<>();
    final List<Long> marked = new ArrayList<>();
    final List<Long> active = new ArrayList<>();
    int finds;
    boolean leadership = true;
    boolean broken;
    private Quote fillValue;
    private int fillAfterFinds = -1;

    /** 다른 요청이 락을 잡고 있다가 {@code finds}번째 조회 뒤에 값을 채운다. */
    void fillAfter(int finds, Long stockId, Quote quote) {
        locked.add(stockId);
        this.fillAfterFinds = finds;
        this.fillValue = quote;
    }

    @Override
    public Optional<Quote> find(Long stockId) {
        failIfBroken();
        finds++;
        if (finds == fillAfterFinds) {
            values.put(stockId, fillValue);
        }
        return Optional.ofNullable(values.get(stockId));
    }

    @Override
    public void put(Long stockId, Quote quote) {
        failIfBroken();
        values.put(stockId, quote);
    }

    @Override
    public boolean tryLock(Long stockId) {
        failIfBroken();
        return locked.add(stockId);
    }

    @Override
    public void unlock(Long stockId) {
        locked.remove(stockId);
        unlocked.add(stockId);
    }

    @Override
    public void markActive(Long stockId) {
        failIfBroken();
        marked.add(stockId);
    }

    @Override
    public List<Long> activeStockIds() {
        failIfBroken();
        return List.copyOf(active);
    }

    @Override
    public boolean tryRefreshLeadership() {
        failIfBroken();
        return leadership;
    }

    private void failIfBroken() {
        if (broken) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
