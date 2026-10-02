package com.swyp.ploutos.disclosure.service;

import java.time.OffsetDateTime;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;

/**
 * 종목 공시 조회 결과. 공급자를 조회하지 않았으면(미매핑) 조회 범위와 수집 시각이 null이다.
 *
 * @param source         종목 시장의 공시 공급자
 * @param filedDateRange 공급자에 실제로 요청한 접수일 범위(시장 현지 날짜). 기간 양 끝 날짜를 통째로 포함한다.
 * @param fetchedAt      공급자에서 목록을 받은 시각(종목 시장 현지 시각). 캐시 결과면 과거 시각이다.
 */
public record StockDisclosures(
        Long stockId,
        Country market,
        DisclosureSource source,
        DisclosureWindow window,
        FiledDateRange filedDateRange,
        OffsetDateTime fetchedAt,
        StockDisclosureFeed feed
) {

    static StockDisclosures unmapped(Long stockId, Country market, DisclosureSource source, DisclosureWindow window) {
        return new StockDisclosures(stockId, market, source, window, null, null, StockDisclosureFeed.unmapped());
    }
}
