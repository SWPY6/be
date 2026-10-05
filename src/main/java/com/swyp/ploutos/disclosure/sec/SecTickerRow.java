package com.swyp.ploutos.disclosure.sec;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.disclosure.service.IssuerCodeCollector;

/** 티커 파일의 한 행. {@code [cik, name, ticker, exchange]} 배열을 위치로 읽는다. */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
record SecTickerRow(Long cik, String name, String ticker, String exchange) {

    private static final long MAX_CIK = 9_999_999_999L;

    /** 서비스가 다루는 거래소(Nasdaq·NYSE)이고 CIK·티커가 온전할 때만 넣는다. CIK는 10자리 문자열로 맞춘다. */
    void putInto(IssuerCodeCollector collector) {
        Exchange listed = listedExchange();
        if (listed == null || cik == null || cik <= 0 || cik > MAX_CIK || ticker == null || ticker.isBlank()) {
            return;
        }
        collector.put(listed, ticker, String.format("%010d", cik));
    }

    private Exchange listedExchange() {
        if ("Nasdaq".equals(exchange)) {
            return Exchange.NASDAQ;
        }
        if ("NYSE".equals(exchange)) {
            return Exchange.NYSE;
        }
        return null;
    }
}
