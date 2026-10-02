package com.swyp.ploutos.market.price.service;

import java.time.LocalDate;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrices;

/**
 * 지표의 확정 일봉을 읽는다. 저장된 봉이 부족하면 외부에서 받아 채운 뒤 돌려준다(read-through).
 * 호출자는 동기화의 존재를 알 필요가 없다. 지표에는 거래량이 없어 봉의 volume은 0이다.
 */
public interface IndicatorDailyPriceReader {

    DailyPrices findBetween(MarketIndicator indicator, LocalDate from, LocalDate to);
}
