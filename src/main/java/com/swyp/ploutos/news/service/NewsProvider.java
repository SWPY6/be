package com.swyp.ploutos.news.service;

/**
 * 뉴스 검색 공급자. 검색어로 최신순 기사 후보를 가져온다. 후보에는 종목과 무관한 기사도 섞여 있다.
 */
public interface NewsProvider {

    /**
     * 공급자가 실패했거나 응답을 쓸 수 없으면 {@code NEWS_UNAVAILABLE}을,
     * 호출 한도에 걸리면 {@code NEWS_QUOTA_EXCEEDED}를 던진다.
     */
    NewsSearchResult search(String query);
}
