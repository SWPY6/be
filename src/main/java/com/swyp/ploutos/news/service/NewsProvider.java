package com.swyp.ploutos.news.service;

import java.util.List;

import com.swyp.ploutos.news.NewsArticle;

/**
 * 뉴스 검색 공급자. 검색어로 최신순 기사 후보를 가져온다. 후보에는 종목과 무관한 기사도 섞여 있다.
 */
public interface NewsProvider {

    /**
     * 공급자가 실패했거나 응답을 쓸 수 없으면 {@code NEWS_UNAVAILABLE}을,
     * 호출 한도에 걸리면 {@code NEWS_QUOTA_EXCEEDED}를 던진다.
     */
    SearchResult search(String query);

    /**
     * @param articles  정제된 기사 후보. 보여 줄 수 없는 레코드는 이미 빠져 있다.
     * @param exhausted 공급자의 검색 결과를 끝까지 받았는지
     */
    record SearchResult(List<NewsArticle> articles, boolean exhausted) {
    }
}
