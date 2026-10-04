package com.swyp.ploutos.news.service;

import java.util.List;

import com.swyp.ploutos.news.NewsArticle;

/**
 * 뉴스 공급자의 검색 결과. 캐시도 이 형식으로 저장하므로 공급자 포트의 내부 타입으로 두지 않는다.
 *
 * @param articles  정제된 기사 후보. 보여 줄 수 없는 레코드는 이미 빠져 있다.
 * @param exhausted 공급자의 검색 결과를 끝까지 받았는지
 */
public record NewsSearchResult(List<NewsArticle> articles, boolean exhausted) {
}
