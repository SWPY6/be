package com.swyp.ploutos.stock.quote.service;

import com.swyp.ploutos.stock.quote.Quote;

/** 종목의 현재가를 읽는다. 호출자는 캐시와 외부 호출의 존재를 알 필요가 없다. */
public interface QuoteReader {

    Quote read(Long stockId);
}
