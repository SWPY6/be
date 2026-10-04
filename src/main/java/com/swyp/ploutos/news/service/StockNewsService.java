package com.swyp.ploutos.news.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.news.NewsWindow;
import com.swyp.ploutos.news.StockNewsFeed;
import com.swyp.ploutos.news.service.NewsCache.CachedSearch;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 종목 관련 뉴스를 조회한다. 종목과 기간을 먼저 검증해 잘못된 요청이면 공급자를 부르지 않는다.
 * 검색 결과는 종목별로 캐시하고, 기간·관련성 필터는 매 요청 적용한다. 공급자 실패는 캐시하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StockNewsService {

    private final StockReader stockReader;
    private final NewsCache newsCache;
    private final NewsProvider newsProvider;
    private final Clock clock;

    /** {@code from}·{@code to}를 둘 다 생략하면 종목 시장 현지 시각 기준 최근 7일이다. */
    public StockNewsResult read(Long stockId, OffsetDateTime from, OffsetDateTime to) {
        StockWithMarket stock = stockReader.read(stockId);
        // 응답 시각에 나노초가 찍히지 않게 초 단위로 자른다. 기본 기간과 수집 시각이 모두 이 값을 쓴다.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        NewsWindow window = NewsWindow.of(from, to, stock.localTimeAt(now));
        CachedSearch search = newsCache.find(stockId).orElseGet(() -> fetch(stockId, stock.name(), now));
        StockNewsFeed feed = search.feedIn(stock.name(), window);
        return new StockNewsResult(stockId, stock.country(), window, stock.localTimeAt(search.fetchedAt()), feed);
    }

    private CachedSearch fetch(Long stockId, String query, Instant now) {
        CachedSearch search = new CachedSearch(newsProvider.search(query), now);
        newsCache.put(stockId, search);
        return search;
    }
}
