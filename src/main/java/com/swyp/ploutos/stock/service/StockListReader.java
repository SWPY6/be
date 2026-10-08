package com.swyp.ploutos.stock.service;

import java.util.List;

import com.swyp.ploutos.stock.StockWithMarket;

/**
 * 종목을 묶어 읽는 계약. 목록 화면처럼 수십~수백 종목을 한 번에 다루는 쪽이 쓴다 —
 * {@link StockReader}로 하나씩 읽으면 조회가 종목 수만큼 나간다.
 *
 * <p>{@code StockReader}에 메서드를 더하지 않고 따로 둔 것은 그쪽이 메서드 하나뿐이라
 * 테스트가 람다로 대신하고 있기 때문이다. 더하면 그 테스트들이 모두 깨진다.
 */
public interface StockListReader {

    /** 찾지 못한 식별자는 결과에서 빠진다. */
    List<StockWithMarket> readAll(List<Long> stockIds);

    /**
     * 종목코드로 찾는다. 외부 순위가 종목코드만 주므로 역방향 조회가 필요하다.
     *
     * <p>같은 코드로 여러 건이 나올 수 있다 — {@code stocks}의 유일 제약이
     * {@code (ticker, market_id)}라 한 종목이 여러 시장에 속할 수 있기 때문이다.
     * 어느 것을 쓸지는 호출자가 정한다.
     */
    List<StockWithMarket> readAllByTickers(List<String> tickers);
}
