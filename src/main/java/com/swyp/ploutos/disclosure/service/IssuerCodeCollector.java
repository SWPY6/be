package com.swyp.ploutos.disclosure.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.swyp.ploutos.common.enums.Exchange;

/**
 * 공급자 매핑 파일을 읽으며 {@code 거래소:티커} → 법인 ID를 모은다. 한 키에 서로 다른 법인 ID가 둘 이상 오면
 * 어느 쪽이 맞는지 알 수 없으므로 그 키는 쓰지 않고, 이후 같은 키가 다시 와도 넣지 않는다.
 * 매핑 파일 하나를 읽는 동안만 쓰고 버린다.
 */
public final class IssuerCodeCollector {

    private final Map<String, String> codes = new HashMap<>();
    private final Set<String> conflicted = new HashSet<>();

    public void put(Exchange exchange, String ticker, String issuerId) {
        String key = IssuerCodes.key(exchange, ticker);
        if (conflicted.contains(key)) {
            return;
        }
        String existing = codes.putIfAbsent(key, issuerId);
        if (existing != null && !existing.equals(issuerId)) {
            codes.remove(key);
            conflicted.add(key);
        }
    }

    /** 충돌 없이 모인 매핑. */
    public Map<String, String> codes() {
        return Map.copyOf(codes);
    }

    /** 법인 ID가 둘 이상이라 뺀 키 수. */
    public int conflictedCount() {
        return conflicted.size();
    }
}
