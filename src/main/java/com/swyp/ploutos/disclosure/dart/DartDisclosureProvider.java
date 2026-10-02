package com.swyp.ploutos.disclosure.dart;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.disclosure.service.DisclosureProvider;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * DART 공시검색으로 법인의 접수일 범위 공시를 최신순 1페이지(100건) 가져온다. 정정 전 보고서도 함께 받는다.
 * 매 시도 전에 호출 예산을 소비하고, 서버 오류·타임아웃만 한 번 재시도한다.
 * 요청 URL에 인증키가 붙으므로 예외 메시지를 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
class DartDisclosureProvider implements DisclosureProvider {

    private static final Logger log = LoggerFactory.getLogger(DartDisclosureProvider.class);
    private static final String API = "공시검색";

    static final String PATH = "/api/list.json";
    static final int PAGE_COUNT = 100;

    private final RestClient dartRestClient;
    private final DisclosureCallBudget dartCallBudget;
    private final JsonMapper jsonMapper;

    @Override
    public DisclosureSource source() {
        return DisclosureSource.DART;
    }

    @Override
    public SearchResult search(String corpCode, FiledDateRange range) {
        DartDisclosureResponse response = fetch(corpCode, range);
        if (DartStatus.NO_DATA.equals(response.status())) {
            return new SearchResult(List.of(), true);
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
        return new SearchResult(disclosures, isExhausted(response.totalCount(), items.size()));
    }

    private DartDisclosureResponse fetch(String corpCode, FiledDateRange range) {
        try {
            return request(corpCode, range);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("DART 공시검색이 일시적으로 실패해 한 번 재시도한다. cause={}", e.getClass().getSimpleName());
        }
        try {
            return request(corpCode, range);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unavailable("재시도 후에도 실패", e);
        }
    }

    private DartDisclosureResponse request(String corpCode, FiledDateRange range) {
        dartCallBudget.consume();
        try {
            String body = dartRestClient.get()
                    .uri(builder -> builder.path(PATH)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", range.from().format(DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("end_de", range.to().format(DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("last_reprt_at", "N")
                            .queryParam("sort", "date")
                            .queryParam("sort_mth", "desc")
                            .queryParam("page_no", 1)
                            .queryParam("page_count", PAGE_COUNT)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw unavailable("빈 응답", null);
            }
            return jsonMapper.readValue(body, DartDisclosureResponse.class);
        } catch (JacksonException e) {
            throw unavailable("응답을 읽지 못함", e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.error("DART 공시검색 호출 한도에 걸렸다. status={}", e.getStatusCode());
                throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
            }
            throw unavailable("요청·인증 오류 status=" + e.getStatusCode(), null);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 재시도 여부는 fetch가 정한다.
            throw e;
        } catch (RestClientException e) {
            throw unavailable("응답을 읽지 못함", e);
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

    // 예외 메시지에는 인증키가 든 요청 URL이 들어 있을 수 있어 클래스명만 남긴다.
    private static BusinessException unavailable(String reason, Exception cause) {
        log.error("DART 공시검색 실패: {}{}", reason, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }
}
