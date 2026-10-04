package com.swyp.ploutos.disclosure.service;

import java.util.Locale;
import java.util.Optional;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.disclosure.DisclosureSource;

/**
 * 거래소·티커 → 공급자 법인 ID(DART corp_code) 조회. 매핑은 주기적으로 갱신된다.
 */
public interface IssuerCodes {

    DisclosureSource source();

    /**
     * 연결된 법인 ID가 없거나 후보가 둘 이상이면 비어 있다.
     * 매핑을 받지 못했고 기존 매핑도 없으면 {@code DISCLOSURE_UNAVAILABLE}을,
     * 저장소에 접근하지 못하거나 다른 서버가 처음 받는 중이면 {@code DISCLOSURE_QUOTA_EXCEEDED}를 던진다.
     */
    Optional<String> issuerIdOf(Exchange exchange, String ticker);

    /** 매핑 키. 티커는 대문자로 맞춘다. */
    static String key(Exchange exchange, String ticker) {
        return exchange.name() + ":" + ticker.strip().toUpperCase(Locale.ROOT);
    }
}
