package com.swyp.ploutos.disclosure.service;

import java.util.List;

import com.swyp.ploutos.disclosure.Disclosure;
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
    SearchResult search(String issuerId, FiledDateRange range);

    /**
     * @param disclosures 정제된 공시. 보여 줄 수 없는 레코드는 이미 빠져 있다.
     * @param exhausted   조회 범위의 공시를 끝까지 받았는지
     */
    record SearchResult(List<Disclosure> disclosures, boolean exhausted) {
    }
}
