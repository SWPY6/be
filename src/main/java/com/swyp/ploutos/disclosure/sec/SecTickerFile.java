package com.swyp.ploutos.disclosure.sec;

import java.util.List;

/**
 * SEC company_tickers_exchange.json. 행은 {@code fields} 순서의 값 배열이다
 * (예: {@code ["cik","name","ticker","exchange"]} → {@code [320193,"Apple Inc.","AAPL","Nasdaq"]}).
 * 행은 {@link SecTickerRow}가 위치로 읽으므로 {@code fields}가 그 순서와 같은지 먼저 확인한다.
 */
record SecTickerFile(List<String> fields, List<SecTickerRow> data) {

    private static final List<String> EXPECTED_FIELDS = List.of("cik", "name", "ticker", "exchange");

    boolean hasExpectedFields() {
        return EXPECTED_FIELDS.equals(fields);
    }
}
