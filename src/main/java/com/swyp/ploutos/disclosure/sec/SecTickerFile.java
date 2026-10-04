package com.swyp.ploutos.disclosure.sec;

import java.util.List;

/**
 * SEC company_tickers_exchange.json. 행은 {@code fields} 순서의 값 배열이다
 * (예: {@code ["cik","name","ticker","exchange"]} → {@code [320193,"Apple Inc.","AAPL","Nasdaq"]}).
 */
record SecTickerFile(List<String> fields, List<List<Object>> data) {
}
