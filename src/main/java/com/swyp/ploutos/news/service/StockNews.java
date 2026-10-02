package com.swyp.ploutos.news.service;

import java.time.OffsetDateTime;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.news.NewsWindow;
import com.swyp.ploutos.news.StockNewsFeed;

/**
 * 종목 뉴스 조회 결과.
 *
 * @param fetchedAt 공급자에서 검색 결과를 받은 시각(종목 시장 현지 시각). 캐시 결과면 과거 시각이다.
 */
public record StockNews(
        Long stockId,
        Country market,
        NewsWindow window,
        OffsetDateTime fetchedAt,
        StockNewsFeed feed
) {
}
