package com.swyp.ploutos.external.kis;

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

    private final RestClient restClient;
    private final KisAccessTokenProvider tokenProvider;
    private final KisProperties properties;

    @Override
    public <T extends KisResponse> T get(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
        T response = exchange(path, trId, queryParams, responseType);
        if (response.isSuccess()) {
            return response;
        }
        if (response.isTokenExpired()) {
            tokenProvider.invalidate();
            response = exchange(path, trId, queryParams, responseType);
            if (response.isSuccess()) {
                return response;
            }
        }
        log.error("KIS 응답 실패 tr_id={} msg_cd={} msg1={}", trId, response.msgCd(), response.msg1());
        throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    private <T extends KisResponse> T exchange(String path, String trId, Map<String, String> queryParams, Class<T> responseType) {
        try {
            T body = restClient.get()
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
