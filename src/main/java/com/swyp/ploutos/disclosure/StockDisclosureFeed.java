package com.swyp.ploutos.disclosure;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 종목의 공시 목록. 기간에 든 공시만 원문 ID로 중복을 없애고
 * 접수일 → 접수 시각(없으면 뒤) → 원문 ID 내림차순으로 담는다.
 * 같은 날짜에 시각이 없으면 그 안의 순서가 실제 접수 순서라고 보장하지 않는다.
 */
public record StockDisclosureFeed(
        List<Disclosure> items,
        Coverage coverage
) {

    private static final Comparator<Disclosure> LATEST_FIRST = Comparator
            .comparing(Disclosure::filedDate, Comparator.reverseOrder())
            .thenComparing(Disclosure::acceptedAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
            .thenComparing(Disclosure::documentId, Comparator.reverseOrder());

    public enum Coverage {
        /** 조회 범위의 공시를 끝까지 받았다. 0건이면 정말 공시가 없는 것이다. */
        COMPLETE,
        /** 공급자에 공시가 더 남아 있다. 최신 일부만 담았다. */
        PARTIAL,
        /** 종목을 공급자 법인 코드에 연결하지 못했다. 공시 0건과 다르다. */
        UNMAPPED
    }

    /**
     * @param candidates 공급자에서 받은 정제된 공시. 기간 양 끝 날짜 전체로 조회한 것이다.
     * @param exhausted  공급자의 조회 결과를 끝까지 받았는지
     * @param zone       종목 시장 시간대. 시각이 없는 공시의 날짜 비교에 쓴다.
     */
    public static StockDisclosureFeed of(
            List<Disclosure> candidates, boolean exhausted, DisclosureWindow window, ZoneId zone
    ) {
        Map<String, Disclosure> unique = new LinkedHashMap<>();
        candidates.stream()
                .filter(disclosure -> disclosure.filedIn(window, zone))
                .sorted(LATEST_FIRST)
                .forEach(disclosure -> unique.putIfAbsent(disclosure.documentId(), disclosure));
        return new StockDisclosureFeed(List.copyOf(unique.values()), exhausted ? Coverage.COMPLETE : Coverage.PARTIAL);
    }

    public static StockDisclosureFeed unmapped() {
        return new StockDisclosureFeed(List.of(), Coverage.UNMAPPED);
    }

    /** 공급자를 조회한 결과인지. 미매핑이면 조회 범위·수집 시각이 없다. */
    public boolean fetched() {
        return coverage != Coverage.UNMAPPED;
    }

    public int total() {
        return items.size();
    }

    /** 공급자에서 받은 순서와 관계없이 화면 정렬 기준으로 최신 {@code limit}건을 고른다. */
    public static List<Disclosure> latest(List<Disclosure> disclosures, int limit) {
        return disclosures.stream().sorted(LATEST_FIRST).limit(limit).toList();
    }
}
