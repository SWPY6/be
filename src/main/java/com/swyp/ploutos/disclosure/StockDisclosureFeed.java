package com.swyp.ploutos.disclosure;

import java.time.Instant;
import java.time.LocalDate;
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
        /** 요청 기간의 공시를 다 받았다. 0건이면 정말 공시가 없는 것이다. */
        COMPLETE,
        /** 요청 기간에 공급자 공시가 더 남아 있을 수 있다. 최신 일부만 담았다. */
        PARTIAL,
        /** 종목을 공급자 법인 코드에 연결하지 못했다. 공시 0건과 다르다. */
        UNMAPPED,
        /** 종목 시장에 아직 공시 공급자가 없다(미국). 공시 0건과 다르다. */
        UNSUPPORTED_MARKET
    }

    /**
     * 공급자 결과가 끝까지 오지 않았어도 기간 시작 날짜보다 이른 공시까지 받았으면 기간 안은 다 받은 것이므로
     * COMPLETE다. 공급자는 최신순으로 잘라 주므로 그보다 새로운 공시는 빠지지 않는다.
     *
     * @param candidates 공급자에서 받은 정제된 공시. 기간을 포함하는 접수일 범위로 최신순 조회한 것이다.
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
        boolean complete = exhausted || reachesBefore(candidates, window.filedDatesIn(zone).from(), zone);
        return new StockDisclosureFeed(List.copyOf(unique.values()), complete ? Coverage.COMPLETE : Coverage.PARTIAL);
    }

    // 같은 날짜는 더 남았을 수 있으므로 시작 날짜보다 엄격히 이른 공시가 있어야 한다.
    private static boolean reachesBefore(List<Disclosure> candidates, LocalDate start, ZoneId zone) {
        return candidates.stream().anyMatch(disclosure -> disclosure.localDateIn(zone).isBefore(start));
    }

    public int total() {
        return items.size();
    }

    /** 공급자에서 받은 순서와 관계없이 화면 정렬 기준으로 최신 {@code limit}건을 고른다. */
    public static List<Disclosure> latest(List<Disclosure> disclosures, int limit) {
        return disclosures.stream().sorted(LATEST_FIRST).limit(limit).toList();
    }
}
