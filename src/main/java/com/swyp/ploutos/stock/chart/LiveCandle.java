package com.swyp.ploutos.stock.chart;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.swyp.ploutos.stock.price.DailyPrice;

/**
 * 진행 중인 봉. 당일자 일봉과 그 값의 기준 시각이다.
 *
 * <p>시세를 어디서 얻었는지는 담지 않는다. 그래서 종목 현재가와 시장 지표 현재값이
 * 같은 타입으로 {@link Chart}에 들어온다. "진행 중인 봉이 있는가"는 만드는 쪽이 판단한다.
 */
public record LiveCandle(DailyPrice price, OffsetDateTime asOf) {

    public LocalDate tradeAt() {
        return price.tradeAt();
    }
}
