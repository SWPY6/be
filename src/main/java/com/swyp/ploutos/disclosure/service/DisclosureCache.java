package com.swyp.ploutos.disclosure.service;

import java.time.Instant;
import java.util.Optional;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureProvider.SearchResult;

/**
 * 공급자·법인·접수일 범위별 공시 목록 캐시. 저장소에 접근하지 못하면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다.
 */
public interface DisclosureCache {

    Optional<CachedSearch> find(DisclosureSource source, String issuerId, FiledDateRange range);

    void put(DisclosureSource source, String issuerId, FiledDateRange range, CachedSearch search);

    /**
     * @param fetchedAt 공급자에서 결과를 받은 시각. 캐시에서 읽어도 현재 시각으로 덮어쓰지 않는다.
     */
    record CachedSearch(SearchResult result, Instant fetchedAt) {
    }
}
