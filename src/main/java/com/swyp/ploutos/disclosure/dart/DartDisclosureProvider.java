package com.swyp.ploutos.disclosure.dart;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.disclosure.service.DisclosureProvider;
import com.swyp.ploutos.disclosure.service.DisclosureSearchResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * DART 공시검색으로 법인의 접수일 범위 공시를 최신순 1페이지(100건) 가져온다. 정정 전 보고서도 함께 받는다.
 * 요청·재시도는 {@link DartFetcher}가 하며, 법인 매핑 갱신 몫을 남겨 둔 공시검색 예산을 쓴다.
 */
@Component
class DartDisclosureProvider implements DisclosureProvider {

    private static final Logger log = LoggerFactory.getLogger(DartDisclosureProvider.class);
    private static final String API = "공시검색";

    static final String PATH = "/api/list.json";
    static final int PAGE_COUNT = 100;

    private final DartFetcher dartFetcher;
    private final JsonMapper jsonMapper;

    DartDisclosureProvider(RestClient dartRestClient, DisclosureCallBudget dartSearchCallBudget, JsonMapper jsonMapper) {
        this.dartFetcher = new DartFetcher(dartRestClient, dartSearchCallBudget);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public DisclosureSource source() {
        return DisclosureSource.DART;
    }

    @Override
    public DisclosureSearchResult search(String corpCode, FiledDateRange range) {
        DartDisclosureResponse response = fetch(corpCode, range);
        if (DartStatus.NO_DATA.equals(response.status())) {
            return new DisclosureSearchResult(List.of(), true);
        }
        DartStatus.requireOk(response.status(), API);
        List<DartDisclosureResponse.Item> items = response.list() == null ? List.of() : response.list();
        List<Disclosure> disclosures = items.stream()
                .map(DartDisclosureProvider::toDisclosure)
                .flatMap(Optional::stream)
                .toList();
        if (!items.isEmpty() && disclosures.isEmpty()) {
            log.error("DART 공시검색 응답 {}건이 모두 쓸 수 없는 레코드다.", items.size());
            throw new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
        }
        return new DisclosureSearchResult(disclosures, isExhausted(response.totalCount(), items.size()));
    }

    private DartDisclosureResponse fetch(String corpCode, FiledDateRange range) {
        String body = dartFetcher.get(builder -> builder.path(PATH)
                .queryParam("corp_code", corpCode)
                .queryParam("bgn_de", range.from().format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("end_de", range.to().format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("last_reprt_at", "N")
                .queryParam("sort", "date")
                .queryParam("sort_mth", "desc")
                .queryParam("page_no", 1)
                .queryParam("page_count", PAGE_COUNT)
                .build(), API);
        try {
            return jsonMapper.readValue(body, DartDisclosureResponse.class);
        } catch (JacksonException e) {
            throw DartFetcher.unavailable(API, "응답을 읽지 못함", e);
        }
    }

    /** 받은 1페이지 안에 전체 결과가 다 들어왔으면 더 받을 것이 없다. */
    private static boolean isExhausted(Long totalCount, int received) {
        if (totalCount == null) {
            return received < PAGE_COUNT;
        }
        return totalCount <= received;
    }

    private static Optional<Disclosure> toDisclosure(DartDisclosureResponse.Item item) {
        return Disclosure.dart(
                item.receiptNo(), item.reportName(), item.corpName(), item.filerName(), item.rm(), item.receiptDate()
        );
    }
}
