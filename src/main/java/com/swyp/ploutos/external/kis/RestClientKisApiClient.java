package com.swyp.ploutos.external.kis;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.kis.auth.KisAccessTokenProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RestClientKisApiClient implements KisApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientKisApiClient.class);

    // KIS 한도는 초 단위로 판정하므로 다음 초로 넘어가도록 쉰다.
    static final Duration RATE_LIMIT_BACKOFF = Duration.ofSeconds(1);

    private final RestClient kisRestClient;
    private final KisAccessTokenProvider tokenProvider;
    private final KisProperties properties;

    /** 토큰 만료와 초당 한도 초과는 요청당 한 번만 재시도한다. 재시도 응답이 다시 실패하면 반복하지 않는다. */
    @Override
    public <T extends KisResponse> T get(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
        T response = exchange(path, trId, queryParams, responseType);
        if (response.isSuccess()) {
            return response;
        }
        if (response.isTokenExpired()) {
            tokenProvider.invalidate();
            return retry(path, trId, queryParams, responseType);
        }
        if (response.isRateLimited()) {
            log.warn("KIS 초당 호출 한도를 넘어 잠시 뒤 다시 부른다 tr_id={}", trId);
            pause();
            return retry(path, trId, queryParams, responseType);
        }
        throw failure(trId, response);
    }

    private <T extends KisResponse> T retry(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
        T response = exchange(path, trId, queryParams, responseType);
        if (response.isSuccess()) {
            return response;
        }
        throw failure(trId, response);
    }

    private static BusinessException failure(String trId, KisResponse response) {
        log.error("KIS 응답 실패 tr_id={} msg_cd={} msg1={}", trId, response.msgCd(), response.msg1());
        return new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private static void pause() {
        try {
            Thread.sleep(RATE_LIMIT_BACKOFF);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }

    private <T extends KisResponse> T exchange(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
        try {
            T body = kisRestClient.get()
                    .uri(builder -> {
                        builder.path(path);
                        queryParams.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken())
                    .header("appkey", properties.appKey())
                    .header("appsecret", properties.appSecret())
                    .header("tr_id", trId)
                    .header("custtype", "P")
                    .retrieve()
                    // KIS는 토큰 만료 같은 오류를 5xx와 함께 본문의 rt_cd/msg_cd로 알려 주므로
                    // 상태 코드로 끊지 않고 본문을 읽어 판정한다.
                    .onStatus(HttpStatusCode::isError, (request, response) -> { })
                    .body(responseType);
            if (body == null) {
                log.error("KIS 응답 본문이 비어 있습니다 tr_id={}", trId);
                throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
            }
            return body;
        } catch (RestClientException e) {
            log.error("KIS 호출에 실패했습니다 tr_id={}: {}", trId, e.getMessage());
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
