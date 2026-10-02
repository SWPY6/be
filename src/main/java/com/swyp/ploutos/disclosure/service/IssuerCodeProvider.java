package com.swyp.ploutos.disclosure.service;

import java.util.Map;

import com.swyp.ploutos.disclosure.DisclosureSource;

/**
 * 공급자의 전체 법인 매핑 파일을 받는다. 호출이 무거우므로 조회마다 부르지 않는다.
 */
public interface IssuerCodeProvider {

    DisclosureSource source();

    /**
     * {@link IssuerCodes#key} → 법인 ID. 지원하지 않는 거래소, 한 키에 법인이 둘 이상인 경우는 빠져 있다.
     * 받지 못했거나 파일을 쓸 수 없으면 {@code DISCLOSURE_UNAVAILABLE}을,
     * 호출 한도에 걸리면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다.
     */
    Map<String, String> fetchAll();
}
