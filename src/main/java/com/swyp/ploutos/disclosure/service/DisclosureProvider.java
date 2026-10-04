package com.swyp.ploutos.disclosure.service;

import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;

/**
 * 공시 목록 공급자. 공급자 법인 ID와 접수일 범위(시장 현지 날짜)로 공시를 가져온다.
 */
public interface DisclosureProvider {

    DisclosureSource source();

    /**
     * 공급자가 실패했거나 응답을 쓸 수 없으면 {@code DISCLOSURE_UNAVAILABLE}을,
     * 호출 한도·점검에 걸리면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다.
     */
    DisclosureSearchResult search(String issuerId, FiledDateRange range);
}
