package com.swyp.ploutos.news.naver;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.service.NewsCallBudget;
import com.swyp.ploutos.news.service.NewsProvider;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * NAVER API HUB 뉴스 검색으로 최신순 1페이지를 가져온다.
 * 매 시도 전에 호출 예산을 소비하고, 서버 오류·타임아웃만 한 번 재시도한다.
 * 인증·요청 오류는 반복해도 같은 결과이므로 재시도하지 않는다.
 */
@Component
@RequiredArgsConstructor
class NaverNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(NaverNewsProvider.class);

    static final String PATH = "/search/v1/news";
    static final int DISPLAY = 100;
    // 첫 시도 + 일시적 실패 시 재시도 한 번
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient naverRestClient;
    private final NewsCallBudget newsCallBudget;
    private final JsonMapper jsonMapper;

    @Override
    public SearchResult search(String query) {
        NaverNewsResponse response = fetch(query);
        List<NaverNewsResponse.Item> items = response.items() == null ? List.of() : response.items();
        List<NewsArticle> articles = items.stream()
                .map(NaverNewsProvider::toArticle)
                .flatMap(Optional::stream)
                .toList();
        if (!items.isEmpty() && articles.isEmpty()) {
            log.error("네이버 뉴스 응답 {}건이 모두 쓸 수 없는 레코드다.", items.size());
            throw new BusinessException(ErrorCode.NEWS_UNAVAILABLE);
        }
        return new SearchResult(articles, isExhausted(response.total(), items.size()));
    }

    private NaverNewsResponse fetch(String query) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request(query);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw unavailable("재시도 후에도 실패", e);
                }
                log.warn("네이버 뉴스 검색이 일시적으로 실패해 한 번 재시도한다. cause={}", e.getClass().getSimpleName());
            }
        }
    }

    private NaverNewsResponse request(String query) {
        newsCallBudget.consume();
        try {
            // API HUB는 JSON 본문을 text/plain으로 보낸다. 콘텐츠 타입으로 변환기를 고르면 읽지 못하므로
            // 문자열로 받아 직접 파싱한다.
            String body = naverRestClient.get()
                    .uri(builder -> builder.path(PATH)
                            .queryParam("query", query)
                            .queryParam("display", DISPLAY)
                            .queryParam("start", 1)
                            .queryParam("sort", "date")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw unavailable("빈 응답", null);
            }
            return jsonMapper.readValue(body, NaverNewsResponse.class);
        } catch (JacksonException e) {
            throw unavailable("응답을 읽지 못함", e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.error("네이버 뉴스 검색 호출 한도에 걸렸다. status={}", e.getStatusCode());
                throw new BusinessException(ErrorCode.NEWS_QUOTA_EXCEEDED);
            }
            throw unavailable("요청·인증 오류 status=" + e.getStatusCode(), null);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 재시도 여부는 fetch가 정한다.
            throw e;
        } catch (RestClientException e) {
            throw unavailable("응답을 읽지 못함", e);
        }
    }

    /** 요청한 1페이지 안에 전체 결과가 다 들어왔으면 더 받을 것이 없다. */
    private static boolean isExhausted(Long total, int received) {
        if (total == null) {
            return received < DISPLAY;
        }
        return total <= received;
    }

    private static Optional<NewsArticle> toArticle(NaverNewsResponse.Item item) {
        return NewsArticle.from(
                item.title(), item.description(), item.originallink(), item.link(), parsePubDate(item.pubDate())
        );
    }

    /** 파싱하지 못한 시각은 null이며, 그 기사는 {@link NewsArticle#from}에서 빠진다. */
    private static OffsetDateTime parsePubDate(String pubDate) {
        if (pubDate == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(pubDate.strip(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // 외부 응답 본문에는 요청 정보가 섞여 있을 수 있어 로그에 남기지 않는다.
    private static BusinessException unavailable(String reason, Exception cause) {
        log.error("네이버 뉴스 검색 실패: {}{}", reason, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new BusinessException(ErrorCode.NEWS_UNAVAILABLE);
    }
}
