package com.swyp.ploutos.disclosure.sec;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;
import com.swyp.ploutos.disclosure.service.DisclosureProvider;
import com.swyp.ploutos.disclosure.service.DisclosureSearchResult;
import com.swyp.ploutos.external.sec.SecApiProperties;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * SEC submissions에서 법인의 최근 제출 이력(recent)을 받아 접수일 범위(뉴욕 날짜)에 드는 공시를 최신 100건까지 고른다.
 * 과거 이력 파일은 읽지 않으며, recent가 조회 범위 시작까지 닿지 못했으면 끝까지 받지 못한 것으로 본다.
 */
@Component
@RequiredArgsConstructor
class SecDisclosureProvider implements DisclosureProvider {

    private static final String API = "submissions";
    private static final ZoneId NEW_YORK = Country.US.zoneId();

    static final String PATH = "/submissions/CIK%s.json";
    static final int MAX_ITEMS = 100;

    private final SecFetcher secFetcher;
    private final SecApiProperties secApiProperties;
    private final JsonMapper jsonMapper;

    @Override
    public DisclosureSource source() {
        return DisclosureSource.SEC;
    }

    @Override
    public DisclosureSearchResult search(String cik, FiledDateRange range) {
        SecSubmissionsResponse response = parse(
                secFetcher.get(secApiProperties.dataBaseUrl() + PATH.formatted(cik), API)
        );
        SecSubmissionsResponse.Recent recent = recentOf(response);
        int size = recent.accessionNumber().size();
        List<Disclosure> inRange = new ArrayList<>();
        int invalid = 0;
        // recent 안에 조회 시작일보다 이른 접수일이 있으면 범위 시작까지 다 본 것이다.
        boolean reachedStart = false;
        for (int i = 0; i < size; i++) {
            Optional<Disclosure> disclosure = Disclosure.sec(
                    cik, recent.accessionNumber().get(i), recent.form().get(i), recent.primaryDocDescription().get(i),
                    response.name(), recent.filingDate().get(i), recent.acceptanceDateTime().get(i),
                    recent.primaryDocument().get(i)
            );
            if (disclosure.isEmpty()) {
                invalid++;
                continue;
            }
            if (disclosure.get().filedDate().isBefore(range.from())) {
                reachedStart = true;
            }
            if (range.contains(disclosure.get().localDateIn(NEW_YORK))) {
                inRange.add(disclosure.get());
            }
        }
        if (size > 0 && invalid == size) {
            throw SecFetcher.unavailable(API, "레코드 " + size + "건이 모두 쓸 수 없음", null);
        }
        boolean exhausted = (reachedStart || !response.hasOlderFiles()) && inRange.size() <= MAX_ITEMS;
        return new DisclosureSearchResult(StockDisclosureFeed.latest(inRange, MAX_ITEMS), exhausted);
    }

    /** 열 배열의 길이가 모두 같아야 같은 인덱스를 한 제출 건으로 읽을 수 있다. */
    private static SecSubmissionsResponse.Recent recentOf(SecSubmissionsResponse response) {
        if (response.filings() == null || response.filings().recent() == null) {
            throw SecFetcher.unavailable(API, "filings.recent가 없음", null);
        }
        SecSubmissionsResponse.Recent recent = response.filings().recent();
        List<List<String>> columns = new ArrayList<>();
        columns.add(recent.accessionNumber());
        columns.add(recent.filingDate());
        columns.add(recent.acceptanceDateTime());
        columns.add(recent.form());
        columns.add(recent.primaryDocument());
        columns.add(recent.primaryDocDescription());
        if (columns.stream().anyMatch(column -> column == null)) {
            throw SecFetcher.unavailable(API, "recent에 필요한 열이 없음", null);
        }
        int size = recent.accessionNumber().size();
        if (columns.stream().anyMatch(column -> column.size() != size)) {
            throw SecFetcher.unavailable(API, "recent 열 길이가 다름", null);
        }
        return recent;
    }

    private SecSubmissionsResponse parse(String body) {
        try {
            return jsonMapper.readValue(body, SecSubmissionsResponse.class);
        } catch (JacksonException e) {
            throw SecFetcher.unavailable(API, "응답을 읽지 못함", e);
        }
    }
}
