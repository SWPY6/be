package com.swyp.ploutos.news.naver;

import java.util.List;

/**
 * NAVER API HUB 뉴스 검색 응답. 쓰지 않는 필드(lastBuildDate, start, display)는 받지 않는다.
 *
 * @param total 네이버 검색 결과 전체 건수. 종목 뉴스 건수가 아니다.
 */
record NaverNewsResponse(Long total, List<Item> items) {

    /** title·description에는 검색어 강조 태그와 HTML 엔티티가 들어 있다. pubDate는 RFC 1123 형식이다. */
    record Item(String title, String originallink, String link, String description, String pubDate) {
    }
}
