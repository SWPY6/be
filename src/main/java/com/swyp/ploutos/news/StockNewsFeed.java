package com.swyp.ploutos.news;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 종목의 뉴스 목록. 검색 후보에서 종목명이 언급되고 기간 안에 있는 기사만 골라
 * 중복을 없애고 최신순으로 최대 20건을 담는다.
 *
 * @param windowCovered 기간 시작까지 검색 결과를 훑었는지. 받은 후보가 기간 시작에 닿지 못했고
 *                      검색 결과가 더 남아 있으면 false다.
 */
public record StockNewsFeed(
        List<NewsArticle> items,
        boolean windowCovered
) {

    private static final int MAX_ITEMS = 20;

    private static final Comparator<NewsArticle> LATEST_FIRST = Comparator
            .comparing(NewsArticle::publishedAt, OffsetDateTime.timeLineOrder()).reversed()
            .thenComparing(NewsArticle::documentId);

    /**
     * @param candidates 공급자에서 받은 정제된 후보. 관련 없는 기사도 섞여 있다.
     * @param exhausted  공급자의 검색 결과를 끝까지 받았는지
     */
    public static StockNewsFeed of(
            String stockName, List<NewsArticle> candidates, boolean exhausted, NewsWindow window
    ) {
        Map<String, NewsArticle> unique = new LinkedHashMap<>();
        candidates.stream()
                .filter(article -> article.mentions(stockName))
                .filter(article -> article.publishedIn(window))
                .sorted(LATEST_FIRST)
                .forEach(article -> unique.putIfAbsent(article.documentId(), article));
        List<NewsArticle> items = unique.values().stream()
                .limit(MAX_ITEMS)
                .toList();
        return new StockNewsFeed(items, exhausted || reachedStart(candidates, window));
    }

    private static boolean reachedStart(List<NewsArticle> candidates, NewsWindow window) {
        return candidates.stream()
                .map(NewsArticle::publishedAt)
                .min(OffsetDateTime.timeLineOrder())
                .map(window::reachedStartBy)
                .orElse(true);
    }

    /**
     * 여러 종목의 피드를 합쳐 중복 없이 최신순으로 최대 {@code limit}건. 같은 기사가 두 종목에
     * 걸려도 한 번만 싣는다.
     *
     * <p>정렬을 먼저 하고 중복을 없앤다. {@code LATEST_FIRST}가 발표 시각이 같으면
     * {@code documentId}로 갈라 주므로, 피드를 넘긴 순서가 달라도 같은 기사가 남는다.
     */
    public static List<NewsArticle> latestAcross(List<StockNewsFeed> feeds, int limit) {
        Map<String, NewsArticle> unique = new LinkedHashMap<>();
        feeds.stream()
                .flatMap(feed -> feed.items.stream())
                .sorted(LATEST_FIRST)
                .forEach(article -> unique.putIfAbsent(article.documentId(), article));
        return unique.values().stream()
                .limit(limit)
                .toList();
    }

    public int total() {
        return items.size();
    }
}
