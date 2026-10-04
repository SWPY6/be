package com.swyp.ploutos.disclosure.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;

/**
 * 공급자·법인·접수일 범위별 공시 목록 캐시. 조회 때 저장소에 접근하지 못하면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다.
 * 저장 실패는 던지지 않는다 — 이미 받은 결과를 버리지 않는다.
 */
public interface DisclosureCache {

    Optional<CachedSearch> find(DisclosureSource source, String issuerId, FiledDateRange range);

    void put(DisclosureSource source, String issuerId, FiledDateRange range, CachedSearch search);

    /**
     * @param fetchedAt 공급자에서 결과를 받은 시각. 캐시에서 읽어도 현재 시각으로 덮어쓰지 않는다.
     */
    record CachedSearch(DisclosureSearchResult result, Instant fetchedAt) {

        /** 받은 공시를 요청 기간으로 걸러 목록을 만든다. */
        StockDisclosureFeed feedIn(DisclosureWindow window, ZoneId zone) {
            return StockDisclosureFeed.of(result.disclosures(), result.exhausted(), window, zone);
        }
    }
}
