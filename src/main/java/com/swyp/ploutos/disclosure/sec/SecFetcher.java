package com.swyp.ploutos.disclosure.sec;

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
import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;

import lombok.RequiredArgsConstructor;

/**
 * SEC 요청 공통 처리. 매 시도 전에 초당 예산을 소비하고, 서버 오류·타임아웃만 한 번 재시도한다.
 * 403은 차단·User-Agent 문제일 수 있어 재시도·우회하지 않는다. 429는 호출 제한이다.
 */
@Component
@RequiredArgsConstructor
class SecFetcher {

    private static final Logger log = LoggerFactory.getLogger(SecFetcher.class);
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient secRestClient;
    private final DisclosureCallBudget secCallBudget;

    /** 본문을 문자열로 돌려준다. 실패하면 {@code DISCLOSURE_UNAVAILABLE}·{@code DISCLOSURE_QUOTA_EXCEEDED}다. */
    String get(String url, String api) {
        RestClientException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return request(url, api);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                last = e;
            }
        }
        throw unavailable(api, "재시도 후에도 실패", last);
    }

    private String request(String url, String api) {
        secCallBudget.consume();
        try {
            String body = secRestClient.get().uri(url).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw unavailable(api, "빈 응답", null);
            }
            return body;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.error("SEC {} 호출 한도에 걸렸다. status={}", api, e.getStatusCode());
                throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw unavailable(api, "접근 거부(403). User-Agent·요청률·차단 여부를 확인", null);
            }
            throw unavailable(api, "요청 오류 status=" + e.getStatusCode(), null);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 재시도 여부는 get이 정한다.
            throw e;
        } catch (RestClientException e) {
            throw unavailable(api, "응답을 읽지 못함", e);
        }
    }

    static BusinessException unavailable(String api, String reason, Exception cause) {
        log.error("SEC {} 실패: {}{}", api, reason, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new BusinessException(ErrorCode.DISCLOSURE_UNAVAILABLE);
    }
}
