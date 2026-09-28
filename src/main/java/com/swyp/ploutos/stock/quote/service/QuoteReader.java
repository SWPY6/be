package com.swyp.ploutos.stock.quote.service;

import com.swyp.ploutos.stock.quote.Quote;

/** 종목의 현재가를 읽는다. 호출자는 캐시와 외부 호출의 존재를 알 필요가 없다. */
public interface QuoteReader {

    Quote read(Long stockId);

    /**
     * 갱신 대상으로 표시하지 않고 읽는다. 캐시 확인·락·외부 호출은 {@link #read}와 같다.
     *
     * <p>배치처럼 "지금 사용자가 보고 있는 종목"이 아닌 경우에 쓴다. {@link #read}는 조회한 종목을
     * 갱신 대상으로 표시해 주기마다 다시 받아오는데, 수백 종목을 한 번에 훑는 쪽이 그렇게 하면
     * 갱신 한 바퀴가 길어져 정작 사용자가 보고 있는 종목의 시세가 늦게 갱신된다.
     */
    Quote readWithoutTracking(Long stockId);
}
