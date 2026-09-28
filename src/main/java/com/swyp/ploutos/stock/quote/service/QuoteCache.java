package com.swyp.ploutos.stock.quote.service;

import java.util.List;
import java.util.Optional;

import com.swyp.ploutos.stock.quote.Quote;

/**
 * 현재가 캐시. 저장소에 접근할 수 없으면 {@code MARKET_DATA_UNAVAILABLE}을 던진다.
 * 락 해제만은 실패해도 던지지 않는다(TTL로 풀린다).
 */
public interface QuoteCache {

    Optional<Quote> find(Long stockId);

    void put(Long stockId, Quote quote);

    /** 종목당 한 요청만 외부 호출을 하도록 락을 잡는다. 잡았으면 true. */
    boolean tryLock(Long stockId);

    void unlock(Long stockId);

    /** 조회된 종목을 갱신 대상으로 표시한다. */
    void markActive(Long stockId);

    /** 활성 창 안에 조회된 종목. 창을 벗어난 종목은 목록에서 지운다. */
    List<Long> activeStockIds();

    /** 인스턴스가 여러 대여도 한 곳만 갱신하도록 이번 주기의 갱신 권한을 잡는다. 잡았으면 true. */
    boolean tryRefreshLeadership();
}
