package com.swyp.ploutos.news.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsWindow;

/**
 * 뉴스 공급자의 검색 결과. 캐시도 이 형식으로 저장하므로 공급자 포트의 내부 타입으로 두지 않는다.
 *
 * @param articles          정제된 기사 후보. 보여 줄 수 없는 레코드와 거른 섹션의 기사는 이미 빠져 있다.
 * @param exhausted         공급자의 검색 결과를 끝까지 받았는지
 * @param oldestPublishedAt 받은 응답 원본(섹션 필터·정제 전) 중 가장 오래된 기사 시각. 읽을 수 있는 시각이 없으면 null
 */
public record NewsSearchResult(List<NewsArticle> articles, boolean exhausted, OffsetDateTime oldestPublishedAt) {

    /**
     * 기간 시작까지 검색 결과를 훑었는지. 검색 결과를 어디까지 받았는가의 문제이므로
     * 걸러 낸 후보가 아니라 응답 원본의 가장 오래된 시각으로 판정한다.
     */
    boolean covers(NewsWindow window) {
        if (exhausted || oldestPublishedAt == null) {
            return true;
        }
        return window.reachedStartBy(oldestPublishedAt);
    }
}
