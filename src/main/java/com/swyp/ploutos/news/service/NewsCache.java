package com.swyp.ploutos.news.service;

import java.time.Instant;
import java.util.Optional;

import com.swyp.ploutos.news.NewsWindow;
import com.swyp.ploutos.news.StockNewsFeed;

/**
 * 종목별 뉴스 검색 결과 캐시. 기간과 관계없이 종목당 하나를 두고, 기간 필터는 읽는 쪽이 적용한다.
 * 조회 때 저장소에 접근하지 못하면 {@code NEWS_QUOTA_EXCEEDED}를 던진다. 저장 실패는 던지지 않는다.
 */
public interface NewsCache {

    Optional<CachedSearch> find(Long stockId);

    void put(Long stockId, CachedSearch search);

    /**
     * @param fetchedAt 공급자에서 결과를 받은 시각. 캐시에서 읽어도 현재 시각으로 덮어쓰지 않는다.
     */
    record CachedSearch(NewsSearchResult result, Instant fetchedAt) {

        /** 종목과 관련 있고 기간에 든 기사로 목록을 만든다. */
        StockNewsFeed feedIn(String stockName, NewsWindow window) {
            return StockNewsFeed.of(stockName, result.articles(), result.exhausted(), window);
        }
    }
}
